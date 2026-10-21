package ru.bogempty.client.module;

import ru.bogempty.client.ClientExtras;
import ru.bogempty.client.module.ai.AiTriggerBot;
import ru.bogempty.client.module.combat.AutoSwap;
import ru.bogempty.client.module.combat.AutoTotem;
import ru.bogempty.client.module.combat.TriggerBot;
import ru.bogempty.client.module.hud.ArmorHud;
import ru.bogempty.client.module.hud.ArrayListModule;
import ru.bogempty.client.module.hud.InfoHud;
import ru.bogempty.client.module.hud.Keystrokes;
import ru.bogempty.client.module.hud.TargetHud;
import ru.bogempty.client.module.hud.Watermark;
import ru.bogempty.client.module.movement.AutoSprint;
import ru.bogempty.client.module.movement.ShiftTap;
import ru.bogempty.client.module.movement.SprintReset;
import ru.bogempty.client.module.visual.CustomCrosshair;
import ru.bogempty.client.module.visual.CustomSky;
import ru.bogempty.client.module.visual.FullBright;
import ru.bogempty.client.module.visual.NoRender;
import ru.bogempty.client.module.visual.Zoom;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;

public class ModuleManager {
    public final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        // Combat
        modules.add(new TriggerBot());
        modules.add(new AutoTotem());
        modules.add(new AutoSwap());

        // Movement
        modules.add(new AutoSprint());
        modules.add(new ShiftTap());
        modules.add(new SprintReset());

        // Visual
        modules.add(new FullBright());
        modules.add(new Zoom());
        modules.add(new CustomSky());
        modules.add(new NoRender());
        modules.add(new CustomCrosshair());

        // HUD
        modules.add(new Watermark());
        modules.add(new InfoHud());
        modules.add(new Keystrokes());
        modules.add(new TargetHud());
        modules.add(new ArmorHud());
        modules.add(new ArrayListModule());

        // Ai
        modules.add(new AiTriggerBot());

        ClientExtras.init();
    }

    public void onTick() {
        for (Module m : modules) {
            if (m.isEnabled()) m.onTick();
        }
    }

    public void handleKeys(MinecraftClient mc) {
        if (mc.currentScreen != null) return;
        long handle = mc.getWindow().getHandle();
        for (Module m : modules) {
            if (m.key == -1) continue;
            boolean pressed = InputUtil.isKeyPressed(handle, m.key);
            if (pressed && !m.keyDown) m.toggle();
            m.keyDown = pressed;
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