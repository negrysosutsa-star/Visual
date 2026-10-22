package ru.bogempty.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import ru.bogempty.client.setting.Setting;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String name;
    public final String description;
    public final Category category;
    public boolean enabled;
    public int key = -1;
    public boolean keyDown;
    /** true: клавиша модуля не включает его, а вызывает onKey() (например, свап предметов). */
    public boolean keyAction = false;
    public float anim;
    public final List<Setting> settings = new ArrayList<>();

    protected final MinecraftClient mc = MinecraftClient.getInstance();

    public Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <T extends Setting> T add(T setting) {
        settings.add(setting);
        return setting;
    }

    protected <T extends Setting> T add(T setting, String desc) {
        setting.desc = desc;
        settings.add(setting);
        return setting;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        if (enabled) onEnable();
        else onDisable();
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
    public void onKey() {}
    public void onRender2D(DrawContext context, float tickDelta) {}

    public boolean isEnabled() {
        return enabled;
    }
}