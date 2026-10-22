package ru.bogempty.client.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.bogempty.client.alt.AltManager;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Главное меню BogEmpty.
 * Фон: положи картинку в config/bogempty/background.png (или .jpg), иначе рисуется анимированный космос.
 * Ссылки слева меняются в константах ниже. Клик по ссылке копирует её в буфер.
 */
public class BogTitleScreen extends Screen {
    // ===== поменяй под себя =====
    private static final String CREATOR = "BogEmpty";
    private static final String TELEGRAM = "@BogEmptyy";
    private static final String DISCORD = "твой_дискорд";
    // ============================

    private static final int ACCENT = 0xFF7C6CFF;
    private static final int ACCENT2 = 0xFF6CD0FF;

    private static final Identifier BG_ID = Identifier.of("bogempty", "title_bg");
    private static boolean bgTried = false;
    private static boolean bgOk = false;
    private static int bgW = 1, bgH = 1;

    private static final class Entry {
        final String label;
        final Runnable action;
        float hover = 0f;

        Entry(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final long start = System.currentTimeMillis();
    private String toast = "";
    private long toastUntil = 0L;
    private final float[] linkHover = new float[3];
    private float accHover = 0f;

    public BogTitleScreen() {
        super(Text.literal("BogEmpty"));
    }

    @Override
    protected void init() {
        entries.clear();
        entries.add(new Entry("Одиночная игра", () -> client.setScreen(new SelectWorldScreen(this))));
        entries.add(new Entry("Сетевая игра", () -> client.setScreen(new MultiplayerScreen(this))));
        entries.add(new Entry("Альт-менеджер", () -> client.setScreen(new AltManagerScreen(this))));
        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            entries.add(new Entry("Моды", () -> {
                try {
                    Class<?> k = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
                    client.setScreen((Screen) k.getConstructor(Screen.class).newInstance(this));
                } catch (Throwable t) {
                    say("Mod Menu недоступен");
                }
            }));
        }
        entries.add(new Entry("Настройки", () -> client.setScreen(new OptionsScreen(this, client.options))));
        entries.add(new Entry("Выйти из игры", () -> client.scheduleStop()));
    }

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float d) {}

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ---------- геометрия ----------
    private int menuTop() { return height / 2 - 6; }
    private static final int ROW = 24;

    private boolean overRow(int i, int mx, int my) {
        Entry e = entries.get(i);
        int tw = Fonts.width(e.label);
        int cx = width / 2;
        int y = menuTop() + i * ROW;
        return mx >= cx - tw / 2 - 40 && mx < cx + tw / 2 + 40 && my >= y - 4 && my < y + ROW - 4;
    }

    private static final int LX = 14, LY = 14, LW = 206;

    private boolean overLink(int i, int mx, int my) {
        int y = LY + 34 + i * 26;
        return mx >= LX + 6 && mx < LX + LW - 6 && my >= y - 3 && my < y + 22;
    }

    private int accW() { return 156; }
    private boolean overAcc(int mx, int my) {
        int x = width - 14 - accW();
        return mx >= x && mx < x + accW() && my >= 14 && my < 56;
    }

