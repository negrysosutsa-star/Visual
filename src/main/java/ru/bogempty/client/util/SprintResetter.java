package ru.bogempty.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/**
 * Короткий локальный сброс движения вперёд после игрового события.
 * Восстанавливает состояние клавиш штатным механизмом Minecraft.
 */
public final class SprintResetter {
    private static int ticksLeft;
    private static boolean resetActive;

    private SprintResetter() {}

    public static void trigger(int ticks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!canRun(mc)) {
            cancel();
            return;
        }

        ticksLeft = Math.max(1, Math.min(3, ticks));
        resetActive = true;
        mc.options.forwardKey.setPressed(false);
        mc.player.setSprinting(false);
    }

    /** Вызывается каждый клиентский тик. */
    public static void tick() {
        if (!resetActive) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (!canRun(mc)) {
            cancel();
            return;
        }

        if (--ticksLeft <= 0) {
            restore(mc);
        }
    }

    /** Завершает сброс при открытии меню или смене мира. */
    public static void cancel() {
        if (!resetActive) {
            ticksLeft = 0;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            restore(mc);
        } else {
            resetActive = false;
            ticksLeft = 0;
        }
    }

    private static boolean canRun(MinecraftClient mc) {
        return mc.player != null && mc.world != null && mc.options != null
                && mc.currentScreen == null;
    }

    private static void restore(MinecraftClient mc) {
        try {
            KeyBinding.updatePressedStates();
        } finally {
            resetActive = false;
            ticksLeft = 0;
        }
    }
}
