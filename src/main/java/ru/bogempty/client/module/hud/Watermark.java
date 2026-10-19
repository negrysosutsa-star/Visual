package ru.bogempty.client.module.hud;

import net.minecraft.client.gui.DrawContext;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;

public class Watermark extends Module {
    public Watermark() {
        super("Watermark", "Client watermark", Category.HUD);
        this.enabled = true;
    }

    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        int x = 6, y = 6;
        int w1 = Fonts.width("BogEmpty ");
        int w = w1 + Fonts.width("Client") + 12;
        RenderUtils.drawRoundedRect(c, x, y, x + w, y + 16, 5, 0xAA0C0C14);
        RenderUtils.drawRect(c, x + 3, y + 4, x + 5, y + 12, 0xFF7C6CFF);
        Fonts.draw(c, "BogEmpty ", x + 9, y + 4, 0xFFFFFFFF);
        Fonts.draw(c, "Client", x + 9 + w1, y + 4, 0xFF7C6CFF);
    }
}