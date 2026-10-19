package ru.bogempty.client.module.visual;

import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;

public class FullBright extends Module {
    public FullBright() {
        super("FullBright", "Maximum brightness", Category.VISUAL);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) {
            mc.options.getGamma().setValue(16.0);
        }
    }

    @Override
    public void onDisable() {
        if (mc.options != null) {
            mc.options.getGamma().setValue(1.0);
        }
    }
}