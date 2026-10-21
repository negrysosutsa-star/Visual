package ru.bogempty.client.module.combat;

import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.class_1657;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_746;
import org.lwjgl.glfw.GLFW;

/**
 * Быстрый свап предметов.
 * Режимы: сфера↔сфера, тотем↔тотем, сфера↔тотем.
 * Silent + небольшая задержка под FunTime.
 */
public class AutoSwap extends Module {

    private final Setting.Mode mode = add(new Setting.Mode("Mode",
            "Crystal to Crystal", "Totem to Totem", "Crystal to Totem"),
            "What to swap between mainhand and offhand / inventory.");
    private final Setting.Bool bindSwap = add(new Setting.Bool("On key", true),
            "Swap only when the module key is pressed (toggle still enables the module).");
    private final Setting.Num delay = add(new Setting.Num("Delay", 1, 0, 6, 1),
            "Ticks between clicks.");
    private final Setting.Bool silent = add(new Setting.Bool("Silent", true),
            "Do not open inventory.");
    private final Setting.Bool preferHotbar = add(new Setting.Bool("Prefer hotbar", true),
            "Search hotbar first.");

    private int cooldown = 0;
    private boolean keyWasDown = false;

    public AutoSwap() {
        super("AutoSwap", "Swaps crystals and totems according to mode", Category.COMBAT);
        key = GLFW.GLFW_KEY_X;
    }

    @Override
    public void onTick() {
        class_746 p = mc.field_1724;
        if (p == null || mc.field_1761 == null || mc.field_1755 != null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        boolean pressed = key != -1 && GLFW.glfwGetKey(
                mc.method_22683().method_4490(), key) == GLFW.GLFW_PRESS;

        if (bindSwap.get()) {
            if (!pressed || keyWasDown) {
                keyWasDown = pressed;
                return;
            }
            keyWasDown = true;
        } else {
            if (!shouldAuto(p)) return;
        }

        doSwap(p);
        cooldown = (int) delay.get() + ThreadLocalRandom.current().nextInt(0, 2);
    }

    private boolean shouldAuto(class_746 p) {
        class_1799 main = p.method_6047();
        class_1799 off = p.method_6079();
        return switch (mode.get()) {
            case "Crystal to Crystal" -> !main.method_31574(class_1802.field_8301)
                    && has(p, class_1802.field_8301);
            case "Totem to Totem" -> !off.method_31574(class_1802.field_8288)
                    && has(p, class_1802.field_8288);
            case "Crystal to Totem" -> main.method_31574(class_1802.field_8301)
                    && has(p, class_1802.field_8288);
            default -> false;
        };
    }

    private void doSwap(class_746 p) {
        switch (mode.get()) {
            case "Crystal to Crystal" -> swapItem(p, class_1802.field_8301, false);
            case "Totem to Totem" -> swapItem(p, class_1802.field_8288, true);
            case "Crystal to Totem" -> {
                if (!p.method_6079().method_31574(class_1802.field_8288)) {
                    int t = find(p, class_1802.field_8288);
                    if (t != -1) clickSwap(t, 45);
                }
            }
            default -> {}
        }
    }

    private void swapItem(class_746 p, class_1792 item, boolean toOffhand) {
        int targetSlot = toOffhand ? 45 : p.method_31548().field_7545 + 36;
        class_1799 cur = toOffhand ? p.method_6079() : p.method_6047();
        if (cur.method_31574(item)) return;
        int from = find(p, item);
        if (from == -1) return;
        clickSwap(from, targetSlot);
    }

    private int find(class_1657 p, class_1792 item) {
        int start = preferHotbar.get() ? 0 : 9;
        int end = preferHotbar.get() ? 9 : 36;
        for (int i = start; i < end; i++) {
            if (p.method_31548().method_5438(i).method_31574(item))
                return i < 9 ? i + 36 : i;
        }
        for (int i = 0; i < 36; i++) {
            if (p.method_31548().method_5438(i).method_31574(item))
                return i < 9 ? i + 36 : i;
        }
        return -1;
    }

    private boolean has(class_1657 p, class_1792 item) {
        return find(p, item) != -1;
    }

    private void clickSwap(int from, int to) {
        if (mc.field_1761 == null || mc.field_1724 == null) return;
        int sync = mc.field_1724.field_7512.field_7763;
        mc.field_1761.method_2906(sync, from, 0, class_1713.field_7790, mc.field_1724);
        mc.field_1761.method_2906(sync, to, 0, class_1713.field_7790, mc.field_1724);
        if (!mc.field_1724.field_7512.method_34255().method_7960()) {
            mc.field_1761.method_2906(sync, from, 0, class_1713.field_7790, mc.field_1724);
        }
    }
}