    // ---------- фон ----------
    private static void loadBg() {
        if (bgTried) return;
        bgTried = true;
        try {
            File dir = FabricLoader.getInstance().getConfigDir().resolve("bogempty").toFile();
            File f = null;
            for (String n : new String[]{"background.png", "background.jpg", "background.jpeg"}) {
                File t = new File(dir, n);
                if (t.exists()) { f = t; break; }
            }
            if (f == null) return;
            BufferedImage img = ImageIO.read(f);
            if (img == null) return;
            if (img.getWidth() > 2048) {
                int nw = 2048;
                int nh = Math.max(1, img.getHeight() * nw / img.getWidth());
                BufferedImage s = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = s.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(img, 0, 0, nw, nh, null);
                g.dispose();
                img = s;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            NativeImage ni = NativeImage.read(new ByteArrayInputStream(out.toByteArray()));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(ni);
            tex.setFilter(true, false);
            net.minecraft.client.MinecraftClient.getInstance().getTextureManager().registerTexture(BG_ID, tex);
            bgW = img.getWidth();
            bgH = img.getHeight();
            bgOk = true;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void drawBackground(DrawContext c, float t) {
        int w = width, h = height;
        if (bgOk) {
            float sc = Math.max(w / (float) bgW, h / (float) bgH);
            float ox = (w - bgW * sc) / 2f;
            float oy = (h - bgH * sc) / 2f;
            MatrixStack ms = c.getMatrices();
            ms.push();
            ms.translate(ox, oy, 0);
            ms.scale(sc, sc, 1f);
            c.drawTexture(RenderLayer::getGuiTextured, BG_ID, 0, 0, 0f, 0f, bgW, bgH, bgW, bgH);
            ms.pop();
            c.fillGradient(0, 0, w, h, 0x88070410, 0xDD0B0620);
            return;
        }
        c.fillGradient(0, 0, w, h, 0xFF05050C, 0xFF0B0820);
        orb(c, (int) (w * (0.25f + 0.12f * (float) Math.sin(t * 0.00031f))), (int) (h * (0.35f + 0.10f * (float) Math.sin(t * 0.00047f + 1f))), (int) (h * 0.5f), 0x5B3FD0);
        orb(c, (int) (w * (0.75f + 0.12f * (float) Math.sin(t * 0.00037f + 2f))), (int) (h * (0.65f + 0.10f * (float) Math.sin(t * 0.00029f + 4f))), (int) (h * 0.45f), 0x2D6BFF);
        orb(c, (int) (w * (0.5f + 0.2f * (float) Math.sin(t * 0.00023f + 3f))), (int) (h * (0.15f + 0.08f * (float) Math.sin(t * 0.00041f))), (int) (h * 0.32f), 0xB03FD0);
        for (int i = 0; i < 140; i++) {
            float layer = 0.3f + 0.7f * hash(i * 7 + 3);
            float sx = (hash(i * 3 + 1) * w - t * 0.004f * layer) % w;
            if (sx < 0) sx += w;
            float sy = hash(i * 5 + 2) * h;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 0.002f * (0.5f + layer) + i);
            int al = (int) (255 * (0.25f + 0.75f * tw) * layer);
            int size = layer > 0.85f ? 2 : 1;
            c.fill((int) sx, (int) sy, (int) sx + size, (int) sy + size, (al << 24) | 0xFFFFFF);
        }
    }

    private static void orb(DrawContext c, int cx, int cy, int radius, int rgb) {
        for (int i = 0; i < 8; i++) {
            int rr = Math.round(radius * (1f - i / 8f));
            if (rr < 2) break;
            RenderUtils.drawRoundedRect(c, cx - rr, cy - rr, cx + rr, cy + rr, rr, (0x0E << 24) | rgb);
        }
    }

    private static float hash(int i) {
        int h = i * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / 16777216f;
    }

    // ---------- рисование ----------
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        loadBg();
        long now = System.currentTimeMillis();
        float t = now - start;
        float in = Math.min(1f, t / 600f);
        float a = in * in * (3f - 2f * in);

        drawBackground(c, t);

        // логотип с бегущим бликом
        String logo = "BOGEMPTY";
        float ls = 3.2f;
        int lw = Math.round(Fonts.width(logo) * ls);
        int lx = width / 2 - lw / 2;
        int ly = menuTop() - 78 + Math.round((1f - a) * 10f);
        shimmer(c, logo, lx, ly, ls, t, a);
        String sub = "C  L  I  E  N  T";
        Fonts.draw(c, sub, width / 2 - Fonts.width(sub) / 2, ly + Math.round(9 * ls) + 6, RenderUtils.alpha(ACCENT, a));

        // меню
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            boolean hov = overRow(i, mx, my);
            e.hover += ((hov ? 1f : 0f) - e.hover) * 0.22f;
            int tw = Fonts.width(e.label);
            int cx = width / 2;
            int slide = Math.round((1f - a) * 14f * (i + 1) / entries.size());
            int y = menuTop() + i * ROW + slide;

            if (e.hover > 0.02f) {
                RenderUtils.drawRoundedRect(c, cx - tw / 2 - 28, y - 4, cx + tw / 2 + 28, y + 15, 7,
                        RenderUtils.alpha(0x30FFFFFF, e.hover * a * 0.6f));
                int ul = Math.round(tw * e.hover);
                RenderUtils.drawRect(c, cx - ul / 2, y + 14, cx + ul / 2, y + 15, RenderUtils.alpha(ACCENT, e.hover * a));
            }
            int col = RenderUtils.lerp(0xFFB8B8D0, 0xFFFFFFFF, e.hover);
            Fonts.draw(c, e.label, cx - tw / 2, y, RenderUtils.alpha(col, a));
        }

        // карточка со ссылками
        card(c, LX, LY, LW, 34 + 3 * 26 + 8, a);
        Fonts.draw(c, "BogEmpty", LX + 12, LY + 10, RenderUtils.alpha(0xFFFFFFFF, a));
        Fonts.draw(c, "Client", LX + 12 + Fonts.width("BogEmpty "), LY + 10, RenderUtils.alpha(ACCENT, a));
        String[] labels = {"Создатель", "Telegram", "Discord"};
        String[] values = {CREATOR, TELEGRAM, DISCORD};
        String[] icons = {"C", "T", "D"};
        int[] icol = {0xFF7C6CFF, 0xFF4AA8E8, 0xFF7A83F5};
        for (int i = 0; i < 3; i++) {
            linkHover[i] += ((overLink(i, mx, my) ? 1f : 0f) - linkHover[i]) * 0.25f;
            int y = LY + 34 + i * 26;
            if (linkHover[i] > 0.02f) {
                RenderUtils.drawRoundedRect(c, LX + 6, y - 3, LX + LW - 6, y + 22, 6, RenderUtils.alpha(0x30FFFFFF, linkHover[i] * a * 0.6f));
            }
            RenderUtils.drawRoundedRect(c, LX + 10, y, LX + 30, y + 19, 5, RenderUtils.alpha(icol[i], a));
            Fonts.draw(c, icons[i], LX + 20 - Fonts.width(icons[i]) / 2, y + 5, RenderUtils.alpha(0xFFFFFFFF, a));
            Fonts.draw(c, labels[i], LX + 38, y + 1, RenderUtils.alpha(0xFF8A8AA6, a));
            Fonts.draw(c, values[i], LX + 38, y + 10, RenderUtils.alpha(0xFFFFFFFF, a));
        }

        // аккаунт
        String acc = AltManager.current();
        int ax = width - 14 - accW();
        accHover += ((overAcc(mx, my) ? 1f : 0f) - accHover) * 0.25f;
        card(c, ax, 14, accW(), 42, a);
        if (accHover > 0.02f) {
            RenderUtils.drawRoundedRect(c, ax, 14, ax + accW(), 56, 8, RenderUtils.alpha(0x28FFFFFF, accHover * a));
        }
        int hue = 0xFF000000 | (int) (hash(acc.hashCode()) * 0x70) + 0x50 << 16 | (int) (hash(acc.hashCode() + 5) * 0x60) + 0x40 << 8 | 0xE0;
        RenderUtils.drawRoundedRect(c, ax + 8, 21, ax + 36, 49, 7, RenderUtils.alpha(hue, a));
        String ini = acc.isEmpty() ? "?" : acc.substring(0, 1).toUpperCase();
        Fonts.draw(c, ini, ax + 22 - Fonts.width(ini) / 2, 31, RenderUtils.alpha(0xFFFFFFFF, a));
        Fonts.draw(c, "Аккаунт", ax + 44, 22, RenderUtils.alpha(0xFF8A8AA6, a));
        Fonts.draw(c, acc, ax + 44, 33, RenderUtils.alpha(0xFFFFFFFF, a));

        // низ
        String ver = "Minecraft 1.21.4 / Fabric (" + FabricLoader.getInstance().getAllMods().size() + " модов)";
        Fonts.draw(c, ver, 8, height - 14, RenderUtils.alpha(0xFF8A8AA6, a));
        String cr = "BogEmpty Client";
        Fonts.draw(c, cr, width - 8 - Fonts.width(cr), height - 14, RenderUtils.alpha(0xFF8A8AA6, a));

        // уведомление
        long left = toastUntil - now;
        if (left > 0 && !toast.isEmpty()) {
            float ta = Math.min(1f, left / 400f);
            int tw = Fonts.width(toast);
            int x = (width - tw) / 2;
            int y = height - 44;
            RenderUtils.drawRoundedRect(c, x - 8, y - 4, x + tw + 8, y + 13, 5, RenderUtils.alpha(0xCC0C0C14, ta));
            RenderUtils.drawRect(c, x - 8, y + 12, x + tw + 8, y + 13, RenderUtils.alpha(ACCENT, ta));
            Fonts.draw(c, toast, x, y, RenderUtils.alpha(0xFFFFFFFF, ta));
        }
    }

