package ru.bogempty.client.module.hud;

import net.minecraft.client.gui.DrawContext;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;

import java.util.Locale;

public class InfoHud extends Module {
    public InfoHud() {
        super("InfoHud", "FPS and coordinates", Category.HUD);
        this.enabled = true;
    }

    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        if (mc.player == null) return;
        String fps = "FPS  " + mc.getCurrentFps();
        String xyz = String.format(Locale.ROOT, "XYZ  %.1f  %.1f  %.1f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());

        int x = 6, y = 26;
        int w = Math.max(Fonts.width(fps), Fonts.width(xyz)) + 16;
        RenderUtils.drawRoundedRect(c, x, y, x + w, y + 30, 5, 0xAA0C0C14);
        RenderUtils.drawRect(c, x + 3, y + 5, x + 5, y + 25, 0xFF7C6CFF);
        Fonts.draw(c, fps, x + 9, y + 4, 0xFFFFFFFF);
        Fonts.draw(c, xyz, x + 9, y + 16, 0xFFB8B8D0);
    }
}