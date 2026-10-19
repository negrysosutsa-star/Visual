package ru.bogempty.client.module.visual;

import net.minecraft.client.gui.DrawContext;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.RenderUtils;

public class CustomCrosshair extends Module {
    public CustomCrosshair() {
        super("CustomCrosshair", "Nice animated crosshair", Category.VISUAL);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;

        int size = 6;
        int gap = 3;
        int color = 0xFF00FFAA;

        // Горизонталь
        RenderUtils.drawRect(context, cx - size - gap, cy - 1, cx - gap, cy + 1, color);
        RenderUtils.drawRect(context, cx + gap, cy - 1, cx + size + gap, cy + 1, color);

        // Вертикаль
        RenderUtils.drawRect(context, cx - 1, cy - size - gap, cx + 1, cy - gap, color);
        RenderUtils.drawRect(context, cx - 1, cy + gap, cx + 1, cy + size + gap, color);
    }
}