    private void card(DrawContext c, int x, int y, int w, int h, float a) {
        RenderUtils.drawRoundedRect(c, x - 1, y - 1, x + w + 1, y + h + 1, 9, RenderUtils.alpha(0x24FFFFFF, a));
        RenderUtils.drawRoundedRect(c, x, y, x + w, y + h, 8, RenderUtils.alpha(0xCC0C0C14, a));
    }

    private void shimmer(DrawContext c, String text, int x, int y, float scale, float t, float a) {
        MatrixStack ms = c.getMatrices();
        ms.push();
        ms.translate(x, y, 0);
        ms.scale(scale, scale, 1f);
        int total = Math.max(1, Fonts.width(text));
        float pos = ((t % 3200f) / 3200f) * 1.6f - 0.3f;
        int cx = 0;
        for (char ch : text.toCharArray()) {
            String s = String.valueOf(ch);
            float u = cx / (float) total;
            int base = RenderUtils.lerp(0xFF8F86FF, ACCENT2, u);
            float k = Math.max(0f, 1f - Math.abs(u - pos) / 0.12f);
            int col = RenderUtils.lerp(base, 0xFFFFFFFF, k * 0.85f);
            Fonts.draw(c, s, cx, 0, RenderUtils.alpha(col, a));
            cx += Fonts.width(s);
        }
        ms.pop();
    }

    private void say(String s) {
        toast = s;
        toastUntil = System.currentTimeMillis() + 1600L;
    }

    // ---------- ввод ----------
    @Override
    public boolean mouseClicked(double dmx, double dmy, int button) {
        int mx = (int) dmx, my = (int) dmy;
        if (button == 0) {
            for (int i = 0; i < entries.size(); i++) {
                if (overRow(i, mx, my)) {
                    entries.get(i).action.run();
                    return true;
                }
            }
            String[] values = {CREATOR, TELEGRAM, DISCORD};
            for (int i = 0; i < 3; i++) {
                if (overLink(i, mx, my)) {
                    client.keyboard.setClipboard(values[i]);
                    say("Скопировано: " + values[i]);
                    return true;
                }
            }
            if (overAcc(mx, my)) {
                client.setScreen(new AltManagerScreen(this));
                return true;
            }
        }
        return super.mouseClicked(dmx, dmy, button);
    }
}