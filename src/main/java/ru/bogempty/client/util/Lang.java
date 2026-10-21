package ru.bogempty.client.util;

import java.util.HashMap;
import java.util.Map;

/** Русские названия и описания. Внутри кода остаются английские ключи, чтобы конфиги не ломались. */
public final class Lang {
    private static final Map<String, String> M = new HashMap<>();

    private static void p(String en, String ru) {
        M.put(en, ru);
    }

    static {
        // вкладки
        p("Combat", "Бой");
        p("Movement", "Движение");
        p("Visual", "Визуал");

        // модули
        p("TriggerBot", "ТриггерБот");
        p("AutoSprint", "Авто-спринт");
        p("ShiftTap", "ШифтТап");
        p("FullBright", "Яркость");
        p("Zoom", "Зум");
        p("CustomSky", "Небо");
        p("NoRender", "НоуРендер");
        p("CustomCrosshair", "Прицел");
        p("Watermark", "Водяной знак");
        p("InfoHud", "Инфо");
        p("Keystrokes", "Клавиши");
        p("TargetHud", "Цель");
        p("ArmorHud", "Броня");
        p("ArrayList", "Список модулей");
        p("AiTriggerBot", "ИИ ТриггерБот");

        // TriggerBot: настройки
        p("Mode", "Режим");
        p("Targets", "Цели");
        p("Conditions", "Условия");
        p("Range", "Дистанция");
        p("Through entities", "Сквозь существ");
        p("Own cooldown", "Свой кулдаун");
        p("Cooldown %", "Кулдаун %");
        p("Fall speed", "Скорость падения");
        p("Skip landing hit", "Пропуск удара при приземлении");
        p("Skip water jump", "Пропуск прыжка из воды");
        p("Auto jump", "Авто-прыжок");
        p("Random delay", "Случайная задержка");
        // TriggerBot: значения
        p("Smart", "Умный");
        p("Only crits", "Только криты");
        p("Players", "Игроки");
        p("Mobs", "Мобы");
        p("All", "Все");
        p("Only with weapon", "Только с оружием");
        p("Through grass", "Сквозь траву");
        p("Naked", "Без брони");
        p("Invisible", "Невидимые");
        p("Only in armor", "Только в броне");

        // Прицел, Зум
        p("Size", "Размер");
        p("Gap", "Зазор");
        p("Thickness", "Толщина");
        p("Center dot", "Точка в центре");
        p("FOV", "Угол обзора");

        // описания TriggerBot
        p("Smart: crit in the air, normal hit when standing. Only crits: hit only while falling.",
                "Умный: крит в воздухе, обычный удар на земле. Только криты: бьёт только во время падения.");
        p("Which entities to attack.", "Каких существ атаковать.");
        p("Extra conditions for attacking.", "Дополнительные условия для атаки.");
        p("Maximum attack distance.", "Максимальная дистанция атаки.");
        p("Hit the target through any other living entities.", "Бить цель сквозь других живых существ.");
        p("Ignore the vanilla threshold and use your own cooldown percent.",
                "Не использовать ванильный порог, а взять свой процент кулдауна.");
        p("Minimum weapon readiness for a hit (needs Own cooldown).",
                "Минимальная готовность оружия для удара (нужен «Свой кулдаун»).");
        p("Vertical fall speed from which the fall counts as a crit.",
                "Вертикальная скорость, с которой падение считается критическим.");
        p("Skip the last tick of the fall: the server counts such a hit as a ground hit.",
                "Пропустить последний тик падения: сервер засчитает такой удар как удар с земли.");
        p("Do not hit while jumping out of water.", "Не бить при прыжке из воды.");
        p("Jump by itself when the weapon is charged and hit on the way down.",
                "Прыгать самому, когда оружие заряжено, и бить на спуске.");
        p("Random extra delay before a hit, in ms.", "Случайная добавочная задержка перед ударом, в мс.");

        // интерфейс
        p("No settings", "Нет настроек");
    }

    public static String t(String s) {
        if (s == null) return "";
        String v = M.get(s);
        return v != null ? v : s;
    }

    private Lang() {}
}