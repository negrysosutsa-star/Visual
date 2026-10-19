package ru.bogempty.client.module.visual;

import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

public class CustomSky extends Module {
    private final Setting.Mode time = add(new Setting.Mode("Time",
            "Day", "Noon", "Sunset", "Night", "Midnight", "Custom", "Cycle"),
            "Fixed time of day, your own value or a fast day/night cycle.");
    private final Setting.Num custom = add(new Setting.Num("Custom time", 6000, 0, 24000, 100),
            "Time of day in ticks (0 sunrise, 6000 noon, 12000 sunset, 18000 midnight).");
    private final Setting.Num speed = add(new Setting.Num("Cycle speed", 10, 1, 100, 1),
            "How many ticks of time pass every tick in Cycle mode.");
    private final Setting.Mode weather = add(new Setting.Mode("Weather", "Default", "Clear", "Rain", "Thunder"),
            "Client-side weather.");

    private long cycle = 0L;

    public CustomSky() {
        super("CustomSky", "Custom time of day and weather", Category.VISUAL);
    }

    @Override
    public void onTick() {
        if (mc.world == null) return;

        long t;
        if (time.is("Cycle")) {
            cycle = (cycle + (long) speed.get()) % 24000L;
            t = cycle;
        } else if (time.is("Custom")) {
            t = (long) custom.get();
        } else if (time.is("Noon")) {
            t = 6000L;
        } else if (time.is("Sunset")) {
            t = 12500L;
        } else if (time.is("Night")) {
            t = 14500L;
        } else if (time.is("Midnight")) {
            t = 18000L;
        } else {
            t = 1000L;
        }
        mc.world.getLevelProperties().setTimeOfDay(t);

        if (weather.is("Clear")) {
            mc.world.setRainGradient(0f);
            mc.world.setThunderGradient(0f);
        } else if (weather.is("Rain")) {
            mc.world.setRainGradient(1f);
            mc.world.setThunderGradient(0f);
        } else if (weather.is("Thunder")) {
            mc.world.setRainGradient(1f);
            mc.world.setThunderGradient(1f);
        }
    }
}