package ru.bogempty.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.class_2561;
import net.minecraft.class_4185;
import net.minecraft.class_442;
import net.minecraft.class_500;
import ru.bogempty.client.ai.AiBrain;
import ru.bogempty.client.alt.AltManager;
import ru.bogempty.client.gui.AltManagerScreen;
import ru.bogempty.client.gui.BogLoadingOverlay;
import ru.bogempty.client.util.SprintResetter;

public final class ClientExtras {
    private static boolean done = false;

    public static void init() {
        if (done) return;
        done = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SprintResetter.tick();
            BogLoadingOverlay.tryInstall(client);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            AiBrain.get().save();
            AltManager.save();
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof class_442 || screen instanceof class_500) {
                Screens.getButtons(screen).add(
                        class_4185.method_46430(class_2561.method_43470("Альт-менеджер"),
                                        b -> client.method_1507(new AltManagerScreen(screen)))
                                .method_46434(6, 6, 100, 20)
                                .method_46431());
            }
        });
    }

    private ClientExtras() {}
}