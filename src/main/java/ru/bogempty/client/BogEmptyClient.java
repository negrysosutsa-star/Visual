package ru.bogempty.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.bogempty.client.config.ConfigManager;
import ru.bogempty.client.gui.ClickGui;
import ru.bogempty.client.module.ModuleManager;

public class BogEmptyClient implements ClientModInitializer {

    public static BogEmptyClient INSTANCE;
    public static ModuleManager moduleManager;
    public static ClickGui clickGui;
    public static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        moduleManager = new ModuleManager();
        ConfigManager.load(moduleManager);
        clickGui = new ClickGui();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bogempty.opengui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.bogempty.client"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(clickGui);
            }
            moduleManager.handleKeys(client);
            moduleManager.onTick();
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            moduleManager.onRender2D(context, tickCounter.getTickDelta(true));
        });
    }
}