package ru.bogempty.client.module.visual;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

import java.util.concurrent.ThreadLocalRandom;

/** Красивые частицы вокруг тебя (видны и другим на экране записи, но только на твоём клиенте). */
public class FxParticles extends Module {
    private final Setting.Mode shape = add(new Setting.Mode("Форма", "Аура", "Спираль", "След"),
            "Аура: вращающееся кольцо. Спираль: два витка вверх. След: шлейф за ногами.");
    private final Setting.Mode type = add(new Setting.Mode("Частицы", "Звёзды", "Душа", "Магия", "Ведьма", "Салют", "Сердца"),
            "Из чего сделан эффект.");
    private final Setting.Num density = add(new Setting.Num("Плотность", 3, 1, 8, 1), "Сколько частиц за тик.");
    private final Setting.Num radius = add(new Setting.Num("Радиус", 0.9, 0.4, 1.8, 0.1), "Размер кольца и спирали.");
    private final Setting.Bool movingOnly = add(new Setting.Bool("Только в движении", false), "Для «След»: рисовать, только когда идёшь.");
    private final Setting.Bool hitBurst = add(new Setting.Bool("Вспышка при ударе", true), "Искры у цели, когда ты её бьёшь.");

    private int t = 0;
    private boolean prevSwing = false;

    public FxParticles() {
        super("Эффекты", "Частицы: аура, спираль, след и искры при ударе", Category.VISUAL);
    }

    private ParticleEffect eff() {
        if (type.is("Душа")) return ParticleTypes.SOUL_FIRE_FLAME;
        if (type.is("Магия")) return ParticleTypes.ENCHANT;
        if (type.is("Ведьма")) return ParticleTypes.WITCH;
        if (type.is("Салют")) return ParticleTypes.FIREWORK;
        if (type.is("Сердца")) return ParticleTypes.HEART;
        return ParticleTypes.END_ROD;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        t++;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        ParticleEffect e = eff();
        int n = (int) density.get();
        double rad = radius.get();
        double px = p.getX(), py = p.getY(), pz = p.getZ();

        if (shape.is("Аура")) {
            for (int i = 0; i < n; i++) {
                double a = t * 0.18 + i * (Math.PI * 2 / n);
                double yy = py + 0.15 + (Math.sin(t * 0.1 + i) * 0.5 + 0.5) * (p.getHeight() - 0.2);
                mc.world.addParticle(e, px + Math.cos(a) * rad, yy, pz + Math.sin(a) * rad, 0, 0.005, 0);
            }
        } else if (shape.is("Спираль")) {
            for (int s = 0; s < 2; s++) {
                for (int i = 0; i < Math.max(1, n / 2); i++) {
                    double k = ((t * 0.04 + i * 0.12) % 1.0);
                    double a = k * Math.PI * 4 + s * Math.PI;
                    mc.world.addParticle(e, px + Math.cos(a) * rad * 0.8, py + k * (p.getHeight() + 0.3), pz + Math.sin(a) * rad * 0.8, 0, 0, 0);
                }
            }
        } else {
            double vx = p.getVelocity().x, vz = p.getVelocity().z;
            if (movingOnly.get() && vx * vx + vz * vz < 0.0004) {
                // стоим
            } else {
                for (int i = 0; i < n; i++) {
                    mc.world.addParticle(e,
                            px + (r.nextDouble() - 0.5) * 0.5, py + 0.05 + r.nextDouble() * 0.3, pz + (r.nextDouble() - 0.5) * 0.5,
                            (r.nextDouble() - 0.5) * 0.02, 0.01 + r.nextDouble() * 0.02, (r.nextDouble() - 0.5) * 0.02);
                }
            }
        }

        // искры при ударе по цели в прицеле
        boolean swing = p.handSwinging;
        if (hitBurst.get() && swing && !prevSwing && mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult ehr) {
            net.minecraft.util.math.Vec3d h = ehr.getPos();
            for (int i = 0; i < 14; i++) {
                mc.world.addParticle(ParticleTypes.CRIT, h.x, h.y, h.z,
                        (r.nextDouble() - 0.5) * 0.6, r.nextDouble() * 0.5, (r.nextDouble() - 0.5) * 0.6);
            }
        }
        prevSwing = swing;
    }
}