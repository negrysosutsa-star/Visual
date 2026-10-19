package ru.bogempty.client.module.hud;

import net.minecraft.client.gui.DrawContext;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.RenderUtils;

import java.util.Locale;

public class InfoHud extends Module {
    public InfoHud() {
        super("InfoHud", "FPS and coordinates", Category.HUD);
        this.enabled = true;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null) return;

        String fps = "FPS: " + mc.getCurrentFps();
        String xyz = String.format(Locale.ROOT, "XYZ: %.1f / %.1f / %.1f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());

        int x = 6;
        int y = 24;
        int width = Math.max(mc.textRenderer.getWidth(fps), mc.textRenderer.getWidth(xyz)) + 10;

        RenderUtils.drawRoundedRect(context, x - 4, y - 3, x + width, y + 22, 4, 0xAA0A0A12);
        RenderUtils.drawRect(context, x - 4, y - 3, x - 2, y + 22, 0xFF00FFAA);

        context.drawTextWithShadow(mc.textRenderer, fps, x + 2, y, 0xFFFFFFFF);
        context.drawTextWithShadow(mc.textRenderer, xyz, x + 2, y + 11, 0xFFFFFFFF);
    }
}