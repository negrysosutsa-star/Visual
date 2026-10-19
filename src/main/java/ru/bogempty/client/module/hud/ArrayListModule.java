package ru.bogempty.client.module.hud;

import net.minecraft.client.gui.DrawContext;
import ru.bogempty.client.BogEmptyClient;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ArrayListModule extends Module {
    public ArrayListModule() {
        super("ArrayList", "Shows enabled modules", Category.HUD);
        this.enabled = true;
    }

    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        List<Module> list = BogEmptyClient.moduleManager.modules.stream()
                .filter(Module::isEnabled)
                .filter(m -> m.category != Category.HUD)
                .sorted(Comparator.comparingInt(m -> -Fonts.width(m.name)))
                .collect(Collectors.toList());

        int sw = mc.getWindow().getScaledWidth();
        int y = 6;
        for (Module m : list) {
            int w = Fonts.width(m.name) + 12;
            int x = sw - w - 4;
            RenderUtils.drawRoundedRect(c, x, y, sw - 4, y + 15, 4, 0xAA0C0C14);
            RenderUtils.drawRect(c, sw - 6, y + 3, sw - 4, y + 12, 0xFF7C6CFF);
            Fonts.draw(c, m.name, x + 5, y + 3, 0xFFE8E8FF);
            y += 17;
        }
    }
}