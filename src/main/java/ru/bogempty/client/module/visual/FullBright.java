package ru.bogempty.client.module.visual;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;

public class FullBright extends Module {
    public FullBright() {
        super("FullBright", "Maximum brightness", Category.VISUAL);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        StatusEffectInstance e = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (e == null || e.getDuration() < 400) {
            mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, false, false));
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
    }
}