package ru.bogempty.client.module.hud;

import net.minecraft.client.gui.DrawContext;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.RenderUtils;

public class Watermark extends Module {
    public Watermark() {
        super("Watermark", "Client watermark", Category.HUD);
        this.enabled = true;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        String text = "BogEmpty §bClient";
        int x = 6;
        int y = 6;

        int width = mc.textRenderer.getWidth(text) + 10;
        RenderUtils.drawRoundedRect(context, x - 4, y - 3, x + width, y + 12, 4, 0xAA0A0A12);
        RenderUtils.drawRect(context, x - 4, y - 3, x - 2, y + 12, 0xFF00FFAA);

        context.drawTextWithShadow(mc.textRenderer, text, x, y, 0xFFFFFFFF);
    }
}