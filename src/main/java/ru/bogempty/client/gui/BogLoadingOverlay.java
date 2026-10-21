package ru.bogempty.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import ru.bogempty.client.render.RenderUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Загрузочный экран в стиле BogEmpty поверх ванильного (красный Mojang).
 * Ванильный оверлей продолжает работать под нами и сам завершает загрузку,
 * мы только закрываем его своей картинкой. Текст рисуем пиксельным шрифтом из прямоугольников,
 * потому что обычные шрифты на момент загрузки ещё не готовы.
 */
public class BogLoadingOverlay extends Overlay {
    private static boolean installed = false;

    private static final Map<Character, String[]> G = new HashMap<>();

    private static void g(char ch, String... rows) {
        G.put(ch, rows);
    }

    static {
        g('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        g('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###.");
        g('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        g('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        g('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        g('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        g('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
        g('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        g('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        g('I', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####");
        g('N', "#...#", "##..#", "#.#.#", "#.#.#", "#..##", "#...#", "#...#");
        g('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.");
        g('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.");
        g('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
        g('3', "####.", "....#", "....#", ".###.", "....#", "....#", "####.");
        g('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
        g('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
        g('6', ".###.", "#....", "#....", "####.", "#...#", "#...#", ".###.");
        g('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...");
        g('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
        g('9', ".###.", "#...#", "#...#", ".####", "....#", "....#", ".###.");
        g('%', "##..#", "##..#", "...#.", "..#..", ".#...", "#..##", "#..##");
    }

    private final MinecraftClient mc;
    private final SplashOverlay original;
    private final ResourceReload reload;
    private final long start = Util.getMeasuringTimeMs();
    private long doneAt = -1L;
    private float shown = 0f;

    public BogLoadingOverlay(MinecraftClient mc, SplashOverlay original, ResourceReload reload) {
        this.mc = mc;
        this.original = original;
        this.reload = reload;
    }

    /** Подменяет ванильный экран загрузки. Вызывается каждый тик, сработает один раз. */
    public static void tryInstall(MinecraftClient mc) {
        if (installed) return;
        Overlay o = mc.getOverlay();
        if (!(o instanceof SplashOverlay so)) return;
        installed = true;
        try {
            ResourceReload rl = null;
            for (Field f : SplashOverlay.class.getDeclaredFields()) {
                if (f.getType() == ResourceReload.class) {
                    f.setAccessible(true);
                    rl = (ResourceReload) f.get(so);
                    break;
                }
            }
            if (rl == null) return;
            mc.setOverlay(new BogLoadingOverlay(mc, so, rl));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // ---------- рисование ----------
    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        // ванильный оверлей ведёт загрузку и сам себя убирает, мы рисуем поверх него
        original.render(c, mouseX, mouseY, delta);
        c.draw();
        if (mc.getOverlay() != this) return;

        long now = Util.getMeasuringTimeMs();
        final float t = now - start;

        boolean complete = reload.isComplete();
        if (complete && doneAt < 0) doneAt = now;
        float target = complete ? 1f : Math.min(0.99f, reload.getProgress());
        shown += (target - shown) * 0.08f;

        float f = doneAt < 0 ? -1f : (now - doneAt) / 1000f;
        float fade = f < 1f ? 0f : MathHelper.clamp(f - 1f, 0f, 1f);
        final float a = 1f - fade;
        if (a <= 0f) return;

        int w = c.getScaledWindowWidth();
        int h = c.getScaledWindowHeight();

        // фон
        c.fillGradient(0, 0, w, h, RenderUtils.alpha(0xFF05050C, a), RenderUtils.alpha(0xFF0B0820, a));

        // светящиеся туманности
        orb(c, (int) (w * (0.28f + 0.12f * (float) Math.sin(t * 0.00031f))),
                (int) (h * (0.35f + 0.10f * (float) Math.sin(t * 0.00047f + 1f))), (int) (h * 0.45f), 0x5B3FD0, a * 0.9f);
        orb(c, (int) (w * (0.72f + 0.12f * (float) Math.sin(t * 0.00037f + 2f))),
                (int) (h * (0.62f + 0.10f * (float) Math.sin(t * 0.00029f + 4f))), (int) (h * 0.40f), 0x2D6BFF, a * 0.8f);
        orb(c, (int) (w * (0.50f + 0.20f * (float) Math.sin(t * 0.00023f + 3f))),
                (int) (h * (0.20f + 0.08f * (float) Math.sin(t * 0.00041f))), (int) (h * 0.30f), 0xB03FD0, a * 0.6f);

        // звёзды
        for (int i = 0; i < 120; i++) {
            float layer = 0.3f + 0.7f * hash(i * 7 + 3);
            float sx = (hash(i * 3 + 1) * w - t * 0.004f * layer) % w;
            if (sx < 0) sx += w;
            float sy = hash(i * 5 + 2) * h;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 0.002f * (0.5f + layer) + i);
            int al = (int) (255 * (0.25f + 0.75f * tw) * layer * a);
            int size = layer > 0.85f ? 2 : 1;
            c.fill((int) sx, (int) sy, (int) sx + size, (int) sy + size, (al << 24) | 0xFFFFFF);
        }

        // логотип
        int sc = Math.max(2, Math.min(7, (int) (w * 0.62f / 47f)));
        int logoW = textW("BOGEMPTY", sc, sc);
        int bob = Math.round((float) Math.sin(t * 0.002f) * 1.5f) - Math.round(fade * 12f);
        int lx = (w - logoW) / 2;
        int ly = (int) (h * 0.40f - 3.5f * sc) + bob;
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * 0.003f);

        for (int layer = 4; layer >= 1; layer--) {
            final int ex = layer * Math.max(1, sc / 2);
            final int col = RenderUtils.alpha(0x207C6CFF, a * pulse);
            final int scc = sc;
            walk("BOGEMPTY", lx, ly, sc, sc, (px, py, u) -> c.fill(px - ex, py - ex, px + scc + ex, py + scc + ex, col));
        }
        final int scl = sc;
        walk("BOGEMPTY", lx, ly, sc, sc, (px, py, u) ->
                c.fill(px, py, px + scl, py + scl, RenderUtils.alpha(logoColor(u, t), a)));

        // подпись
        int sc2 = Math.max(1, sc / 3);
        int subW = textW("CLIENT", sc2, sc2 * 3);
        int sx2 = (w - subW) / 2;
        int sy2 = ly + 7 * sc + sc + 2;
        final int s2 = sc2;
        final int subCol = RenderUtils.alpha(0xFF8F86FF, a * 0.9f);
        walk("CLIENT", sx2, sy2, sc2, sc2 * 3, (px, py, u) -> c.fill(px, py, px + s2, py + s2, subCol));
        int lineY = sy2 + 3 * sc2;
        int lineLen = Math.max(12, logoW / 6);
        c.fill(sx2 - 10 - lineLen, lineY, sx2 - 10, lineY + 1, RenderUtils.alpha(0xFF7C6CFF, a * 0.6f));
        c.fill(sx2 + subW + 10, lineY, sx2 + subW + 10 + lineLen, lineY + 1, RenderUtils.alpha(0xFF7C6CFF, a * 0.6f));

        // полоса загрузки
        int bw = Math.min((int) (w * 0.5f), 240);
        int bh = 4;
        int bx = (w - bw) / 2;
        int by = (int) (h * 0.74f);
        int fw = Math.max(4, Math.round(bw * shown));
        int barCol = RenderUtils.lerp(0xFF7C6CFF, 0xFF6CD0FF, shown);

        RenderUtils.drawRoundedRect(c, bx - 1, by - 1, bx + bw + 1, by + bh + 1, 3, RenderUtils.alpha(0x22FFFFFF, a));
        RenderUtils.drawRoundedRect(c, bx, by, bx + bw, by + bh, 2, RenderUtils.alpha(0xFF181824, a));
        RenderUtils.drawRoundedRect(c, bx - 2, by - 2, bx + fw + 2, by + bh + 2, 4, RenderUtils.alpha((barCol & 0xFFFFFF) | 0x30000000, a));
        RenderUtils.drawRoundedRect(c, bx, by, bx + fw, by + bh, 2, RenderUtils.alpha(barCol, a));
        c.fill(bx + fw - 2, by, bx + fw, by + bh, RenderUtils.alpha(0xFFFFFFFF, a));

        // проценты
        String pct = Math.round(shown * 100f) + "%";
        int sc3 = Math.max(1, sc / 2);
        int pw = textW(pct, sc3, sc3);
        final int s3 = sc3;
        final int pctCol = RenderUtils.alpha(0xFFB8B8D0, a);
        walk(pct, (w - pw) / 2, by - 7 * sc3 - 8, sc3, sc3, (px, py, u) -> c.fill(px, py, px + s3, py + s3, pctCol));
    }

    // ---------- помощники ----------
    private interface PixelFn {
        void at(int x, int y, float u);
    }

    private static int textW(String s, int sc, int gap) {
        return s.length() * (5 * sc + gap) - gap;
    }

    private static void walk(String s, int x, int y, int sc, int gap, PixelFn fn) {
        int total = Math.max(1, textW(s, sc, gap));
        int cx = x;
        for (char ch : s.toCharArray()) {
            String[] rows = G.get(ch);
            if (rows != null) {
                for (int r = 0; r < 7; r++) {
                    for (int col = 0; col < 5; col++) {
                        if (rows[r].charAt(col) == '#') {
                            int px = cx + col * sc;
                            fn.at(px, y + r * sc, (px - x) / (float) total);
                        }
                    }
                }
            }
            cx += 5 * sc + gap;
        }
    }

    /** Градиент по ширине надписи и бегущий блик. */
    private static int logoColor(float u, float t) {
        int base = RenderUtils.lerp(0xFF8F86FF, 0xFF6CD0FF, u);
        float pos = ((t % 2800f) / 2800f) * 1.6f - 0.3f;
        float k = Math.max(0f, 1f - Math.abs(u - pos) / 0.12f);
        return RenderUtils.lerp(base, 0xFFFFFFFF, k * 0.85f);
    }

    private static void orb(DrawContext c, int cx, int cy, int radius, int rgb, float a) {
        int layers = 8;
        for (int i = 0; i < layers; i++) {
            int rr = Math.round(radius * (1f - i / (float) layers));
            if (rr < 2) break;
            int col = (0x0E << 24) | (rgb & 0xFFFFFF);
            RenderUtils.drawRoundedRect(c, cx - rr, cy - rr, cx + rr, cy + rr, rr, RenderUtils.alpha(col, a));
        }
    }

    private static float hash(int i) {
        int h = i * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / 16777216f;
    }
}