package ru.bogempty.client.module.movement;

import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;

public class AutoSprint extends Module {
    public AutoSprint() {
        super("AutoSprint", "Always sprint when moving forward", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player != null && mc.player.input != null) {
            if (mc.player.input.movementForward > 0 && !mc.player.isSneaking() && !mc.player.isUsingItem()) {
                mc.player.setSprinting(true);
            }
        }
    }
}