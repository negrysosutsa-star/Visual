package ru.bogempty.client.module.visual;

import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

public class Zoom extends Module {
    private final Setting.Num fov = add(new Setting.Num("FOV", 30, 30, 70, 1));
    private int previous = 70;

    public Zoom() {
        super("Zoom", "Narrow field of view", Category.VISUAL);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) previous = mc.options.getFov().getValue();
    }

    @Override
    public void onTick() {
        if (mc.options != null) mc.options.getFov().setValue((int) fov.get());
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.getFov().setValue(previous);
    }
}