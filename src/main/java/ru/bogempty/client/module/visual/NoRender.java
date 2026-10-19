package ru.bogempty.client.module.visual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticlesMode; // <-- ИСПРАВЛЕННЫЙ ИМПОРТ
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;

public class NoRender extends Module {
    // Храним предыдущее состояние частиц, чтобы вернуть его при выключении модуля
    private ParticlesMode previous = null;

    public NoRender() {
        super("NoRender", "Disables rendering of certain things", Category.VISUAL);
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            // Запоминаем текущее состояние
            previous = mc.options.getParticles().getValue();
            // Отключаем частицы
            mc.options.getParticles().setValue(ParticlesMode.MINIMAL);
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null && previous != null) {
            // Возвращаем как было
            mc.options.getParticles().setValue(previous);
            previous = null;
        }
    }
}