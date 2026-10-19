package ru.bogempty.client.module.visual;

import net.minecraft.client.option.ParticlesMode;
import net.minecraft.entity.effect.StatusEffects;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

public class NoRender extends Module {
    private final Setting.Bool weather = add(new Setting.Bool("Weather", true),
            "Remove rain and thunder.");
    private final Setting.Bool effects = add(new Setting.Bool("Bad effects", true),
            "Remove blindness, nausea and darkness visuals.");
    private final Setting.Bool hurt = add(new Setting.Bool("Hurt shake", true),
            "Remove camera shake when you take damage.");
    private final Setting.Bool particles = add(new Setting.Bool("Particles", false),
            "Switch particles to the minimal level.");

    private ParticlesMode previous = null;
    private boolean applied = false;

    public NoRender() {
        super("NoRender", "Hides annoying visual effects", Category.VISUAL);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) previous = mc.options.getParticles().getValue();
        applied = false;
    }

    @Override
    public void onDisable() {
        restoreParticles();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.options == null) return;

        if (weather.get()) {
            mc.world.setRainGradient(0f);
            mc.world.setThunderGradient(0f);
        }
        if (effects.get()) {
            mc.player.removeStatusEffect(StatusEffects.BLINDNESS);
            mc.player.removeStatusEffect(StatusEffects.NAUSEA);
            mc.player.removeStatusEffect(StatusEffects.DARKNESS);
        }
        if (hurt.get()) {
            mc.player.hurtTime = 0;
        }
        if (particles.get()) {
            if (!applied) {
                previous = mc.options.getParticles().getValue();
                applied = true;
            }
            mc.options.getParticles().setValue(ParticlesMode.MINIMAL);
        } else {
            restoreParticles();
        }
    }

    private void restoreParticles() {
        if (applied && previous != null && mc.options != null) {
            mc.options.getParticles().setValue(previous);
        }
        applied = false;
    }
}