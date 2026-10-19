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

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("bogempty.json");
    }

    public static void save(ModuleManager mm) {
        try {
            JsonObject root = new JsonObject();
            for (Module m : mm.modules) {
                JsonObject o = new JsonObject();
                o.addProperty("key", m.key);
                JsonObject s = new JsonObject();
                for (Setting st : m.settings) st.save(s);
                o.add("settings", s);
                root.add(m.name, o);
            }
            Files.writeString(file(), GSON.toJson(root));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void load(ModuleManager mm) {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (Module m : mm.modules) {
                if (!root.has(m.name)) continue;
                JsonObject o = root.getAsJsonObject(m.name);
                if (o.has("key")) m.key = o.get("key").getAsInt();
                if (o.has("settings")) {
                    JsonObject s = o.getAsJsonObject("settings");
                    for (Setting st : m.settings) {
                        try {
                            st.load(s);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}