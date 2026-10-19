package ru.bogempty.client.module;

import net.minecraft.client.gui.DrawContext;
import ru.bogempty.client.module.hud.ArrayListModule;
import ru.bogempty.client.module.hud.InfoHud;
import ru.bogempty.client.module.hud.Watermark;
import ru.bogempty.client.module.movement.AutoSprint;
import ru.bogempty.client.module.movement.ShiftTap;
import ru.bogempty.client.module.visual.CustomCrosshair;
import ru.bogempty.client.module.visual.FullBright;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    public final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        // Movement
        modules.add(new AutoSprint());
        modules.add(new ShiftTap());

        // Visual
        modules.add(new FullBright());
        modules.add(new CustomCrosshair());

        // HUD
        modules.add(new Watermark());
        modules.add(new InfoHud());
        modules.add(new ArrayListModule());
    }

    public void onTick() {
        for (Module m : modules) {
            if (m.isEnabled()) m.onTick();
        }
    }

    public void onRender2D(DrawContext context, float tickDelta) {
        for (Module m : modules) {
            if (m.isEnabled()) m.onRender2D(context, tickDelta);
        }
    }

    public List<Module> getModulesByCategory(Category cat) {
        List<Module> list = new ArrayList<>();
        for (Module m : modules) {
            if (m.category == cat) list.add(m);
        }
        return list;
    }
}