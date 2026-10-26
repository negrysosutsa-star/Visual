package ru.bogempty.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.module.ModuleManager;
import ru.bogempty.client.setting.Setting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve("visual").resolve("configs");
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("bogempty.json");
    }

    public static void save(ModuleManager mm) {
        write(mm, file());
    }

    public static void load(ModuleManager mm) {
        read(mm, file());
    }

    public static boolean saveNamed(ModuleManager mm, String name) {
        Path path = namedFile(name);
        if (path == null) return false;
        return write(mm, path);
    }

    public static boolean loadNamed(ModuleManager mm, String name) {
        Path path = namedFile(name);
        if (path == null || !Files.isRegularFile(path)) return false;
        return read(mm, path);
    }

    public static boolean deleteNamed(String name) {
        Path path = namedFile(name);
        if (path == null) return false;
        try {
            return Files.deleteIfExists(path);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<String> listNamed() {
        List<String> names = new ArrayList<>();
        try {
            Path dir = directory();
            if (!Files.isDirectory(dir)) return names;
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".cfg"))
                        .map(p -> p.getFileName().toString())
                        .map(n -> n.substring(0, n.length() - 4))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(names::add);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return names;
    }

    private static Path namedFile(String name) {
        if (name == null) return null;
        String safe = name.trim();
        if (safe.endsWith(".cfg")) safe = safe.substring(0, safe.length() - 4);
        if (safe.isBlank() || safe.length() > 32 || !safe.matches("[a-zA-Z0-9_-]+")) return null;
        try {
            Files.createDirectories(directory());
            return directory().resolve(safe + ".cfg");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean write(ModuleManager mm, Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            JsonObject root = new JsonObject();
            for (Module m : mm.modules) {
                JsonObject o = new JsonObject();
                o.addProperty("enabled", m.isEnabled());
                o.addProperty("key", m.key);
                JsonObject settings = new JsonObject();
                for (Setting st : m.settings) st.save(settings);
                o.add("settings", settings);
                root.add(m.name, o);
            }
            Files.writeString(path, GSON.toJson(root));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean read(ModuleManager mm, Path path) {
        try {
            if (!Files.isRegularFile(path)) return false;
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (Module m : mm.modules) {
                if (!root.has(m.name) || !root.get(m.name).isJsonObject()) continue;
                JsonObject o = root.getAsJsonObject(m.name);
                if (o.has("key")) m.key = o.get("key").getAsInt();
                if (o.has("settings") && o.get("settings").isJsonObject()) {
                    JsonObject settings = o.getAsJsonObject("settings");
                    for (Setting st : m.settings) {
                        try {
                            st.load(settings);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
                // Backward compatibility: old bogempty.json files have no enabled field.
                if (o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
