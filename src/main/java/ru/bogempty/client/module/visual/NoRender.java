package ru.bogempty.client.module.visual;

import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.ParticlesMode;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;
import ru.bogempty.client.util.OptOverride;

/** Отключает лишнюю отрисовку. Каждая настройка применяется и возвращается как было. */
public class NoRender extends Module {
    private final Setting.Mode particles = add(new Setting.Mode("Частицы", "Не менять", "Меньше", "Выключить"),
            "Режим частиц: меньше или совсем без них.");
    private final Setting.Bool clouds = add(new Setting.Bool("Облака", true), "Убрать облака.");
    private final Setting.Bool shadows = add(new Setting.Bool("Тени существ", true), "Убрать круглые тени под существами.");
    private final Setting.Bool weather = add(new Setting.Bool("Погода", false), "Убрать дождь, снег и грозу (только у тебя).");
    private final Setting.Bool bobbing = add(new Setting.Bool("Покачивание камеры", false), "Убрать покачивание камеры при ходьбе.");
    private final Setting.Bool fovFx = add(new Setting.Bool("Эффект FOV", false), "Не менять FOV от спринта, зелий и натяжения лука.");
    private final Setting.Bool distortion = add(new Setting.Bool("Искажения", false), "Убрать тошноту и эффект портала.");

    private final OptOverride<ParticlesMode> oParticles = new OptOverride<>();
    private final OptOverride<CloudRenderMode> oClouds = new OptOverride<>();
    private final OptOverride<Boolean> oShadows = new OptOverride<>();
    private final OptOverride<Boolean> oBob = new OptOverride<>();
    private final OptOverride<Double> oFov = new OptOverride<>();
    private final OptOverride<Double> oDist = new OptOverride<>();

    public NoRender() {
        super("NoRender", "Отключает лишнюю отрисовку", Category.VISUAL);
    }

    @Override
    public void onTick() {
        GameOptions o = mc.options;
        if (o == null) return;

        if (particles.is("Не менять")) oParticles.restore(o.getParticles());
        else oParticles.apply(o.getParticles(), true,
                particles.is("Выключить") ? ParticlesMode.MINIMAL : ParticlesMode.DECREASED);

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
        oParticles.restore(o.getParticles());
        oClouds.restore(o.getCloudRenderMode());
        oShadows.restore(o.getEntityShadows());
        oBob.restore(o.getBobView());
        oFov.restore(o.getFovEffectScale());
        oDist.restore(o.getDistortionEffectScale());
    }
}