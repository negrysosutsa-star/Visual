package ru.bogempty.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Сброс спринта (W-tap): на пару тиков отпускает «вперёд», потом возвращает как было. */
public final class SprintResetter {
    private static int hold = 0;

    /** Вызывается сразу после удара. ticks — сколько тиков держать «вперёд» отпущенным. */
    public static void trigger(int ticks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;
        mc.player.setSprinting(false);
        mc.options.forwardKey.setPressed(false);
        hold = Math.max(1, ticks);
    }

    /** Вызывается каждый тик (регистрируется в ClientExtras). */
    public static void tick() {
        if (hold <= 0) return;
        hold--;
        if (hold == 0) restore();
    }

    private static void restore() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null || mc.getWindow() == null) return;
        try {
            KeyBinding kb = mc.options.forwardKey;
            int code = InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey()).getCode();
            kb.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), code));
        } catch (Throwable t) {
            mc.options.forwardKey.setPressed(false);
        }
    }

    private SprintResetter() {}
}