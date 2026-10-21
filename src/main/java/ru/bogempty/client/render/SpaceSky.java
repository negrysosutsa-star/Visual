package ru.bogempty.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Космическое небо без миксинов: миру подменяется тип неба на "Энд",
 * а текстура неба Энда заменяется на сгенерированную (галактика, туманность и т.д.).
 */
public final class SpaceSky {
    private static final Identifier END_SKY = Identifier.of("minecraft", "textures/environment/end_sky.png");
    private static final int SIZE = 512;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bogempty-sky");
        t.setDaemon(true);
        return t;
    });

    private record Ready(String key, byte[] png) {}

    private static DimensionEffects endFx;
    private static Field fxField;
    private static boolean fieldSearched = false;
    private static boolean failed = false;
    private static DimensionEffects original;
    private static ClientWorld originalWorld;

    private static volatile String requestedKey = "";
    private static volatile Ready ready;
    private static NativeImageBackedTexture current;
    private static boolean active = false;
    private static int checkTimer = 0;

    public static void update(ClientWorld world, String style, int density, int sat, int variant) {
        active = true;
        swapEffects(world);

        final String key = style + "|" + density + "|" + sat + "|" + variant;
        if (!key.equals(requestedKey)) {
            requestedKey = key;
            POOL.execute(() -> {
                try {
                    byte[] png = generate(style, density, sat, variant);
                    if (key.equals(requestedKey)) ready = new Ready(key, png);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        }

        Ready r = ready;
        if (r != null) {
            ready = null;
            upload(r.png());
        } else if (current != null && ++checkTimer % 100 == 0) {
            try {
                if (MinecraftClient.getInstance().getTextureManager().getTexture(END_SKY) != current) {
                    MinecraftClient.getInstance().getTextureManager().registerTexture(END_SKY, current);
                }
            } catch (Throwable ignored) {}
        }
    }

    public static void restore() {
        if (!active) return;
        active = false;
        requestedKey = "";
        ready = null;
        try {
            if (original != null && originalWorld != null && fxField != null) {
                fxField.set(originalWorld, original);
            }
        } catch (Throwable ignored) {}
        original = null;
        originalWorld = null;
        if (current != null) {
            try {
                MinecraftClient.getInstance().getTextureManager().registerTexture(END_SKY, new ResourceTexture(END_SKY));
            } catch (Throwable t) {
                t.printStackTrace();
            }
            current = null;
        }
    }

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

    private static void upload(byte[] png) {
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            MinecraftClient.getInstance().getTextureManager().registerTexture(END_SKY, tex);
            current = tex;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // ---------------- генерация ----------------

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

    private static byte[] generate(String style, int density, int sat, int variant) throws Exception {
        final int S = SIZE;
        final int seed = variant * 7919 + 13;

        float[][] pal;
        float nebAmt, nebScale;
        boolean galaxy = false, aurora = false;
        switch (style) {
            case "Галактика":
                pal = new float[][]{{0.10f, 0.12f, 0.45f}, {0.45f, 0.25f, 0.80f}, {0.85f, 0.55f, 0.95f}};
                nebAmt = 0.55f; nebScale = 3.0f; galaxy = true;
                break;
            case "Туманность":
                pal = new float[][]{{0.10f, 0.55f, 0.80f}, {0.80f, 0.20f, 0.65f}, {0.98f, 0.60f, 0.25f}};
                nebAmt = 1.15f; nebScale = 2.4f;
                break;
            case "Звёздный рой":
                pal = new float[][]{{0.15f, 0.25f, 0.60f}, {0.35f, 0.45f, 0.85f}, {0.70f, 0.80f, 1.00f}};
                nebAmt = 0.25f; nebScale = 4.0f;
                break;
            case "Космический закат":
                pal = new float[][]{{0.45f, 0.10f, 0.50f}, {0.95f, 0.35f, 0.30f}, {1.00f, 0.75f, 0.35f}};
                nebAmt = 1.0f; nebScale = 2.0f;
                break;
            default:
                pal = new float[][]{{0.05f, 0.60f, 0.40f}, {0.20f, 0.90f, 0.70f}, {0.55f, 0.35f, 0.90f}};
                nebAmt = 1.0f; nebScale = 2.5f; aurora = true;
                break;
        }

        final float p0 = 0.04f + density * 0.012f;
        final float p1 = 0.004f + density * 0.0018f;
        final float satK = sat / 100f;

        int[] px = new int[S * S];
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                float u = (x + 0.5f) / S, v = (y + 0.5f) / S;
                float edge = smooth(0f, 0.2f, Math.min(Math.min(u, 1f - u), Math.min(v, 1f - v)));

                float r = 0.004f, g = 0.005f, b = 0.012f;

                // туманность / аврора
                float n1 = fbm(u * nebScale + seed * 0.37f, v * nebScale + seed * 0.11f, seed, 5);
                float tint = fbm(u * nebScale * 1.7f + 40f, v * nebScale * 1.7f + 17f, seed + 5, 4);
                float k;
                if (aurora) {
                    double wave = Math.sin((v * 7.0 + n1 * 4.0) * Math.PI);
                    float band = smooth(0.2f, 1f, (float) wave);
                    k = nebAmt * band * (0.3f + 0.7f * (1f - v * 0.6f)) * (0.4f + 0.6f * edge);
                } else {
                    k = nebAmt * smooth(0.38f, 0.82f, n1) * (0.35f + 0.65f * edge);
                }
                float t3 = Math.max(0f, Math.min(1f, tint)) * 2f;
                int i0 = Math.min(1, (int) t3);
                float f = t3 - i0;
                float cr = pal[i0][0] + (pal[i0 + 1][0] - pal[i0][0]) * f;
                float cg = pal[i0][1] + (pal[i0 + 1][1] - pal[i0][1]) * f;
                float cb = pal[i0][2] + (pal[i0 + 1][2] - pal[i0][2]) * f;
                r += cr * k; g += cg * k; b += cb * k;

                // спиральная галактика
                if (galaxy) {
                    float dx = u - 0.5f, dy = v - 0.5f;
                    float ca = (float) Math.cos(0.5), sa = (float) Math.sin(0.5);
                    float rx = dx * ca + dy * sa;
                    float ry = (-dx * sa + dy * ca) * 2.4f;
                    float rr = (float) Math.sqrt(rx * rx + ry * ry);
                    double th = Math.atan2(ry, rx);
                    double armD = 0.5 + 0.5 * Math.cos(2.0 * th - 9.0 * Math.log(rr + 0.04) + seed);
                    float arm = (float) armD;
                    float disk = (float) Math.exp(-rr * 5.5f);
                    float core = (float) Math.exp(-rr * 22f);
                    float dust = 0.6f + 0.4f * fbm(u * 9f, v * 9f, seed + 9, 3);
                    float gl = disk * (0.25f + 0.75f * arm * arm) * dust * edge;
                    core *= edge;
                    r += gl * 0.75f + core;
                    g += gl * 0.65f + core * 0.9f;
                    b += gl * 0.95f + core * 0.8f;
                }

                // звёзды
                for (int layer = 0; layer < 2; layer++) {
                    int cs = layer == 0 ? 5 : 11;
                    int cx = x / cs, cy = y / cs;
                    float prob = layer == 0 ? p0 : p1;
                    if (hash(cx, cy, seed + layer * 101) >= prob) continue;
                    float sx = cx * cs + 1.5f + hash(cx, cy, seed + 7 + layer) * (cs - 3f);
                    float sy = cy * cs + 1.5f + hash(cx, cy, seed + 13 + layer) * (cs - 3f);
                    float ddx = x + 0.5f - sx, ddy = y + 0.5f - sy;
                    float d2 = ddx * ddx + ddy * ddy;
                    float br = 0.4f + 0.6f * hash(cx, cy, seed + 29 + layer);
                    br = br * br;
                    float sigma = layer == 0 ? 0.55f : 0.9f;
                    float val = br * (float) Math.exp(-d2 / (2f * sigma * sigma)) * (layer == 0 ? 1f : 1.6f);
                    float tmp = hash(cx, cy, seed + 31);
                    float sr, sg, sb;
                    if (tmp < 0.25f) { sr = 0.7f; sg = 0.8f; sb = 1f; }
                    else if (tmp < 0.8f) { sr = 1f; sg = 1f; sb = 1f; }
                    else { sr = 1f; sg = 0.85f; sb = 0.65f; }
                    r += val * sr; g += val * sg; b += val * sb;
                }

                // тон-маппинг и насыщенность
                r = 1f - (float) Math.exp(-r * 1.5f);
                g = 1f - (float) Math.exp(-g * 1.5f);
                b = 1f - (float) Math.exp(-b * 1.5f);
                float lum = 0.3f * r + 0.59f * g + 0.11f * b;
                r = Math.max(0f, Math.min(1f, lum + (r - lum) * satK));
                g = Math.max(0f, Math.min(1f, lum + (g - lum) * satK));
                b = Math.max(0f, Math.min(1f, lum + (b - lum) * satK));

                px[y * S + x] = 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
            }
        }

        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, S, S, px, 0, S);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private SpaceSky() {}
}