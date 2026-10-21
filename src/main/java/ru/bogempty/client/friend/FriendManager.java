package ru.bogempty.client.friend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class FriendManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("bogempty_friends.json");
    private static final Set<String> friends = new HashSet<>();

    static {
        load();
    }

    public static boolean isFriend(String name) {
        if (name == null) return false;
        return friends.contains(name.toLowerCase(Locale.ROOT));
    }

    public static boolean add(String name) {
        if (name == null || name.isBlank()) return false;
        boolean ok = friends.add(name.toLowerCase(Locale.ROOT));
        if (ok) save();
        return ok;
    }

    public static boolean remove(String name) {
        if (name == null) return false;
        boolean ok = friends.remove(name.toLowerCase(Locale.ROOT));
        if (ok) save();
        return ok;
    }

    public static void clear() {
        friends.clear();
        save();
    }

    public static Set<String> all() {
        return Collections.unmodifiableSet(friends);
    }

    private static void load() {
        try {
            if (!Files.exists(FILE)) return;
            Set<String> s = GSON.fromJson(Files.readString(FILE), new TypeToken<Set<String>>(){}.getType());
            if (s != null) {
                friends.clear();
                for (String n : s) friends.add(n.toLowerCase(Locale.ROOT));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(friends));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private FriendManager() {}
}