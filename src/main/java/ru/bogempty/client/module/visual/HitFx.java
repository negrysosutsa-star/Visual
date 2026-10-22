package ru.bogempty.client.module.visual;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;
import ru.bogempty.client.util.Draw;
import ru.bogempty.client.util.Projector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Всплывающие цифры урона над целями и хитмаркер в центре экрана. */
public class HitFx extends Module {
    private final Setting.Bool numbers = add(new Setting.Bool("Цифры урона", true), "Цифры всплывают над тем, кто получил урон.");
    private final Setting.Bool marker = add(new Setting.Bool("Хитмаркер", true), "Крестик в центре, когда ты попал.");
    private final Setting.Bool onlyMine = add(new Setting.Bool("Только мой урон", true), "Показывать только урон от твоих ударов.");
    private final Setting.Num scale = add(new Setting.Num("Размер цифр", 1.2, 0.8, 2.2, 0.1), "Размер чисел урона.");

    private static final class Pop {
        double x, y, z;
        float amount;
        long born;
        boolean mine;
        float dx;
    }

    private final Projector proj = new Projector();
    private final Map<Integer, Float> hp = new HashMap<>();
    private final List<Pop> pops = new ArrayList<>();
    private final Random rnd = new Random();
    private long lastAttack = 0L;
    private boolean prevAtk = false;
    private boolean prevSwing = false;
    private long markUntil = 0L;
    private boolean markKill = false;

    public HitFx() {
        super("УронХит", "Цифры урона и хитмаркер", Category.VISUAL);
    }

    @Override
    public void onDisable() {
        hp.clear();
        pops.clear();
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            hp.clear();
            pops.clear();
            return;
        }
        long now = System.currentTimeMillis();

        boolean pressed = mc.options.attackKey.isPressed();
        boolean swing = p.handSwinging;
        if ((pressed && !prevAtk) || (swing && !prevSwing)) lastAttack = now;
        prevAtk = pressed;
        prevSwing = swing;

        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || le == p) continue;
            if (p.distanceTo(le) > 48) continue;
            float h = le.getHealth();
            int id = le.getId();
            seen.add(id);
            Float prev = hp.put(id, h);
            if (prev != null && h < prev - 0.05f) {
                boolean mine = now - lastAttack < 450 && p.distanceTo(le) < 7.0;
                if (onlyMine.get() && !mine) continue;
                if (numbers.get()) {
                    Pop pop = new Pop();
                    pop.x = le.getX() + (rnd.nextDouble() - 0.5) * 0.5;
                    pop.y = le.getY() + le.getHeight() * 0.9;
                    pop.z = le.getZ() + (rnd.nextDouble() - 0.5) * 0.5;
                    pop.amount = prev - h;
                    pop.born = now;
                    pop.mine = mine;
                    pop.dx = (rnd.nextFloat() - 0.5f) * 30f;
                    pops.add(pop);
                }
                if (mine && marker.get()) {
                    markUntil = now + 280;
                    markKill = h <= 0.01f;
                }
            }
        }
        hp.keySet().retainAll(seen);
        pops.removeIf(pp -> now - pp.born > 1000);
    }

    @Override
    public void onRender2D(DrawContext c, float td) {
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();

        if (numbers.get() && !pops.isEmpty() && proj.begin(mc, c, td)) {
            for (Pop pp : pops) {
                float age = (now - pp.born) / 1000f;
                float[] s = proj.project(pp.x, pp.y + age * 1.1, pp.z);
                if (s == null) continue;
                float a = age < 0.7f ? 1f : Math.max(0f, 1f - (age - 0.7f) / 0.3f);
                float pop = age < 0.12f ? 0.6f + age / 0.12f * 0.5f : 1.1f - Math.min(0.1f, (age - 0.12f) * 0.3f);
                int col = pp.amount >= 8f ? 0xFFFF4D4D : pp.amount >= 4f ? 0xFFFFB347 : 0xFFFFFFFF;
                String txt = "-" + String.format(Locale.ROOT, pp.amount >= 10 ? "%.0f" : "%.1f", pp.amount);
                float sc = (float) scale.get() * pop * (pp.amount >= 8f ? 1.25f : 1f);
                MatrixStack ms = c.getMatrices();
                ms.push();
                ms.translate(s[0] + pp.dx * age, s[1], 0);
                ms.scale(sc, sc, 1f);
                int tw = Fonts.width(txt);
                Fonts.draw(c, txt, -tw / 2 + 1, 1, RenderUtils.alpha(0xFF000000, a * 0.7f));
                Fonts.draw(c, txt, -tw / 2, 0, RenderUtils.alpha(col, a));
                ms.pop();
            }
        }

        long left = markUntil - now;
        if (marker.get() && left > 0) {
            float k = left / 280f;
            int cx = c.getScaledWindowWidth() / 2, cy = c.getScaledWindowHeight() / 2;
            int col = RenderUtils.alpha(markKill ? 0xFFFF4D4D : 0xFFFFFFFF, Math.min(1f, k * 1.5f));
            int gap = 4 + Math.round((1f - k) * 3f);
            int len = markKill ? 8 : 6;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    Draw.line(c, cx + sx * gap, cy + sy * gap, cx + sx * (gap + len), cy + sy * (gap + len), 1, col);
                }
            }
        }
    }
}