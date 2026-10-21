package ru.bogempty.client.module.combat;

import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.class_1657;
import net.minecraft.class_1713;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_746;

/**
 * Автототем с легит-задержкой и silent-кликами.
 * FunTime смотрит на слишком быстрые/предсказуемые свапы и на открытие инвентаря.
 * Здесь: порог HP, случайная задержка, работа только через slot-click, без GUI.
 */
public class AutoTotem extends Module {

    private final Setting.Num health = add(new Setting.Num("Health", 12.0, 1.0, 20.0, 0.5),
            "Swap when health is at or below this value.");
    private final Setting.Num delayMin = add(new Setting.Num("Delay min", 1, 0, 8, 1),
            "Minimum ticks before swap.");
    private final Setting.Num delayMax = add(new Setting.Num("Delay max", 3, 0, 12, 1),
            "Maximum ticks before swap.");
    private final Setting.Bool offhandOnly = add(new Setting.Bool("Offhand only", true),
            "Put totem only into offhand.");
    private final Setting.Bool silent = add(new Setting.Bool("Silent", true),
            "Do not open inventory screen.");
    private final Setting.Bool predict = add(new Setting.Bool("Predict pop", true),
            "Also swap after recent hard damage.");
    private final Setting.Mode mode = add(new Setting.Mode("Mode", "Legit", "Strict"),
            "Legit: random delay. Strict: minimal delay for hard situations.");

    private int waitTicks = 0;
    private float lastHp = 20f;
    private long lastDamageMs = 0L;

    public AutoTotem() {
        super("AutoTotem", "Puts a totem into offhand when low on health", Category.COMBAT);
    }

    @Override
    public void onTick() {
        class_746 p = mc.field_1724;
        if (p == null || mc.field_1761 == null || mc.field_1755 != null) return;

        float hp = p.method_6032() + p.method_6067();
        if (hp < lastHp - 2.0f) lastDamageMs = System.currentTimeMillis();
        lastHp = hp;

        class_1799 off = p.method_6079();
        if (off.method_31574(class_1802.field_8288)) {
            waitTicks = 0;
            return;
        }

        boolean need = hp <= health.get()
                || (predict.get() && System.currentTimeMillis() - lastDamageMs < 350);

        if (!need) {
            waitTicks = 0;
            return;
        }

        int slot = findTotemSlot(p);
        if (slot == -1) return;

        if (waitTicks <= 0) {
            int min = (int) delayMin.get();
            int max = Math.max(min, (int) delayMax.get());
            if (mode.is("Strict")) {
                waitTicks = min;
            } else {
                waitTicks = ThreadLocalRandom.current().nextInt(min, max + 1);
            }
            return;
        }

        waitTicks--;
        if (waitTicks > 0) return;

        swapToOffhand(slot);
    }

    private int findTotemSlot(class_1657 p) {
        for (int i = 0; i < 36; i++) {
            class_1799 s = p.method_31548().method_5438(i);
            if (s.method_31574(class_1802.field_8288)) return i < 9 ? i + 36 : i;
        }
        return -1;
    }

    private void swapToOffhand(int invSlot) {
        if (mc.field_1761 == null || mc.field_1724 == null) return;
        int sync = mc.field_1724.field_7512.field_7763;

        mc.field_1761.method_2906(sync, invSlot, 0, class_1713.field_7790, mc.field_1724);
        mc.field_1761.method_2906(sync, 45, 0, class_1713.field_7790, mc.field_1724);

        if (!mc.field_1724.field_7512.method_34255().method_7960()) {
            mc.field_1761.method_2906(sync, invSlot, 0, class_1713.field_7790, mc.field_1724);
        }
    }
}