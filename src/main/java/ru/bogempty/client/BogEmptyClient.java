package ru.bogempty.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.bogempty.client.gui.ClickGui;
import ru.bogempty.client.module.ModuleManager;
import ru.bogempty.client.render.RenderUtils;

public class BogEmptyClient implements ClientModInitializer {

    public static BogEmptyClient INSTANCE;
    public static ModuleManager moduleManager;
    public static ClickGui clickGui;

    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        moduleManager = new ModuleManager();
        clickGui = new ClickGui();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bogempty.opengui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.bogempty.client"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(clickGui);
                } else if (client.currentScreen instanceof ClickGui) {
                    client.setScreen(null);
                }
            }

            moduleManager.onTick();
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            moduleManager.onRender2D(context, tickDelta);
        });
    }
}