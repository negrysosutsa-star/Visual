package ru.bogempty.client.module.visual;

import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;
import ru.bogempty.client.util.OptOverride;

/** Отключает лишнюю отрисовку. Каждая настройка применяется и возвращается как было. */
public class NoRender extends Module {
    private final Setting.Mode particles = add(new Setting.Mode("Частицы", "Не менять", "Меньше", "Выключить"),
            "Режим частиц: меньше или совсем без них.");
    private final Setting.Bool clouds = add(new Setting.Bool("Облака", true), "Убрать облака.");
    private final Setting.Bool fog = add(new Setting.Bool("Туман", false), "Отключить дальний туман.");
    private final Setting.Bool shadows = add(new Setting.Bool("Тени существ", true), "Убрать круглые тени под существами.");
    private final Setting.Bool weather = add(new Setting.Bool("Погода", false), "Убрать дождь, снег и грозу (только у тебя).");
    private final Setting.Bool bobbing = add(new Setting.Bool("Покачивание камеры", false), "Убрать покачивание камеры при ходьбе.");
    private final Setting.Bool fovFx = add(new Setting.Bool("Эффект FOV", false), "Не менять FOV от спринта, зелий и натяжения лука.");
    private final Setting.Bool distortion = add(new Setting.Bool("Искажения", false), "Убрать тошноту и эффект портала.");

    private final OptOverride<Object> oParticles = new OptOverride<>();
    private final OptOverride<CloudRenderMode> oClouds = new OptOverride<>();
    private final OptOverride<Boolean> oShadows = new OptOverride<>();
    private final OptOverride<Boolean> oBob = new OptOverride<>();
    private final OptOverride<Double> oFov = new OptOverride<>();
    private final OptOverride<Double> oDist = new OptOverride<>();

    public boolean isFogDisabled() { return enabled && fog.get(); }

    public NoRender() {
        super("NoRender", "Отключает лишнюю отрисовку", Category.VISUAL);
    }

    /** Настройка частиц без обращения к классу режима по имени (его пакет менялся между версиями). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private SimpleOption<Object> particleOption(GameOptions o) {
        return (SimpleOption) o.getParticles();
    }

    /** Находит значение режима частиц по имени (ALL, DECREASED, MINIMAL), иначе по номеру. */
    private static Object pickMode(Object current, String name, int ordinal) {
        Class<?> ec = ((Enum<?>) current).getDeclaringClass();
        Object[] all = ec.getEnumConstants();
        for (Object e : all) {
            if (((Enum<?>) e).name().equals(name)) return e;
        }
        return all[Math.min(ordinal, all.length - 1)];
    }

    @Override
    public void onTick() {
        GameOptions o = mc.options;
        if (o == null) return;

        SimpleOption<Object> po = particleOption(o);
        if (particles.is("Не менять")) {
            oParticles.restore(po);
        } else {
            Object want = particles.is("Выключить")
                    ? pickMode(po.getValue(), "MINIMAL", 2)
                    : pickMode(po.getValue(), "DECREASED", 1);
            oParticles.apply(po, true, want);
        }

        oClouds.apply(o.getCloudRenderMode(), clouds.get(), CloudRenderMode.OFF);
        oShadows.apply(o.getEntityShadows(), shadows.get(), false);
        oBob.apply(o.getBobView(), bobbing.get(), false);
        oFov.apply(o.getFovEffectScale(), fovFx.get(), 0.0);
        oDist.apply(o.getDistortionEffectScale(), distortion.get(), 0.0);

        if (weather.get() && mc.world != null) {
            mc.world.setRainGradient(0f);
            mc.world.setThunderGradient(0f);
        }
    }

    @Override
    public void onDisable() {
        GameOptions o = mc.options;
        if (o == null) return;
        oParticles.restore(particleOption(o));
        oClouds.restore(o.getCloudRenderMode());
        oShadows.restore(o.getEntityShadows());
        oBob.restore(o.getBobView());
        oFov.restore(o.getFovEffectScale());
        oDist.restore(o.getDistortionEffectScale());
    }
}