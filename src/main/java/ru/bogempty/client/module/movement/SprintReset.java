package ru.bogempty.client.module.movement;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;
import ru.bogempty.client.util.SprintResetter;

public class SprintReset extends Module {
    private final Setting.Num ticks = add(new Setting.Num("Пауза, тики", 1, 1, 3, 1),
            "На сколько тиков отпустить «вперёд» после удара.");
    private final Setting.Bool onlyTarget = add(new Setting.Bool("Только по цели", true),
            "Сбрасывать спринт, только если в прицеле живое существо.");

    private boolean prev = false;

    public SprintReset() {
        super("СбросСпринта", "Сбрасывает спринт после твоего удара (W-tap)", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null) {
            prev = false;
            return;
        }
        boolean pressed = mc.options.attackKey.isPressed();
        boolean click = pressed && !prev;
        prev = pressed;
        if (!click) return;

        if (onlyTarget.get()) {
            if (!(mc.crosshairTarget instanceof EntityHitResult ehr)) return;
            if (!(ehr.getEntity() instanceof LivingEntity)) return;
        }
        SprintResetter.trigger((int) ticks.get());
    }
}