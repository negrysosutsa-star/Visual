package ru.bogempty.client.module.movement;

import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;

public class ShiftTap extends Module {
    public ShiftTap() {
        super("ShiftTap", "Better sneak control", Category.MOVEMENT);
    }

    private boolean wasPressed = false;

    @Override
    public void onTick() {
        if (mc.player == null || mc.options == null) return;

        boolean pressed = mc.options.sneakKey.isPressed();
        wasPressed = pressed;
        // Пока базовая версия. Позже можно добавить double-tap логику.
    }
}