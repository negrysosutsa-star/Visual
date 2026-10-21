package ru.bogempty.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import ru.bogempty.client.ai.AiBrain;
import ru.bogempty.client.alt.AltManager;
import ru.bogempty.client.gui.AltManagerScreen;

public final class ClientExtras {
    private static boolean done = false;

    public static void init() {
        if (done) return;
        done = true;

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            AiBrain.get().save();
            AltManager.save();
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen || screen instanceof MultiplayerScreen) {
                Screens.getButtons(screen).add(
                        ButtonWidget.builder(Text.literal("Альт-менеджер"),
                                        b -> client.setScreen(new AltManagerScreen(screen)))
                                .dimensions(6, 6, 100, 20)
                                .build());
            }
        });
    }

    private ClientExtras() {}
}