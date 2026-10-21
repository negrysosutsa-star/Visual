package ru.bogempty.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import ru.bogempty.client.ai.AiBrain;
import ru.bogempty.client.alt.AltManager;
import ru.bogempty.client.friend.FriendManager;
import ru.bogempty.client.gui.AltManagerScreen;
import ru.bogempty.client.gui.BogLoadingOverlay;
import ru.bogempty.client.util.SprintResetter;

import java.util.Locale;

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
            if (screen instanceof TitleScreen || screen instanceof MultiplayerScreen) {
                Screens.getButtons(screen).add(
                        ButtonWidget.builder(Text.literal("Альт-менеджер"),
                                        b -> client.setScreen(new AltManagerScreen(screen)))
                                .dimensions(6, 6, 100, 20)
                                .build());
            }
        });

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String msg = message.trim();
            if (!msg.toLowerCase(Locale.ROOT).startsWith(".friend")) return true;

            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null) return false;

            String[] parts = msg.split("\\s+", 3);
            String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";

            switch (sub) {
                case "add" -> {
                    if (parts.length < 3) {
                        mc.player.sendMessage(Text.literal("§d[BogEmpty] §f.friend add <ник>"), false);
                    } else {
                        String nick = parts[2].replace("\"", "").trim();
                        if (FriendManager.add(nick)) {
                            mc.player.sendMessage(Text.literal("§d[BogEmpty] §aДобавил в друзья: §f" + nick), false);
                        } else {
                            mc.player.sendMessage(Text.literal("§d[BogEmpty] §7Уже в друзьях: §f" + nick), false);
                        }
                    }
                }
                case "remove", "del" -> {
                    if (parts.length < 3) {
                        mc.player.sendMessage(Text.literal("§d[BogEmpty] §f.friend remove <ник>"), false);
                    } else {
                        String nick = parts[2].replace("\"", "").trim();
                        if (FriendManager.remove(nick)) {
                            mc.player.sendMessage(Text.literal("§d[BogEmpty] §cУдалил из друзей: §f" + nick), false);
                        } else {
                            mc.player.sendMessage(Text.literal("§d[BogEmpty] §7Не был в друзьях: §f" + nick), false);
                        }
                    }
                }
                case "clear" -> {
                    FriendManager.clear();
                    mc.player.sendMessage(Text.literal("§d[BogEmpty] §cУдалил всех друзей"), false);
                }
                case "list" -> {
                    if (FriendManager.all().isEmpty()) {
                        mc.player.sendMessage(Text.literal("§d[BogEmpty] §7Список друзей пуст"), false);
                    } else {
                        mc.player.sendMessage(Text.literal("§d[BogEmpty] §fДрузья: §a" + String.join("§7, §a", FriendManager.all())), false);
                    }
                }
                default -> mc.player.sendMessage(Text.literal("§d[BogEmpty] §f.friend add/remove/clear/list"), false);
            }
            return false;
        });
    }

    private ClientExtras() {}
}