package ru.bogempty.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Космическое небо.
 * Стили-планеты («Планеты», «Чёрная дыра», «Неон», «Шар-туманность», «Земля»): заменяют текстуры солнца и луны
 * на большие яркие светила, ночью вокруг обычные звёзды. Это самый яркий вариант.
 * Стили-Энд («Галактика» и др.): подменяют небо на небо Энда с нарисованной текстурой. Ваниль делает его тусклым.
 */
public final class SpaceSky {
    private static final Identifier END_SKY = Identifier.of("minecraft", "textures/environment/end_sky.png");
    private static final Identifier SUN = Identifier.of("minecraft", "textures/environment/sun.png");
    private static final Identifier MOON = Identifier.of("minecraft", "textures/environment/moon_phases.png");
    private static final Set<String> END_STYLES = Set.of("Галактика", "Туманность", "Звёздный рой", "Космический закат", "Аврора");
    private static final int SIZE = 512;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bogempty-sky");
        t.setDaemon(true);
        return t;
    });

    private record Ready(String key, byte[] end, byte[] sun, byte[] moon) {}

    private static DimensionEffects endFx;
    private static Field fxField;
    private static boolean fieldSearched = false;
    private static boolean failed = false;
    private static DimensionEffects original;
    private static ClientWorld originalWorld;

    private static volatile String requestedKey = "";
    private static volatile Ready ready;
    private static NativeImageBackedTexture tEnd, tSun, tMoon;
    private static boolean active = false;
    private static int checkTimer = 0;

    public static void update(ClientWorld world, String style, int density, int sat, int variant) {
        active = true;
        final boolean end = END_STYLES.contains(style);
        if (end) swapEffects(world);
        else unswapEffects();

        final String key = style + "|" + density + "|" + sat + "|" + variant;
        if (!key.equals(requestedKey)) {
            requestedKey = key;
            POOL.execute(() -> {
                try {
                    Ready r;
                    if (end) {
                        r = new Ready(key, generateEnd(style, density, sat, variant), null, null);
                    } else {
                        r = generatePlanets(style, sat, variant, key);
                    }
                    if (key.equals(requestedKey)) ready = r;
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        }

        Ready r = ready;
        if (r != null) {
            ready = null;
            apply(r);
        } else if (++checkTimer % 100 == 0) {
            reassert();
        }
    }

    public static void restore() {
        if (!active) return;
        active = false;
        requestedKey = "";
        ready = null;
        unswapEffects();
        clearEnd();
        clearBodies();
    }

    // ---------- текстуры ----------
    private static void apply(Ready r) {
        if (r.end() != null) {
            clearBodies();
            tEnd = register(END_SKY, r.end());
        } else {
            clearEnd();
            tSun = register(SUN, r.sun());
            tMoon = register(MOON, r.moon());
        }
    }

    private static NativeImageBackedTexture register(Identifier id, byte[] png) {
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            tex.setFilter(true, false);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
            return tex;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private static void vanilla(Identifier id) {
        try {
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new ResourceTexture(id));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void clearEnd() {
        if (tEnd != null) {
            vanilla(END_SKY);
            tEnd = null;
        }
    }

    private static void clearBodies() {
        if (tSun != null) {
            vanilla(SUN);
            tSun = null;
        }
        if (tMoon != null) {
            vanilla(MOON);
            tMoon = null;
        }
    }

    /** Если ресурсы перезагрузили, возвращаем наши текстуры. */
    private static void reassert() {
        try {
            var tm = MinecraftClient.getInstance().getTextureManager();
            if (tEnd != null && tm.getTexture(END_SKY) != tEnd) tm.registerTexture(END_SKY, tEnd);
            if (tSun != null && tm.getTexture(SUN) != tSun) tm.registerTexture(SUN, tSun);
            if (tMoon != null && tm.getTexture(MOON) != tMoon) tm.registerTexture(MOON, tMoon);
        } catch (Throwable ignored) {
        }
    }

    // ---------- тип неба мира ----------
    private static void swapEffects(ClientWorld world) {
        if (failed) return;
        try {
            if (fxField == null) {
                if (fieldSearched) return;
                fieldSearched = true;
                for (Field f : ClientWorld.class.getDeclaredFields()) {
                    if (f.getType() == DimensionEffects.class) {
                        f.setAccessible(true);
                        fxField = f;
                        break;
                    }
                }
                if (fxField == null) {
                    failed = true;
                    return;
                }
            }
            if (endFx == null) endFx = new DimensionEffects.End();
            Object cur = fxField.get(world);
            if (cur != endFx) {
                if (originalWorld != world) {
                    original = (DimensionEffects) cur;
                    originalWorld = world;
                }
                fxField.set(world, endFx);
            }
        } catch (Throwable t) {
            failed = true;
            t.printStackTrace();
        }
    }

    private static void unswapEffects() {
        try {
            if (original != null && originalWorld != null && fxField != null) {
                fxField.set(originalWorld, original);
            }
        } catch (Throwable ignored) {
        }
        original = null;
        originalWorld = null;
    }

    // ---------- шум ----------
    private static float hash(int x, int y, int s) {
        int h = x * 374761393 + y * 668265263 + s * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float vnoise(float x, float y, int s) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float xf = x - xi, yf = y - yi;
        float u = xf * xf * (3f - 2f * xf), v = yf * yf * (3f - 2f * yf);
        float a = hash(xi, yi, s), b = hash(xi + 1, yi, s);
        float c = hash(xi, yi + 1, s), d = hash(xi + 1, yi + 1, s);
        float top = a + (b - a) * u;
        float bot = c + (d - c) * u;
        return top + (bot - top) * v;
    }

    private static float fbm(float x, float y, int s, int oct) {
        float amp = 0.5f, sum = 0f;
        for (int i = 0; i < oct; i++) {
            sum += amp * vnoise(x, y, s + i * 17);
            x = x * 2.03f + 11.7f;
            y = y * 2.03f + 3.1f;
            amp *= 0.5f;
        }
        return sum;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }

    private static float cl(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ---------- планеты (солнце + луна) ----------
    private static Ready generatePlanets(String style, int sat, int variant, String key) throws Exception {
        String sunArt, moonArt;
        switch (style) {
            case "Чёрная дыра": sunArt = "hole"; moonArt = "neon"; break;
            case "Неон": sunArt = "neon"; moonArt = "crystal"; break;
            case "Шар-туманность": sunArt = "nebula"; moonArt = "star"; break;
            case "Земля": sunArt = "star"; moonArt = "earth"; break;
            default: sunArt = "gas"; moonArt = "ice"; break;
        }
        int seed = variant * 7919 + 31;
        BufferedImage sun = art(sunArt, 256, seed, sat);
        BufferedImage cell = art(moonArt, 128, seed + 7, sat);

        // луна в ванили лежит атласом 4x2 (8 фаз): кладём в каждую клетку одну картинку
        BufferedImage atlas = new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) g.drawImage(cell, col * 128, row * 128, null);
        }
        g.dispose();
        return new Ready(key, null, png(sun), png(atlas));
    }

    private static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** Складывает слой поверх (обычное смешивание). rgba[] обновляется. */
    private static void over(float[] dst, float r, float g, float b, float a) {
        a = cl(a);
        if (a <= 0f) return;
        float oa = a + dst[3] * (1f - a);
        if (oa <= 0f) return;
        dst[0] = (r * a + dst[0] * dst[3] * (1f - a)) / oa;
        dst[1] = (g * a + dst[1] * dst[3] * (1f - a)) / oa;
        dst[2] = (b * a + dst[2] * dst[3] * (1f - a)) / oa;
        dst[3] = oa;
    }

    private static BufferedImage art(String kind, int S, int seed, int sat) {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        int[] px = new int[S * S];
        float satK = sat / 100f;
        float[] px4 = new float[4];

        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                float u = (x + 0.5f) / S * 2f - 1f;
                float v = (y + 0.5f) / S * 2f - 1f;
                px4[0] = px4[1] = px4[2] = px4[3] = 0f;
                switch (kind) {
                    case "gas": gas(px4, u, v, seed); break;
                    case "ice": ice(px4, u, v, seed); break;
                    case "hole": hole(px4, u, v, seed); break;
                    case "neon": neon(px4, u, v, seed); break;
                    case "crystal": crystal(px4, u, v, seed); break;
                    case "nebula": nebulaSphere(px4, u, v, seed); break;
                    case "earth": earth(px4, u, v, seed); break;
                    default: star(px4, u, v, seed); break;
                }
                float lum = 0.3f * px4[0] + 0.59f * px4[1] + 0.11f * px4[2];
                float r = cl(lum + (px4[0] - lum) * satK);
                float g = cl(lum + (px4[1] - lum) * satK);
                float b = cl(lum + (px4[2] - lum) * satK);
                int a = (int) (cl(px4[3]) * 255);
                px[y * S + x] = (a << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
            }
        }
        img.setRGB(0, 0, S, S, px, 0, S);
        return img;
    }

    private static float light(float nx, float ny, float nz) {
        float lx = -0.55f, ly = -0.5f, lz = 0.67f;
        float d = nx * lx + ny * ly + nz * lz;
        return Math.max(0f, d);
    }

    // газовый гигант с кольцом
    private static void gas(float[] o, float u, float v, int seed) {
        float R = 0.42f;
        float r = (float) Math.sqrt(u * u + v * v);
        // свечение атмосферы
        if (r > R) over(o, 0.95f, 0.55f, 0.30f, (float) Math.exp(-(r - R) * 9f) * 0.45f);

        float ang = -0.35f;
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        float ex = u * ca + v * sa;
        float ey = (-u * sa + v * ca) * 3.4f;
        float rr = (float) Math.sqrt(ex * ex + ey * ey);
        float ringA = smooth(0.62f, 0.68f, rr) * (1f - smooth(0.93f, 0.99f, rr));
        ringA *= 0.55f + 0.45f * (float) Math.sin(rr * 80f + 1.3f);
        ringA *= 0.9f - 0.4f * (float) Math.abs(Math.sin(rr * 21f));
        boolean ringFront = ey > 0;
        if (ringA > 0 && !ringFront && r > R) over(o, 0.85f, 0.75f, 0.60f, ringA);

        if (r < R) {
            float lat = v / R;
            float nz = (float) Math.sqrt(Math.max(0f, 1f - (r / R) * (r / R)));
            float band = fbm(lat * 5f + fbm(u * 3f, v * 3f, seed, 3) * 1.6f, 0.5f, seed + 3, 4);
            float t = cl(band * 1.6f - 0.1f);
            float cr = t < 0.5f ? mix(0.55f, 0.95f, t * 2f) : mix(0.95f, 0.98f, (t - 0.5f) * 2f);
            float cg = t < 0.5f ? mix(0.30f, 0.62f, t * 2f) : mix(0.62f, 0.88f, (t - 0.5f) * 2f);
            float cb = t < 0.5f ? mix(0.16f, 0.32f, t * 2f) : mix(0.32f, 0.72f, (t - 0.5f) * 2f);
            float sh = 0.22f + 1.0f * light(u / R, v / R, nz);
            float rim = (float) Math.pow(1f - nz, 3.0) * 0.45f;
            over(o, cl(cr * sh + rim * 0.9f), cl(cg * sh + rim * 0.5f), cl(cb * sh + rim * 0.25f), smooth(R, R - 0.02f, r));
        }
        if (ringA > 0 && ringFront) over(o, 0.92f, 0.82f, 0.66f, ringA);
    }

    // ледяной мир с кратерами
    private static void ice(float[] o, float u, float v, int seed) {
        float R = 0.82f;
        float r = (float) Math.sqrt(u * u + v * v);
        if (r > R) over(o, 0.55f, 0.8f, 1.0f, (float) Math.exp(-(r - R) * 12f) * 0.55f);
        if (r < R) {
            float nz = (float) Math.sqrt(Math.max(0f, 1f - (r / R) * (r / R)));
            float base = 0.75f + 0.18f * fbm(u * 4f, v * 4f, seed, 4);
            float crater = 0f;
            for (int i = 0; i < 12; i++) {
                float cx = (hash(i, 1, seed) * 2f - 1f) * 0.6f;
                float cy = (hash(i, 2, seed) * 2f - 1f) * 0.6f;
                float cr = 0.07f + hash(i, 3, seed) * 0.12f;
                float d = (float) Math.sqrt((u - cx) * (u - cx) + (v - cy) * (v - cy));
                if (d < cr) crater -= 0.25f * (1f - d / cr);
                else if (d < cr * 1.25f) crater += 0.18f;
            }
            float sh = 0.25f + 0.95f * light(u / R, v / R, nz);
            float rim = (float) Math.pow(1f - nz, 3.0) * 0.5f;
            float c = cl((base + crater) * sh);
            over(o, cl(c * 0.8f + rim * 0.5f), cl(c * 0.92f + rim * 0.8f), cl(c + rim), smooth(R, R - 0.03f, r));
        }
    }

    // чёрная дыра с аккреционным диском
    private static void hole(float[] o, float u, float v, int seed) {
        float r = (float) Math.sqrt(u * u + v * v);
        if (r > 0.3f) over(o, 1.0f, 0.55f, 0.2f, (float) Math.exp(-(r - 0.3f) * 5f) * 0.22f);

        // заднюю часть диска линзирует над и под дырой
        float arcR = (float) Math.abs(r - 0.48f);
        float arc = smooth(0.12f, 0f, arcR) * smooth(0.12f, 0.2f, (float) Math.abs(v)) * 0.8f;
        if (arc > 0) over(o, 1.0f, 0.7f, 0.35f, arc * (float) Math.exp(-Math.abs(u) * 1.2f));

        // тонкое фотонное кольцо
        float lens = smooth(0.035f, 0f, (float) Math.abs(r - 0.34f));
        if (lens > 0) over(o, 1.0f, 0.95f, 0.85f, lens);

        // сам диск (сильно приплюснутый)
        float ey = v * 5.5f;
        float rr = (float) Math.sqrt(u * u + ey * ey);
        if (rr > 0.34f && rr < 0.98f) {
            float t = (rr - 0.34f) / 0.64f;
            float inten = (float) Math.pow(1f - t, 1.5);
            float dopp = 0.65f + 0.55f * smooth(0.8f, -0.8f, u);
            float turb = 0.75f + 0.5f * fbm(u * 9f + seed, ey * 2f, seed, 3);
            float k = cl(inten * dopp * turb * 1.4f);
            float cr = 1.0f, cg = mix(0.35f, 0.92f, k), cb = mix(0.08f, 0.6f, k * k);
            float edge = smooth(0.34f, 0.4f, rr) * (1f - smooth(0.9f, 0.98f, rr));
            // передняя часть диска закрывает дыру, задняя прячется за тенью
            if (!(v < 0 && r < 0.3f)) over(o, cr, cg, cb, k * edge);
        }
        // тень горизонта событий
        if (r < 0.3f) over(o, 0f, 0f, 0f, smooth(0.3f, 0.27f, r));
    }

    // неоновый шар с лучами
    private static void neon(float[] o, float u, float v, int seed) {
        float r = (float) Math.sqrt(u * u + v * v);
        double th = Math.atan2(v, u);
        float halo = (float) Math.exp(-r * 3.2f);
        over(o, 0.95f, 0.25f, 0.85f, halo * 0.9f);
        float rays = (float) Math.pow(Math.max(0.0, Math.cos(8 * th + seed)), 14.0) * (float) Math.exp(-r * 2.3f) * 0.8f;
        over(o, 0.7f, 0.5f, 1.0f, rays);
        float core = (float) Math.exp(-r * r * 40f);
        over(o, 1f, 0.95f, 1f, core);
        float ring = smooth(0.05f, 0f, (float) Math.abs(r - 0.42f)) * 0.7f;
        over(o, 0.5f, 0.9f, 1.0f, ring);
    }

    // огранённый кристалл-шар
    private static void crystal(float[] o, float u, float v, int seed) {
        float R = 0.78f;
        float r = (float) Math.sqrt(u * u + v * v);
        if (r > R) over(o, 0.3f, 0.9f, 1.0f, (float) Math.exp(-(r - R) * 11f) * 0.6f);
        if (r < R) {
            double th = Math.atan2(v, u);
            int sector = (int) Math.floor((th + Math.PI) / (Math.PI * 2) * 8);
            float f = 0.55f + 0.45f * hash(sector, 5, seed);
            float nz = (float) Math.sqrt(Math.max(0f, 1f - (r / R) * (r / R)));
            float sh = 0.3f + 0.9f * light(u / R, v / R, nz);
            float k = cl(f * sh);
            float spark = (float) Math.exp(-((u + 0.3f) * (u + 0.3f) + (v + 0.3f) * (v + 0.3f)) * 25f);
            over(o, cl(0.2f * k + spark), cl(0.8f * k + spark), cl(1.0f * k + spark), smooth(R, R - 0.03f, r));
        }
    }

    // сфера-туманность
    private static void nebulaSphere(float[] o, float u, float v, int seed) {
        float R = 0.78f;
        float r = (float) Math.sqrt(u * u + v * v);
        if (r > R) over(o, 0.85f, 0.3f, 0.95f, (float) Math.exp(-(r - R) * 8f) * 0.65f);
        if (r < R) {
            float nz = (float) Math.sqrt(Math.max(0f, 1f - (r / R) * (r / R)));
            double th = Math.atan2(v, u);
            float sw = fbm((float) (Math.cos(th + r * 3.0) * r * 3f) + seed * 0.1f, (float) (Math.sin(th + r * 3.0) * r * 3f), seed, 5);
            float t = cl(sw * 1.8f - 0.2f);
            float cr = t < 0.5f ? mix(0.1f, 0.6f, t * 2f) : mix(0.6f, 1.0f, (t - 0.5f) * 2f);
            float cg = t < 0.5f ? mix(0.7f, 0.25f, t * 2f) : mix(0.25f, 0.55f, (t - 0.5f) * 2f);
            float cb = t < 0.5f ? mix(0.8f, 0.9f, t * 2f) : mix(0.9f, 0.85f, (t - 0.5f) * 2f);
            float sh = 0.4f + 0.85f * light(u / R, v / R, nz);
            float rim = (float) Math.pow(1f - nz, 2.5) * 0.7f;
            over(o, cl(cr * sh + rim * 0.9f), cl(cg * sh + rim * 0.3f), cl(cb * sh + rim * 0.9f), smooth(R, R - 0.03f, r));
        }
    }

    // золотая звезда с лучами
    private static void star(float[] o, float u, float v, int seed) {
        float r = (float) Math.sqrt(u * u + v * v);
        over(o, 1f, 0.72f, 0.25f, (float) Math.exp(-r * 4.5f) * 0.9f);
        float au = Math.abs(u), av = Math.abs(v);
        float cross = (float) (