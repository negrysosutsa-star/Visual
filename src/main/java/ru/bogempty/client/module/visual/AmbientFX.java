package ru.bogempty.client.module.visual;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

/**
 * Subtle client-side ambient particles to make otherwise plain biomes feel alive.
 * Particles are local visuals only and do not change blocks or world state.
 */
public class AmbientFX extends Module {
    private final Setting.Mode style = add(new Setting.Mode("Эффект",
            "Светлячки", "Магическая пыль", "Духи", "Искры"),
            "Неброские частицы вокруг игрока.");
    private final Setting.Num density = add(new Setting.Num("Плотность", 2, 1, 5, 1),
            "Количество частиц за интервал.");
    private final Setting.Num radius = add(new Setting.Num("Радиус", 7, 3, 14, 1),
            "Радиус появления частиц вокруг игрока.");
    private final Setting.Num interval = add(new Setting.Num("Интервал", 3, 1, 10, 1),
            "Чем больше число, тем реже появляются частицы.");
    private final Setting.Bool onlyMoving = add(new Setting.Bool("Только в движении", false),
            "Создавать эффект только когда игрок двигается.");
    private final Setting.Bool inFirstPerson = add(new Setting.Bool("От первого лица", true),
            "Показывать эффект только от первого лица.");

    private int tick;

    public AmbientFX() {
        super("AmbientFX", "Атмосферные частицы для красоты мира", Category.VISUAL);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (inFirstPerson.get() && !mc.options.getPerspective().isFirstPerson()) return;
        if (onlyMoving.get()) {
            double vx = mc.player.getVelocity().x;
            double vz = mc.player.getVelocity().z;
            if (vx * vx + vz * vz < 0.001) return;
        }

        tick++;
        if (tick % (int) interval.get() != 0) return;

        ParticleEffect particle = selectedParticle();
        Random random = mc.world.random;
        int count = (int) density.get();
        double r = radius.get();
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 1.0 + random.nextDouble() * r;
            double x = px + Math.cos(angle) * distance;
            double z = pz + Math.sin(angle) * distance;
            double y = py + 0.3 + random.nextDouble() * Math.max(1.0, mc.player.getHeight() + 1.5);

            double driftX = (random.nextDouble() - 0.5) * 0.012;
            double driftY = 0.003 + random.nextDouble() * 0.012;
            double driftZ = (random.nextDouble() - 0.5) * 0.012;
            mc.world.addParticle(particle, x, y, z, driftX, driftY, driftZ);
        }
    }

    private ParticleEffect selectedParticle() {
        if (style.is("Магическая пыль")) return ParticleTypes.ENCHANT;
        if (style.is("Духи")) return ParticleTypes.SOUL;
        if (style.is("Искры")) return ParticleTypes.CRIT;
        return ParticleTypes.END_ROD;
    }

    @Override
    public void onDisable() {
        tick = 0;
    }
}
