package ru.bogempty.client.alt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.util.Uuids;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Список офлайн-аккаунтов (ники) и смена текущей сессии. */
public final class AltManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final List<String> alts = new ArrayList<>();
    private static boolean loaded = false;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("bogempty_alts.json");
    }

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            if (o.has("alts")) {
                for (JsonElement el : o.getAsJsonArray("alts")) alts.add(el.getAsString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            JsonArray a = new JsonArray();
            for (String s : alts) a.add(s);
            o.add("alts", a);
            Files.writeString(file(), GSON.toJson(o));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String current() {
        try {
            return MinecraftClient.getInstance().getSession().getUsername();
        } catch (Throwable t) {
            return "?";
        }
    }

    private static Field findField(Class<?> owner, Class<?> type) {
        for (Field f : owner.getDeclaredFields()) {
            if (f.getType() == type) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    /** Заходит под офлайн-ником. Работает на серверах без проверки лицензии. */
    public static boolean login(String name) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            UUID uuid = Uuids.getOfflinePlayerUuid(name);
            Session s = new Session(name, uuid, "", Optional.empty(), Optional.empty(), Session.AccountType.MOJANG);

            Field sf = findField(MinecraftClient.class, Session.class);
            if (sf == null) return false;
            sf.set(mc, s);

            Field gf = findField(MinecraftClient.class, GameProfile.class);
            if (gf != null) {
                try {
                    gf.set(mc, new GameProfile(uuid, name));
                } catch (Throwable ignored) {}
            }
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    private AltManager() {}
}