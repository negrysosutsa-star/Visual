package ru.bogempty.client.module.visual;

import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;
import ru.bogempty.client.util.OptOverride;

/** ФПС-буст: облегчает графику и в режиме "Авто" сам подгоняет дальность прорисовки под нужный FPS. */
public class FpsBoost extends Module {
    private final Setting.Mode mode = add(new Setting.Mode("Режим", "Авто", "Лёгкий", "Средний", "Максимум"),
            "Лёгкий: быстрая графика. Средний и Максимум: ещё и ближе прорисовка. Авто: сам подбирает дальность под нужный FPS.");
    private final Setting.Num targetFps = add(new Setting.Num("Целевой FPS", 90, 30, 240, 5),
            "Для режима Авто: если FPS ниже, прорисовка уменьшается.");
    private final Setting.Num minView = add(new Setting.Num("Мин. прорисовка", 6, 3, 16, 1),
            "Для режима Авто: ниже этого дальность не падает.");
    private final Setting.Num maxView = add(new Setting.Num("Макс. прорисовка", 16, 6, 32, 1),
            "Для режима Авто: выше этого дальность не растёт.");
    private final Setting.Bool entities = add(new Setting.Bool("Меньше существ", true),
            "Рисовать существ только поближе.");

    private final OptOverride<GraphicsMode> oGfx = new OptOverride<>();
    private final OptOverride<Integer> oBiome = new OptOverride<>();
    private final OptOverride<Integer> oView = new OptOverride<>();
    private final OptOverride<Integer> oSim = new OptOverride<>();
    private final OptOverride<Double> oEnt = new OptOverride<>();

    private int curView = -1;
    private int timer = 0;

    public FpsBoost() {
        super("FpsBoost", "ФПС-буст: лёгкая графика и умная дальность", Category.VISUAL);
    }

    @Override
    public void onEnable() {
        curView = (int) maxView.get();
        timer = 0;
    }

    @Override
    public void onTick() {
        GameOptions o = mc.options;
        if (o == null) return;

        oGfx.apply(o.getGraphicsMode(), true, GraphicsMode.FAST);

        boolean light = mode.is("Лёгкий");
        boolean mid = mode.is("Средний");
        boolean max = mode.is("Максимум");
        boolean auto = mode.is("Авто");

        oBiome.apply(o.getBiomeBlendRadius(), true, light ? 1 : 0);

        double ent = max ? 0.5 : mid ? 0.65 : auto ? 0.7 : 0.8;
        oEnt.apply(o.getEntityDistanceScaling(), entities.get(), ent);

        if (light) {
            oView.restore(o.getViewDistance());
            oSim.restore(o.getSimulationDistance());
        } else if (mid) {
            oView.apply(o.getViewDistance(), true, 10);
            oSim.apply(o.getSimulationDistance(), true, 8);
        } else if (max) {
            oView.apply(o.getViewDistance(), true, 6);
            oSim.apply(o.getSimulationDistance(), true, 5);
        } else {
            // Авто: раз в 2 секунды сверяем FPS и двигаем дальность на 1 чанк
            int lo = (int) minView.get();
            int hi = Math.max(lo, (int) maxView.get());
            if (curView < 0) curView = hi;
            curView = Math.max(lo, Math.min(hi, curView));
            if (++timer >= 40 && mc.world != null) {
                timer = 0;
                int fps = mc.getCurrentFps();
                int tgt = (int) targetFps.get();
                if (fps < tgt - 8 && curView > lo) curView--;
                else if (fps > tgt + 30 && curView < hi) curView++;
            }
            oView.apply(o.getViewDistance(), true, curView);
            oSim.apply(o.getSimulationDistance(), true, Math.max(5, Math.min(curView, 12)));
        }
    }

    @Override
    public void onDisable() {
        GameOptions o = mc.options;
        if (o == null) return;
        oGfx.restore(o.getGraphicsMode());
        oBiome.restore(o.getBiomeBlendRadius());
        oView.restore(o.getViewDistance());
        oSim.restore(o.getSimulationDistance());
        oEnt.restore(o.getEntityDistanceScaling());
    }
}