package ru.bogempty.client.module.visual;

import net.minecraft.client.gui.DrawContext;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;

public class CustomCrosshair extends Module {
    private final Setting.Num size = add(new Setting.Num("Size", 6, 2, 12, 1));
    private final Setting.Num gap = add(new Setting.Num("Gap", 3, 0, 8, 1));
    private final Setting.Num thickness = add(new Setting.Num("Thickness", 1, 1, 3, 1));
    private final Setting.Bool dot = add(new Setting.Bool("Center dot", false));

    public CustomCrosshair() {
        super("CustomCrosshair", "Customizable crosshair", Category.VISUAL);
    }

    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;
        int s = (int) size.get(), g = (int) gap.get(), t = (int) thickness.get();
        int color = 0xFF7C6CFF;
        int lo = -(t / 2), hi = lo + t;

        RenderUtils.drawRect(c, cx - g - s, cy + lo, cx - g, cy + hi, color);
        RenderUtils.drawRect(c, cx + g, cy + lo, cx + g + s, cy + hi, color);
        RenderUtils.drawRect(c, cx + lo, cy - g - s, cx + hi, cy - g, color);
        RenderUtils.drawRect(c, cx + lo, cy + g, cx + hi, cy + g + s, color);
        if (dot.get()) RenderUtils.drawRect(c, cx + lo, cy + lo, cx + hi, cy + hi, color);
    }
}