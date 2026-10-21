package ru.bogempty.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.bogempty.client.BogEmptyClient;
import ru.bogempty.client.config.ConfigManager;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;
import ru.bogempty.client.util.Lang;

import java.util.List;

public class ClickGui extends Screen {

    private static final int W = 340, H = 200, SW = 170;
    private static final int CELL_H = 26, GAP = 6;
    private static final int ACCENT = 0xFF7C6CFF;
    private static final int PANEL = 0xEE0C0C14;
    private static final int BORDER = 0x24FFFFFF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DIM = 0xFF5E5E75;
    private static final int OFF = 0xFF2A2A3A;
    private static final String ALT_LABEL = "Альт-менеджер";

    private float anim = 0f;
    private float scroll = 0f, scrollTarget = 0f;
    private float sScroll = 0f, sScrollTarget = 0f;
    private Category current = Category.COMBAT;
    private Module settingsOf = null;
    private Module binding = null;
    private Setting.Num dragging = null;

    public ClickGui() {
        super(Text.literal("BogEmpty"));
    }

    @Override
    protected void init() {
        super.init();
        anim = 0f;
    }

    // no vanilla blur / darkening
    @Override
    public void renderBackground(DrawContext c, int mx, int my, float d) {}

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        binding = null;
        dragging = null;
        ConfigManager.save(BogEmptyClient.moduleManager);
    }

    // ---------- geometry ----------
    private int px() { return (width - W) / 2; }
    private int top() { return (height - H) / 2 + Math.round((1f - anim) * 10f); }
    private int sx() { return Math.max(4, Math.min(px() + W + 8, width - SW - 4)); }
    private int gx() { return px() + 12; }
    private int gy() { return top() + 32; }
    private int gw() { return W - 24; }
    private int gh() { return H - 42; }
    private int cw() { return (gw() - GAP) / 2; }
    private int cx(int i) { return gx() + (i % 2) * (cw() + GAP); }
    private int cy(int i) { return gy() + (i / 2) * (CELL_H + GAP) - Math.round(scroll); }

    private int sTop() { return top() + 26; }
    private int sViewH() { return H - 26 - 40; }

    private boolean in(int mx, int my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    private boolean inGrid(int mx, int my) {
        return in(mx, my, gx(), gy(), gx() + gw(), gy() + gh());
    }

    private boolean inSettings(int mx, int my) {
        return settingsOf != null && in(mx, my, sx(), top(), sx() + SW, top() + H);
    }

    private float maxScroll(int n) {
        int rows = (n + 1) / 2;
        int content = rows * (CELL_H + GAP) - GAP;
        return Math.max(0, content - gh());
    }

    private static int rowH(Setting s) {
        if (s instanceof Setting.Num) return 26;
        if (s instanceof Setting.Multi m) return 18 + (m.expanded ? m.options.length * 14 : 0);
        return 18;
    }

    private float sMax() {
        if (settingsOf == null) return 0;
        int t = 0;
        for (Setting s : settingsOf.settings) t += rowH(s);
        return Math.max(0, t - sViewH());
    }

    private int[] catRect(int i) {
        Category[] cats = Category.values();
        int x = px() + 14;
        for (int k = 0; k < i; k++) x += Fonts.width(Lang.t(cats[k].name)) + 18;
        return new int[]{x, top() + 10, x + Fonts.width(Lang.t(cats[i].name)), top() + 21};
    }

    private int[] altRect() {
        int w = Fonts.width(ALT_LABEL);
        int x = px() + W - 14 - w;
        return new int[]{x, top() + 10, x + w, top() + 21};
    }

    private static String keyName(int k) {
        try {
            return InputUtil.fromKeyCode(k, 0).getLocalizedText().getString().toUpperCase();
        } catch (Exception e) {
            return "KEY" + k;
        }
    }

    // ---------- render ----------
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        anim += (1f - anim) * 0.2f;
        scroll += (scrollTarget - scroll) * 0.3f;
        float a = anim;

        ctx.fillGradient(0, 0, width, height,
                RenderUtils.alpha(0xFF05050C, a * 0.55f),
                RenderUtils.alpha(0xFF0B0820, a * 0.85f));

        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(width / 2f, top() - 26, 0);
        ms.scale(1.6f, 1.6f, 1f);
        Fonts.draw(ctx, "BogEmpty", -Fonts.width("BogEmpty") / 2, 0, RenderUtils.alpha(WHITE, a));
        ms.pop();

        int x0 = px(), y0 = top();
        RenderUtils.drawRoundedRect(ctx, x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, 9, RenderUtils.alpha(BORDER, a));
        RenderUtils.drawRoundedRect(ctx, x0, y0, x0 + W, y0 + H, 8, RenderUtils.alpha(PANEL, a));

        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            int[] r = catRect(i);
            boolean sel = cats[i] == current;
            boolean hov = in(mx, my, r[0], r[1], r[2], r[3]);
            int col = sel ? WHITE : (hov ? 0xFFB0B0C8 : DIM);
            Fonts.draw(ctx, Lang.t(cats[i].name), r[0], r[1], RenderUtils.alpha(col, a));
            if (sel) RenderUtils.drawRect(ctx, r[0], r[3] + 2, r[2], r[3] + 3, RenderUtils.alpha(ACCENT, a));
            if (i < cats.length - 1) Fonts.draw(ctx, "/", r[2] + 7, r[1], RenderUtils.alpha(0xFF3A3A4C, a));
        }

        int[] ar = altRect();
        boolean altHov = in(mx, my, ar[0] - 2, ar[1] - 2, ar[2] + 2, ar[3] + 2);
        Fonts.draw(ctx, ALT_LABEL, ar[0], ar[1], RenderUtils.alpha(altHov ? 0xFFB8B0FF : 0xFF8F86FF, a));

        String hint = "ЛКМ — вкл/выкл   ПКМ — настройки   СКМ — бинд";
        Fonts.draw(ctx, hint, x0 + (W - Fonts.width(hint)) / 2, y0 + H + 8, RenderUtils.alpha(0xFF4A4A60, a));

        List<Module> list = BogEmptyClient.moduleManager.getModulesByCategory(current);
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll(list.size())));

        ctx.enableScissor(gx(), gy(), gx() + gw(), gy() + gh());
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            m.anim += ((m.enabled ? 1f : 0f) - m.anim) * 0.25f;

            int x = cx(i), y = cy(i), w = cw();
            boolean hov = inGrid(mx, my) && in(mx, my, x, y, x + w, y + CELL_H);

            int bg = RenderUtils.lerp(hov ? 0x88202030 : 0x66181824, 0x6628215A, m.anim);
            RenderUtils.drawRoundedRect(ctx, x, y, x + w, y + CELL_H, 5, RenderUtils.alpha(bg, a));
            if (settingsOf == m) {
                RenderUtils.drawRect(ctx, x, y + 6, x + 2, y + CELL_H - 6, RenderUtils.alpha(ACCENT, a));
            }

            int nameCol = RenderUtils.lerp(DIM, 0xFFF0F0FF, m.anim);
            Fonts.draw(ctx, Lang.t(m.name), x + 9, y + (CELL_H - 9) / 2, RenderUtils.alpha(nameCol, a));

            int tw = 22, th = 10;
            int tx = x + w - tw - 9, ty = y + (CELL_H - th) / 2;
            RenderUtils.drawRoundedRect(ctx, tx, ty, tx + tw, ty + th, 5,
                    RenderUtils.alpha(RenderUtils.lerp(OFF, ACCENT, m.anim), a));
            int kx = tx + 1 + Math.round((tw - th) * m.anim);
            RenderUtils.drawRoundedRect(ctx, kx, ty + 1, kx + th - 2, ty + th - 1, 4, RenderUtils.alpha(WHITE, a));

            String bt = binding == m ? "..." : (m.key != -1 ? keyName(m.key) : null);
            if (bt != null) {
                Fonts.draw(ctx, bt, tx - 6 - Fonts.width(bt), y + (CELL_H - 9) / 2,
                        RenderUtils.alpha(0xFF8F86FF, a));
            }
        }
        ctx.disableScissor();

        if (settingsOf != null) renderSettings(ctx, mx, my, a);
    }

    private void renderSettings(DrawContext ctx, int mx, int my, float a) {
        Module m = settingsOf;
        int sx = sx(), sy = top();

        RenderUtils.drawRoundedRect(ctx, sx - 1, sy - 1, sx + SW + 1, sy + H + 1, 9, RenderUtils.alpha(BORDER, a));
        RenderUtils.drawRoundedRect(ctx, sx, sy, sx + SW, sy + H, 8, RenderUtils.alpha(PANEL, a));
        Fonts.draw(ctx, Lang.t(m.name), sx + 10, sy + 9, RenderUtils.alpha(WHITE, a));

        if (m.settings.isEmpty()) {
            Fonts.draw(ctx, Lang.t("No settings"), sx + 10, sy + 32, RenderUtils.alpha(DIM, a));
            return;
        }

        sScroll += (sScrollTarget - sScroll) * 0.3f;
        sScrollTarget = Math.max(0, Math.min(sScrollTarget, sMax()));

        int vt = sTop(), vb = vt + sViewH();
        String descText = null;

        ctx.enableScissor(sx, vt, sx + SW, vb);
        int y = vt - Math.round(sScroll);
        for (Setting s : m.settings) {
            int h = rowH(s);
            if (y + h >= vt && y <= vb) drawRow(ctx, s, sx, y, a);
            if (in(mx, my, sx, y, sx + SW, y + h) && my >= vt && my < vb) descText = s.desc;
            y += h;
        }
        ctx.disableScissor();

        float max = sMax();
        if (max > 0) {
            int view = sViewH();
            int thumb = Math.max(12, Math.round(view * (view / (view + max))));
            int ty = vt + Math.round((view - thumb) * (sScroll / max));
            RenderUtils.drawRoundedRect(ctx, sx + SW - 4, ty, sx + SW - 2, ty + thumb, 1, RenderUtils.alpha(0x55FFFFFF, a));
        }

        RenderUtils.drawRect(ctx, sx + 10, vb + 2, sx + SW - 10, vb + 3, RenderUtils.alpha(0x22FFFFFF, a));
        if (descText != null && !descText.isEmpty()) {
            List<OrderedText> lines = textRenderer.wrapLines(Fonts.t(Lang.t(descText)), SW - 20);
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                ctx.drawText(textRenderer, lines.get(i), sx + 10, vb + 6 + i * 10,
                        RenderUtils.alpha(0xFF6E6E86, a), false);
            }
        }
    }

    private void drawRow(DrawContext ctx, Setting s, int sx, int y, float a) {
        int label = RenderUtils.alpha(0xFFD8D8EA, a);
        int accent = RenderUtils.alpha(0xFF8F86FF, a);

        if (s instanceof Setting.Bool b) {
            Fonts.draw(ctx, Lang.t(b.name), sx + 10, y + 5, label);
            int tw = 18, th = 9;
            int tx = sx + SW - 14 - tw, ty = y + 4;
            RenderUtils.drawRoundedRect(ctx, tx, ty, tx + tw, ty + th, 4, RenderUtils.alpha(b.value ? ACCENT : OFF, a));
            int kx = b.value ? tx + tw - th + 1 : tx + 1;
            RenderUtils.drawRoundedRect(ctx, kx, ty + 1, kx + th - 2, ty + th - 1, 3, RenderUtils.alpha(WHITE, a));
        } else if (s instanceof Setting.Num n) {
            Fonts.draw(ctx, Lang.t(n.name), sx + 10, y + 2, label);
            String v = n.format();
            Fonts.draw(ctx, v, sx + SW - 14 - Fonts.width(v), y + 2, accent);
            int x0 = sx + 10, w = SW - 24, ty = y + 17;
            float pct = (float) ((n.value - n.min) / (n.max - n.min));
            int fw = Math.round(w * pct);
            RenderUtils.drawRoundedRect(ctx, x0, ty, x0 + w, ty + 3, 1, RenderUtils.alpha(OFF, a));
            RenderUtils.drawRoundedRect(ctx, x0, ty, x0 + Math.max(2, fw), ty + 3, 1, RenderUtils.alpha(ACCENT, a));
            RenderUtils.drawRoundedRect(ctx, x0 + fw - 3, ty - 2, x0 + fw + 3, ty + 5, 3, RenderUtils.alpha(WHITE, a));
        } else if (s instanceof Setting.Mode md) {
            Fonts.draw(ctx, Lang.t(md.name), sx + 10, y + 5, label);
            String v = Lang.t(md.get());
            Fonts.draw(ctx, v, sx + SW - 14 - Fonts.width(v), y + 5, accent);
        } else if (s instanceof Setting.Multi mu) {
            Fonts.draw(ctx, Lang.t(mu.name), sx + 10, y + 5, label);
            int cnt = 0;
            for (boolean b : mu.on) if (b) cnt++;
            String v = cnt + "/" + mu.on.length + (mu.expanded ? "  -" : "  +");
            Fonts.draw(ctx, v, sx + SW - 14 - Fonts.width(v), y + 5, accent);
            if (mu.expanded) {
                for (int i = 0; i < mu.options.length; i++) {
                    int oy = y + 18 + i * 14;
                    boolean on = mu.on[i];
                    RenderUtils.drawRoundedRect(ctx, sx + 14, oy + 2, sx + 22, oy + 10, 2,
                            RenderUtils.alpha(on ? ACCENT : OFF, a));
                    Fonts.draw(ctx, Lang.t(mu.options[i]), sx + 28, oy + 2,
                            RenderUtils.alpha(on ? 0xFFE8E8FF : DIM, a));
                }
            }
        }
    }

    // ---------- input ----------
    private void setFromMouse(Setting.Num n, double mx) {
        double t = (mx - (sx() + 10)) / (double) (SW - 24);
        t = Math.max(0, Math.min(1, t));
        n.set(n.min + t * (n.max - n.min));
    }

    @Override
    public boolean mouseClicked(double dmx, double dmy, int button) {
        int mx = (int) dmx, my = (int) dmy;
        Module wasBinding = binding;
        binding = null;

        int[] ar = altRect();
        if (button == 0 && in(mx, my, ar[0] - 2, ar[1] - 2, ar[2] + 2, ar[3] + 2)) {
            if (client != null) client.setScreen(new AltManagerScreen(this));
            return true;
        }

        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            int[] r = catRect(i);
            if (button == 0 && in(mx, my, r[0] - 2, r[1] - 2, r[2] + 2, r[3] + 3)) {
                current = cats[i];
                scroll = 0;
                scrollTarget = 0;
                return true;
            }
        }

        if (inSettings(mx, my)) {
            int vt = sTop(), vb = vt + sViewH();
            if (my >= vt && my < vb) {
                int y = vt - Math.round(sScroll);
                for (Setting s : settingsOf.settings) {
                    int h = rowH(s);
                    if (my >= y && my < y + h) {
                        if (s instanceof Setting.Bool b) {
                            b.toggle();
                        } else if (s instanceof Setting.Num n) {
                            dragging = n;
                            setFromMouse(n, dmx);
                        } else if (s instanceof Setting.Mode md) {
                            if (button == 1) md.prev();
                            else md.next();
                        } else if (s instanceof Setting.Multi mu) {
                            if (my < y + 18) {
                                mu.expanded = !mu.expanded;
                            } else {
                                int idx = (my - (y + 18)) / 14;
                                if (idx >= 0 && idx < mu.on.length) mu.on[idx] = !mu.on[idx];
                            }
                        }
                        return true;
                    }
                    y += h;
                }
            }
            return true;
        }

        if (inGrid(mx, my)) {
            List<Module> list = BogEmptyClient.moduleManager.getModulesByCategory(current);
            for (int i = 0; i < list.size(); i++) {
                int x = cx(i), y = cy(i);
                if (in(mx, my, x, y, x + cw(), y + CELL_H)) {
                    Module m = list.get(i);
                    if (button == 0) {
                        m.toggle();
                    } else if (button == 1) {
                        settingsOf = (settingsOf == m) ? null : m;
                        sScroll = 0;
                        sScrollTarget = 0;
                    } else if (button == 2) {
                        binding = (wasBinding == m) ? null : m;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(dmx, dmy, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            setFromMouse(dragging, mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double dmx, double dmy, double horizontal, double vertical) {
        int mx = (int) dmx, my = (int) dmy;
        if (inSettings(mx, my)) {
            sScrollTarget = Math.max(0, Math.min(sScrollTarget - (float) vertical * 16f, sMax()));
            return true;
        }
        if (inGrid(mx, my)) {
            int n = BogEmptyClient.moduleManager.getModulesByCategory(current).size();
            scrollTarget = Math.max(0, Math.min(scrollTarget - (float) vertical * 16f, maxScroll(n)));
            return true;
        }
        return super.mouseScrolled(dmx, dmy, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                binding.key = -1;
            } else {
                binding.key = keyCode;
            }
            binding = null;
            return true;
        }
        if (BogEmptyClient.openGuiKey != null && BogEmptyClient.openGuiKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}