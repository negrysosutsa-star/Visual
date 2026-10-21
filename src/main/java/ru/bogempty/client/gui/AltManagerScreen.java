package ru.bogempty.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.bogempty.client.alt.AltManager;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AltManagerScreen extends Screen {
    private static final int W = 330, H = 214;
    private static final int ROW = 16, ROWS = 6;
    private static final int ACCENT = 0xFF7C6CFF;
    private static final int PANEL = 0xEE0C0C14;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DIM = 0xFF5E5E75;

    private static final String[] A = {"Dark", "Fast", "Cool", "Pro", "Ice", "Fire", "Mega", "Neo", "Epic", "Toxic"};
    private static final String[] B = {"Wolf", "Fox", "Bear", "Hawk", "Tiger", "Ninja", "Knight", "Ghost", "Rider", "Hunter"};

    private final Screen parent;
    private String input = "";
    private int selected = -1;
    private int scroll = 0;
    private String status = "";
    private long lastClick = 0L;
    private int lastIdx = -1;
    private float anim = 0f;

    private static class Btn {
        final String label;
        final int x, y, w, h;
        final Runnable action;

        Btn(String label, int x, int y, int w, int h, Runnable action) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        boolean hit(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public AltManagerScreen(Screen parent) {
        super(Text.literal("Альт-менеджер"));
        this.parent = parent;
        AltManager.ensureLoaded();
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }

    private List<Btn> buttons() {
        int x0 = px(), y0 = py();
        List<Btn> l = new ArrayList<>();
        l.add(new Btn("Добавить", x0 + 168, y0 + 40, 70, 18, this::add));
        l.add(new Btn("Случайный", x0 + 244, y0 + 40, 74, 18, this::randomNick));
        int by = y0 + 172;
        l.add(new Btn("Войти", x0 + 12, by, 98, 20, this::login));
        l.add(new Btn("Удалить", x0 + 116, by, 98, 20, this::remove));
        l.add(new Btn("Назад", x0 + 220, by, 98, 20, this::close));
        return l;
    }

    private void add() {
        String n = input.trim();
        if (!n.matches("[A-Za-z0-9_]{3,16}")) {
            status = "Ник: 3–16 символов, латиница, цифры и _";
            return;
        }
        if (AltManager.alts.contains(n)) {
            status = "Такой ник уже есть";
            return;
        }
        AltManager.alts.add(n);
        AltManager.save();
        selected = AltManager.alts.size() - 1;
        input = "";
        status = "Добавлен: " + n;
    }

    private void randomNick() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String n = A[r.nextInt(A.length)] + B[r.nextInt(B.length)] + r.nextInt(10, 1000);
        if (n.length() > 16) n = n.substring(0, 16);
        input = n;
    }

    private void login() {
        if (selected < 0 || selected >= AltManager.alts.size()) {
            status = "Сначала выбери ник в списке";
            return;
        }
        String n = AltManager.alts.get(selected);
        status = AltManager.login(n) ? "Вы вошли как " + n : "Не удалось сменить аккаунт";
    }

    private void remove() {
        if (selected < 0 || selected >= AltManager.alts.size()) return;
        AltManager.alts.remove(selected);
        AltManager.save();
        selected = Math.min(selected, AltManager.alts.size() - 1);
        status = "Удалён";
    }

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float d) {}

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        anim += (1f - anim) * 0.2f;
        float a = anim;
        c.fillGradient(0, 0, width, height,
                RenderUtils.alpha(0xFF05050C, a * 0.65f), RenderUtils.alpha(0xFF0B0820, a * 0.9f));

        int x0 = px(), y0 = py();
        RenderUtils.drawRoundedRect(c, x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, 9, RenderUtils.alpha(0x24FFFFFF, a));
        RenderUtils.drawRoundedRect(c, x0, y0, x0 + W, y0 + H, 8, RenderUtils.alpha(PANEL, a));

        Fonts.draw(c, "Альт-менеджер", x0 + 12, y0 + 10, RenderUtils.alpha(WHITE, a));
        String cur = "Сейчас: " + AltManager.current();
        Fonts.draw(c, cur, x0 + W - 12 - Fonts.width(cur), y0 + 10, RenderUtils.alpha(0xFF8F86FF, a));

        // поле ввода
        int ix = x0 + 12, iy = y0 + 40, iw = 150, ih = 18;
        RenderUtils.drawRoundedRect(c, ix, iy, ix + iw, iy + ih, 4, RenderUtils.alpha(0xFF181824, a));
        if (input.isEmpty()) {
            Fonts.draw(c, "Введи ник", ix + 6, iy + 5, RenderUtils.alpha(DIM, a));
        } else {
            Fonts.draw(c, input, ix + 6, iy + 5, RenderUtils.alpha(WHITE, a));
        }
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = ix + 6 + (input.isEmpty() ? 0 : Fonts.width(input));
            RenderUtils.drawRect(c, cx, iy + 4, cx + 1, iy + ih - 4, RenderUtils.alpha(WHITE, a));
        }

        // список
        int ly = y0 + 68;
        RenderUtils.drawRoundedRect(c, x0 + 12, ly, x0 + W - 12, ly + ROW * ROWS + 4, 5, RenderUtils.alpha(0x66181824, a));
        int total = AltManager.alts.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - ROWS)));
        if (total == 0) {
            Fonts.draw(c, "Список пуст — добавь ник сверху", x0 + 20, ly + 8, RenderUtils.alpha(DIM, a));
        }
        for (int i = 0; i < ROWS; i++) {
            int idx = i + scroll;
            if (idx >= total) break;
            int ry = ly + 2 + i * ROW;
            boolean sel = idx == selected;
            boolean hov = mx >= x0 + 14 && mx < x0 + W - 14 && my >= ry && my < ry + ROW;
            if (sel) RenderUtils.drawRoundedRect(c, x0 + 14, ry, x0 + W - 14, ry + ROW - 1, 4, RenderUtils.alpha(0x6628215A, a));
            else if (hov) RenderUtils.drawRoundedRect(c, x0 + 14, ry, x0 + W - 14, ry + ROW - 1, 4, RenderUtils.alpha(0x44202030, a));
            String name = AltManager.alts.get(idx);
            Fonts.draw(c, name, x0 + 22, ry + 4, RenderUtils.alpha(sel ? WHITE : 0xFFB0B0C8, a));
            if (name.equals(AltManager.current())) {
                String t = "активен";
                Fonts.draw(c, t, x0 + W - 22 - Fonts.width(t), ry + 4, RenderUtils.alpha(0xFF8F86FF, a));
            }
        }

        for (Btn b : buttons()) {
            boolean hov = b.hit(mx, my);
            RenderUtils.drawRoundedRect(c, b.x, b.y, b.x + b.w, b.y + b.h, 5,
                    RenderUtils.alpha(hov ? 0xFF3A3380 : 0xFF28215A, a));
            Fonts.draw(c, b.label, b.x + (b.w - Fonts.width(b.label)) / 2, b.y + (b.h - 9) / 2,
                    RenderUtils.alpha(WHITE, a));
        }

        if (!status.isEmpty()) {
            Fonts.draw(c, status, x0 + 12, y0 + H - 14, RenderUtils.alpha(0xFF8F86FF, a));
        }
    }

    @Override
    public boolean mouseClicked(double dmx, double dmy, int button) {
        int mx = (int) dmx, my = (int) dmy;
        if (button == 0) {
            for (Btn b : buttons()) {
                if (b.hit(mx, my)) {
                    b.action.run();
                    return true;
                }
            }
            int x0 = px(), ly = py() + 68;
            if (mx >= x0 + 14 && mx < x0 + W - 14 && my >= ly + 2 && my < ly + 2 + ROW * ROWS) {
                int idx = (my - (ly + 2)) / ROW + scroll;
                if (idx >= 0 && idx < AltManager.alts.size()) {
                    long now = System.currentTimeMillis();
                    if (idx == lastIdx && now - lastClick < 400) {
                        selected = idx;
                        login();
                    } else {
                        selected = idx;
                    }
                    lastIdx = idx;
                    lastClick = now;
                    return true;
                }
            }
        }
        return super.mouseClicked(dmx, dmy, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scroll -= (int) Math.signum(vertical);
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if ((Character.isLetterOrDigit(chr) || chr == '_') && chr < 128 && input.length() < 16) {
            input += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            add();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_V && Screen.hasControlDown() && client != null) {
            String clip = client.keyboard.getClipboard();
            StringBuilder sb = new StringBuilder(input);
            for (char ch : clip.toCharArray()) {
                if ((Character.isLetterOrDigit(ch) || ch == '_') && ch < 128 && sb.length() < 16) sb.append(ch);
            }
            input = sb.toString();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}