package ru.bogempty.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import ru.bogempty.client.ai.AiBrain;
import ru.bogempty.client.BogEmptyClient;
import ru.bogempty.client.config.ConfigManager;
import ru.bogempty.client.alt.AltManager;
import ru.bogempty.client.friend.FriendManager;
import ru.bogempty.client.gps.GpsManager;
import ru.bogempty.client.gui.AltManagerScreen;
import ru.bogempty.client.gui.BogLoadingOverlay;
import ru.bogempty.client.gui.BogTitleScreen;
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

            // редирект title → наше меню
            if (client.currentScreen instanceof TitleScreen && !(client.currentScreen instanceof BogTitleScreen)) {
                client.setScreen(new BogTitleScreen());
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            AiBrain.get().save();
            AltManager.save();
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof MultiplayerScreen) {
                Screens.getButtons(screen).add(
                        ButtonWidget.builder(Text.literal("Альт-менеджер"),
                                        b -> client.setScreen(new AltManagerScreen(screen)))
                                .dimensions(6, 6, 100, 20)
                                .build());
            }
        });

        // исходящий чат: .friend / .gps
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String msg = message.trim();
            String low = msg.toLowerCase(Locale.ROOT);
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null) return true;

            if (low.startsWith(".friend")) {
                handleFriend(mc, msg);
                return false;
            }
            if (low.equals(".cfg") || low.startsWith(".cfg ")) {
                handleConfig(mc, msg);
                return false;
            }
            if (low.startsWith(".gps")) {
                handleGps(mc, msg);
                return false;
            }
            return true;
        });

        // входящий чат: парсим координаты после /event delay
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            GpsManager.onChat(message.getString());
        });
    }

    private static void handleConfig(net.minecraft.client.MinecraftClient mc, String msg) {
        String[] parts = msg.trim().split("\\s+", 3);
        String action = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "list";
        String name = parts.length > 2 ? parts[2].trim() : "";
        if (name.endsWith(".cfg")) name = name.substring(0, name.length() - 4);
        switch (action) {
            case "save" -> {
                if (name.isBlank()) mc.player.sendMessage(Text.literal("§d[Visual] §f.cfg save <имя>"), false);
                else if (ConfigManager.saveNamed(BogEmptyClient.moduleManager, name)) mc.player.sendMessage(Text.literal("§d[Visual] §aСохранено: §f" + name + ".cfg"), false);
                else mc.player.sendMessage(Text.literal("§d[Visual] §cОшибка сохранения. Используй a-z, 0-9, _ или -"), false);
            }
            case "load" -> {
                if (name.isBlank()) mc.player.sendMessage(Text.literal("§d[Visual] §f.cfg load <имя>"), false);
                else if (ConfigManager.loadNamed(BogEmptyClient.moduleManager, name)) mc.player.sendMessage(Text.literal("§d[Visual] §aЗагружено: §f" + name + ".cfg"), false);
                else mc.player.sendMessage(Text.literal("§d[Visual] §cКонфиг не найден или не удалось загрузить"), false);
            }
            case "delete", "del" -> {
                if (name.isBlank()) mc.player.sendMessage(Text.literal("§d[Visual] §f.cfg delete <имя>"), false);
                else if (ConfigManager.deleteNamed(name)) mc.player.sendMessage(Text.literal("§d[Visual] §cУдалён: §f" + name + ".cfg"), false);
                else mc.player.sendMessage(Text.literal("§d[Visual] §cКонфиг не найден"), false);
            }
            case "list" -> {
                var names = ConfigManager.listNamed();
                mc.player.sendMessage(Text.literal(names.isEmpty() ? "§d[Visual] §7Конфигов нет" : "§d[Visual] §fКонфиги: §a" + String.join("§7, §a", names)), false);
            }
            default -> mc.player.sendMessage(Text.literal("§d[Visual] §f.cfg save/load/delete/list <имя>"), false);
        }
    }

    private static void handleFriend(net.minecraft.client.MinecraftClient mc, String msg) {
        String[] parts = msg.split("\\s+", 3);
        String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "add" -> {
                if (parts.length < 3) {
                    mc.player.sendMessage(Text.literal("§d[BogEmpty] §f.friend add <ник>"), false);
                } else {
                    String nick = parts[2].replace("\"", "").trim();
                    if (FriendManager.add(nick))
                        mc.player.sendMessage(Text.literal("§d[BogEmpty] §aДобавил в друзья: §f" + nick), false);
                    else
                        mc.player.sendMessage(Text.literal("§d[BogEmpty] §7Уже в друзьях: §f" + nick), false);
                }
            }
            case "remove", "del" -> {
                if (parts.length < 3) {
                    mc.player.sendMessage(Text.literal("§d[BogEmpty] §f.friend remove <ник>"), false);
                } else {
                    String nick = parts[2].replace("\"", "").trim();
                    if (FriendManager.remove(nick))
                        mc.player.sendMessage(Text.literal("§d[BogEmpty] §cУдалил из друзей: §f" + nick), false);
                    else
                        mc.player.sendMessage(Text.literal("§d[BogEmpty] §7Не был в друзьях: §f" + nick), false);
                }
            }
            case "clear" -> {
                FriendManager.clear();
                mc.player.sendMessage(Text.literal("§d[BogEmpty] §cУдалил всех друзей"), false);
            }
            case "list" -> {
                if (FriendManager.all().isEmpty())
                    mc.player.sendMessage(Text.literal("§d[BogEmpty] §7Список друзей пуст"), false);
                else
                    mc.player.sendMessage(Text.literal("§d[BogEmpty] §fДрузья: §a" + String.join("§7, §a", FriendManager.all())), false);
            }
            default -> mc.player.sendMessage(Text.literal("§d[BogEmpty] §f.friend add/remove/clear/list"), false);
        }
    }

    private static void handleGps(net.minecraft.client.MinecraftClient mc, String msg) {
        String[] parts = msg.trim().split("\\s+");
        if (parts.length < 2) {
            mc.player.sendMessage(Text.literal("§d[BogEmpty] §f.gps event | .gps post | .gps <x> <y> <z> [имя] | .gps clear"), false);
            return;
        }
        String sub = parts[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "event" -> {
                GpsManager.requestEvent();
                mc.player.sendMessage(Text.literal("§d[BogEmpty] §fЗапросил §e/event delay§f, жду координаты…"), false);
            }
            case "post", "here" -> {
                var p = mc.player.getPos();
                GpsManager.set("Post", p.x, p.y, p.z);
                mc.player.sendMessage(Text.literal("§d[BogEmpty] §aТочка Post: §f"
                        + (int) p.x + " " + (int) p.y + " " + (int) p.z), false);
            }
            case "clear", "off" -> {
                GpsManager.clear();
                mc.player.sendMessage(Text.literal("§d[BogEmpty] §cGPS очищен"), false);
            }
            default -> {
                // .gps x y z [name...]
                if (parts.length >= 4) {
                    try {
                        double x = Double.parseDouble(parts[1]);
                        double y = Double.parseDouble(parts[2]);
                        double z = Double.parseDouble(parts[3]);
                        String name = parts.length > 4
                                ? msg.substring(msg.toLowerCase(Locale.ROOT).indexOf(parts[3]) + parts[3].length()).trim().replace("\"", "")
                                : "Точка";
                        if (name.isBlank()) name = "Точка";
                        GpsManager.set(name, x, y, z);
                        mc.player.sendMessage(Text.literal("§d[BogEmpty] §aGPS §f" + name + " §7→ §f"
                                + (int) x + " " + (int) y + " " + (int) z), false);
                    } catch (NumberFormatException e) {
                        mc.player.sendMessage(Text.literal("§d[BogEmpty] §cКоординаты: числа"), false);
                    }
                } else {
                    mc.player.sendMessage(Text.literal("§d[BogEmpty] §f.gps event | .gps post | .gps <x> <y> <z> [имя] | .gps clear"), false);
                }
            }
        }
    }

    private ClientExtras() {}
}