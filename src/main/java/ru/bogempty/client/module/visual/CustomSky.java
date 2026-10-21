package ru.bogempty.client.module.visual;

import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.SpaceSky;
import ru.bogempty.client.setting.Setting;

public class CustomSky extends Module {
    private final Setting.Mode style = add(new Setting.Mode("Стиль неба",
            "Обычное", "Галактика", "Туманность", "Звёздный рой", "Космический закат", "Аврора"),
            "Космическое небо. «Обычное» — ванильное. Остальные рисуются заново и заменяют небо.");
    private final Setting.Num variant = add(new Setting.Num("Вариант", 1, 1, 20, 1),
            "Номер варианта: у каждого номера своя картинка неба.");
    private final Setting.Num stars = add(new Setting.Num("Плотность звёзд", 5, 1, 10, 1),
            "Сколько звёзд на небе.");
    private final Setting.Num sat = add(new Setting.Num("Насыщенность %", 100, 0, 150, 5),
            "Яркость цветов неба. 0 — чёрно-белое.");
    private final Setting.Mode time = add(new Setting.Mode("Время",
            "Не менять", "День", "Полдень", "Закат", "Ночь", "Полночь", "Своё", "Цикл"),
            "Время суток: фиксированное, своё значение или быстрая смена дня и ночи.");
    private final Setting.Num custom = add(new Setting.Num("Своё время", 6000, 0, 24000, 100),
            "Время в тиках (0 рассвет, 6000 полдень, 12000 закат, 18000 полночь).");
    private final Setting.Num speed = add(new Setting.Num("Скорость цикла", 10, 1, 100, 1),
            "Сколько тиков проходит за один тик в режиме «Цикл».");
    private final Setting.Mode weather = add(new Setting.Mode("Погода", "Обычная", "Ясно", "Дождь", "Гроза"),
            "Погода только на твоей стороне (клиентская).");

    private long cycle = 0L;

    public CustomSky() {
        super("CustomSky", "Космическое небо, время суток и погода", Category.VISUAL);
    }

    @Override
    public void onDisable() {
        SpaceSky.restore();
    }

    @Override
    public void onTick() {
        if (mc.world == null) return;

        if (style.is("Обычное")) {
            SpaceSky.restore();
        } else {
            SpaceSky.update(mc.world, style.get(), (int) stars.get(), (int) sat.get(), (int) variant.get());
        }

        if (!time.is("Не менять")) {
            long t;
            if (time.is("Цикл")) {
                cycle = (cycle + (long) speed.get()) % 24000L;
                t = cycle;
            } else if (time.is("Своё")) {
                t = (long) custom.get();
            } else if (time.is("Полдень")) {
                t = 6000L;
            } else if (time.is("Закат")) {
                t = 12500L;
            } else if (time.is("Ночь")) {
                t = 14500L;
            } else if (time.is("Полночь")) {
                t = 18000L;
            } else {
                t = 1000L;
            }
            mc.world.getLevelProperties().setTimeOfDay(t);
        }

        if (weather.is("Ясно")) {
            mc.world.setRainGradient(0f);
            mc.world.setThunderGradient(0f);
        } else if (weather.is("Дождь")) {
            mc.world.setRainGradient(1f);
            mc.world.setThunderGradient(0f);
        } else if (weather.is("Гроза")) {
            mc.world.setRainGradient(1f);
            mc.world.setThunderGradient(1f);
        }
    }
}