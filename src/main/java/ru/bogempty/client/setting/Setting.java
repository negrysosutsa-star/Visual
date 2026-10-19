package ru.bogempty.client.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

public abstract class Setting {
    public final String name;
    public String desc = "";

    protected Setting(String name) {
        this.name = name;
    }

    public abstract void save(JsonObject o);
    public abstract void load(JsonObject o);

    public static class Bool extends Setting {
        public boolean value;

        public Bool(String name, boolean value) {
            super(name);
            this.value = value;
        }

        public boolean get() { return value; }
        public void toggle() { value = !value; }

        @Override public void save(JsonObject o) { o.addProperty(name, value); }
        @Override public void load(JsonObject o) { if (o.has(name)) value = o.get(name).getAsBoolean(); }
    }

    public static class Num extends Setting {
        public double value, min, max, step;

        public Num(String name, double value, double min, double max, double step) {
            super(name);
            this.value = value;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        public double get() { return value; }

        public void set(double v) {
            v = Math.round((v - min) / step) * step + min;
            value = Math.max(min, Math.min(max, v));
        }

        public String format() {
            return String.format(Locale.ROOT, step >= 1 ? "%.0f" : "%.2f", value);
        }

        @Override public void save(JsonObject o) { o.addProperty(name, value); }
        @Override public void load(JsonObject o) { if (o.has(name)) set(o.get(name).getAsDouble()); }
    }

    public static class Mode extends Setting {
        public final String[] modes;
        public int index = 0;

        public Mode(String name, String... modes) {
            super(name);
            this.modes = modes;
        }

        public String get() { return modes[index]; }
        public boolean is(String m) { return modes[index].equalsIgnoreCase(m); }
        public void next() { index = (index + 1) % modes.length; }
        public void prev() { index = (index - 1 + modes.length) % modes.length; }

        @Override public void save(JsonObject o) { o.addProperty(name, get()); }

        @Override
        public void load(JsonObject o) {
            if (!o.has(name)) return;
            String v = o.get(name).getAsString();
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].equals(v)) index = i;
            }
        }
    }

    public static class Multi extends Setting {
        public final String[] options;
        public final boolean[] on;
        public boolean expanded = false;

        public Multi(String name, String[] options, String... defaults) {
            super(name);
            this.options = options;
            this.on = new boolean[options.length];
            for (String d : defaults) {
                for (int i = 0; i < options.length; i++) {
                    if (options[i].equals(d)) on[i] = true;
                }
            }
        }

        public boolean has(String option) {
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(option)) return on[i];
            }
            return false;
        }

        @Override
        public void save(JsonObject o) {
            JsonArray arr = new JsonArray();
            for (int i = 0; i < options.length; i++) {
                if (on[i]) arr.add(options[i]);
            }
            o.add(name, arr);
        }

        @Override
        public void load(JsonObject o) {
            if (!o.has(name)) return;
            for (int i = 0; i < on.length; i++) on[i] = false;
            for (JsonElement el : o.getAsJsonArray(name)) {
                String v = el.getAsString();
                for (int i = 0; i < options.length; i++) {
                    if (options[i].equals(v)) on[i] = true;
                }
            }
        }
    }
}