package ru.bogempty.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * Короткий W-tap после удара.
 *
 * Сохраняет состояние клавиши до сброса и восстанавливает фактическое
 * состояние физической клавиши, чтобы W не оставалась залипшей.
 */
public final class SprintResetter {
    private static int ticksLeft;
    private static boolean resetActive;

    private SprintResetter() {}

    /**
     * Временно отпускает движение вперёд. Вызывается после удара.
     * Значение ticks ограничено диапазоном 1..3.
     */
    public static void trigger(int ticks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options == null
                || mc.currentScreen != null || mc.getWindow() == null) {
            cancel();
            return;
        }

        KeyBinding forward = mc.options.forwardKey;
        resetActive = true;
        ticksLeft = Math.max(1, Math.min(3, ticks));
        forward.setPressed(false);
        mc.player.setSprinting(false);
    }

    /** Вызывается каждый клиентский тик. */
    public static void tick() {
        if (!resetActive) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options == null
                || mc.currentScreen != null || mc.getWindow() == null) {
            cancel();
            return;
        }

        if (--ticksLeft <= 0) {
            restore(mc);
        }
    }

    /** Безопасно завершает сброс, например при открытии меню или выходе из мира. */
    public static void cancel() {
        if (!resetActive) {
            ticksLeft = 0;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null && mc.getWindow() != null) {
            restore(mc);
        } else {
            resetActive = false;
            ticksLeft = 0;
        }
    }

    private static void restore(MinecraftClient mc) {
        try {
            KeyBinding forward = mc.options.forwardKey;
            int code = InputUtil.fromTranslationKey(
                    forward.getBoundKeyTranslationKey()).getCode();

            // Если окно не в фокусе, не считаем физическую клавишу удерживаемой.
            boolean physicallyPressed = mc.getWindow().isFocused()
                    && InputUtil.isKeyPressed(mc.getWindow().getHandle(), code);
            forward.setPressed(physicallyPressed);
        } catch (RuntimeException ignored) {
            // Если опрос клавиши временно недоступен, не оставляем W принудительно зажатой.
            mc.options.forwardKey.setPressed(false);
        } finally {
            resetActive = false;
            ticksLeft = 0;
        }
    }
}
