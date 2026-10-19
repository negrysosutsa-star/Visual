package ru.bogempty.client.module.hud;

import net.minecraft.client.gui.DrawContext;
import ru.bogempty.client.BogEmptyClient;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
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
    public void onRender2D(DrawContext context, float tickDelta) {
        List<Module> enabled = BogEmptyClient.moduleManager.modules.stream()
                .filter(Module::isEnabled)
                .filter(m -> !(m instanceof ArrayListModule) && !(m instanceof Watermark))
                .sorted(Comparator.comparingInt(m -> -mc.textRenderer.getWidth(m.name)))
                .collect(Collectors.toList());

        int y = 22;
        int screenWidth = mc.getWindow().getScaledWidth();

        for (Module m : enabled) {
            String name = m.name;
            int width = mc.textRenderer.getWidth(name) + 8;
            int x = screenWidth - width - 4;

            RenderUtils.drawRoundedRect(context, x, y - 1, screenWidth - 2, y + 11, 3, 0xAA0A0A12);
            RenderUtils.drawRect(context, screenWidth - 3, y - 1, screenWidth - 1, y + 11, 0xFF00FFAA);

            context.drawTextWithShadow(mc.textRenderer, name, x + 3, y + 1, 0xFF00FFAA);
            y += 13;
        }
    }
}