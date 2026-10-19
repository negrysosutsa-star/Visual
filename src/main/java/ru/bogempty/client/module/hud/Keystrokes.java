package ru.bogempty.client.module.hud;

import net.minecraft.client.gui.DrawContext;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;

public class Keystrokes extends Module {
    public Keystrokes() {
        super("Keystrokes", "WASD and space overlay", Category.HUD);
    }

    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        if (mc.options == null) return;
        int bx = 8;
        int by = mc.getWindow().getScaledHeight() / 2 - 26;

        key(c, bx + 24, by, 22, 22, "W", mc.options.forwardKey.isPressed());
        key(c, bx, by + 24, 22, 22, "A", mc.options.leftKey.isPressed());
        key(c, bx + 24, by + 24, 22, 22, "S", mc.options.backKey.isPressed());
        key(c, bx + 48, by + 24, 22, 22, "D", mc.options.rightKey.isPressed());
        key(c, bx, by + 48, 70, 14, "SPACE", mc.options.jumpKey.isPressed());
    }

    private void key(DrawContext c, int x, int y, int w, int h, String label, boolean down) {
        RenderUtils.drawRoundedRect(c, x, y, x + w, y + h, 4, down ? 0xAA7C6CFF : 0x880C0C14);
        int tx = x + (w - Fonts.width(label)) / 2;
        int ty = y + (h - 9) / 2;
        Fonts.draw(c, label, tx, ty, down ? 0xFFFFFFFF : 0xFF9A9AB4);
    }
}