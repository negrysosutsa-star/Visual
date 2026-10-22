package ru.bogempty.client.module.ai;

import ru.bogempty.client.friend.FriendManager;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class NeuralAimAssist extends Module {

    private final Setting.Mode mode = add(new Setting.Mode("Режим", "Ассист", "Сайлент", "Только обучение"),
            "Ассист: мягкая наводка. Сайлент: еле заметно. Только обучение: сам не крутит.");
    private final Setting.Mode targets = add(new Setting.Mode("Цели", "Игроки", "Все"),
            "На кого наводиться.");
    private final Setting.Num fov = add(new Setting.Num("FOV", 55, 15, 120, 1),
            "Конус в градусах.");
    private final Setting.Num range = add(new Setting.Num("Дистанция", 4.5, 2.0, 6.0, 0.1),
            "Макс. дистанция.");
    private final Setting.Num speed = add(new Setting.Num("Скорость", 0.22, 0.05, 0.6, 0.01),
            "Базовая скорость. Ниже = спокойнее.");
    private final Setting.Num smooth = add(new Setting.Num("Плавность", 0.82, 0.5, 0.95, 0.01),
            "Сглаживание. 0.8+ почти без рывков.");
    private final Setting.Num maxStep = add(new Setting.Num("Макс. шаг °", 3.5, 1.0, 8.0, 0.1),
            "Максимум градусов за тик. Главный анти-дёргатель.");
    private final Setting.Num missChance = add(new Setting.Num("Промах %", 10, 0, 35, 1),
            "Шанс тик пропустить.");
    private final Setting.Num reactMin = add(new Setting.Num("Реакция от мс", 60, 0, 250, 5),
            "Мин. задержка реакции.");
    private final Setting.Num reactMax = add(new Setting.Num("Реакция до мс", 150, 0, 400, 5),
            "Макс. задержка реакции.");
    private final Setting.Bool learn = add(new Setting.Bool("Учиться на мне", true),
            "Пишет твои движения мыши.");
    private final Setting.Num learnStrength = add(new Setting.Num("Сила обучения", 0.7, 0.1, 1.0, 0.05),
            "Насколько твой стиль перебивает базу.");
    private final Setting.Bool visibleOnly = add(new Setting.Bool("Только видимые", true),
            "Не через стены.");
    private final Setting.Bool ignoreFriends = add(new Setting.Bool("Игнор друзей", true),
            "Пропуск .friend.");
    private final Setting.Bool hud = add(new Setting.Bool("Панель", true),
            "Статистика обучения.");
    private final Setting.Bool resetLearn = add(new Setting.Bool("Сбросить обучение", false),
            "Стереть выученное.");

    private double learnedSpeed = 0.22;
    private double learnedSmooth = 0.82;
    private int learnSamples = 0;

    private LivingEntity locked = null;
    private long seeSince = 0L;
    private long reactUntil = 0L;
    private float lastYaw, lastPitch;
    private float velYaw, velPitch;
    private float flash = 0f;
    private int shownSamples = -1;
    private String why = "—";

    public NeuralAimAssist() {
        super("NeuralAimAssist", "Нейро-аим с обучением под твой стиль", Category.AI);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null) {
            locked = null;
            velYaw = 0;
            velPitch = 0;
            why = "Нет мира";
            return;
        }

        if (resetLearn.get()) {
            resetLearn.value = false;
            learnedSpeed = 0.22;
            learnedSmooth = 0.82;
            learnSamples = 0;
            why = "Обучение сброшено";
        }

        if (learn.get()) samplePlayerAim(p);

        if (mode.is("Только обучение")) {
            why = "Режим: только обучение";
            return;
        }

        LivingEntity t = findTarget(p);
        long now = System.currentTimeMillis();

        if (t == null) {
            locked = null;
            // плавное затухание — не обнулять резко
            velYaw *= 0.5f;
            velPitch *= 0.5f;
            if (Math.abs(velYaw) < 0.01f) velYaw = 0;
            if (Math.abs(velPitch) < 0.01f) velPitch = 0;
            why = "Нет цели в FOV";
            return;
        }

        if (t != locked) {
            locked = t;
            seeSince = now;
            int rmin = (int) reactMin.get();
            int rmax = Math.max(rmin, (int) reactMax.get());
            if (learnSamples > 250) {
                rmin = Math.max(35, rmin - 20);
                rmax = Math.max(rmin + 25, rmax - 30);
            }
            reactUntil = now + ThreadLocalRandom.current().nextInt(rmin, rmax + 1);
            // при смене цели не дёргаем — гасим скорость
            velYaw *= 0.3f;
            velPitch *= 0.3f;
        }

        if (now < reactUntil) {
            why = "Реакция…";
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() * 100.0 < missChance.get()) {
            why = "Промах (человечность)";
            return;
        }

        aimAt(p, t);
        why = "Веду: " + t.getName().getString();
    }

    private void samplePlayerAim(ClientPlayerEntity p) {
        float dy = MathHelper.wrapDegrees(p.getYaw() - lastYaw);
        float dp = p.getPitch() - lastPitch;
        lastYaw = p.getYaw();
        lastPitch = p.getPitch();

        double mag = Math.sqrt(dy * dy + dp * dp);
        if (mag < 0.1 || mag > 20.0) return;

        double s = MathHelper.clamp(mag / 15.0, 0.05, 0.7);
        double sm = MathHelper.clamp(1.0 - mag / 26.0, 0.5, 0.93);
        learnedSpeed = learnedSpeed * 0.988 + s * 0.012;
        learnedSmooth = learnedSmooth * 0.992 + sm * 0.008;
        learnSamples++;
    }

    private void aimAt(ClientPlayerEntity p, LivingEntity t) {
        Vec3d eye = p.getEyePos();
        // грудь, не хедшот-лазер
        double body = 0.4 + (learnSamples % 7) * 0.02;
        Vec3d point = t.getPos().add(0.0, t.getHeight() * body, 0.0);
        Vec3d diff = point.subtract(eye);
        double dist = diff.length();
        if (dist < 1e-4) return;

        float targetYaw = (float) (MathHelper.atan2(diff.z, diff.x) * (180.0 / Math.PI)) - 90f;
        float targetPitch = (float) (-(MathHelper.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)) * (180.0 / Math.PI)));

        float yaw = p.getYaw();
        float pitch = p.getPitch();
        float dYaw = MathHelper.wrapDegrees(targetYaw - yaw);
        float dPitch = targetPitch - pitch;

        double spd = speed.get();
        double sm = smooth.get();
        if (learn.get() && learnSamples > 30) {
            double k = learnStrength.get() * Math.min(1.0, learnSamples / 350.0);
            spd = spd * (1.0 - k) + learnedSpeed * k;
            sm = sm * (1.0 - k) + learnedSmooth * k;
        }

        // дистанция
        spd *= MathHelper.clamp(1.1 - dist / range.get(), 0.35, 1.0);
        if (System.currentTimeMillis() - seeSince > 3000L) spd *= 0.8;

        // целевая скорость кадра
        float wantYaw = (float) (dYaw * (1.0 - sm) * spd);
        float wantPitch = (float) (dPitch * (1.0 - sm) * spd);

        // сильная инерция — главный фикс дёрганий
        velYaw = velYaw * 0.72f + wantYaw * 0.28f;
        velPitch = velPitch * 0.72f + wantPitch * 0.28f;

        float cap = (float) maxStep.get();
        if (learnSamples > 300) cap = Math.min(cap + 0.8f, 5.5f);
        velYaw = MathHelper.clamp(velYaw, -cap, cap);
        velPitch = MathHelper.clamp(velPitch, -cap, cap);

        if (mode.is("Сайлент")) {
            velYaw *= 0.2f;
            velPitch *= 0.2f;
        }

        // мёртвая зона — не дрожим на субпикселе
        if (Math.abs(dYaw) < 0.35f) velYaw *= 0.4f;
        if (Math.abs(dPitch) < 0.35f) velPitch *= 0.4f;

        p.setYaw(yaw + velYaw);
        p.setPitch(MathHelper.clamp(pitch + velPitch, -90f, 90f));
    }

    private LivingEntity findTarget(ClientPlayerEntity p) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d eye = p.getEyePos();
        double fovHalf = fov.get() / 2.0;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || le == p || !le.isAlive()) continue;

            if (le instanceof PlayerEntity pl) {
                if (!(targets.is("Игроки") || targets.is("Все"))) continue;
                if (ignoreFriends.get() && FriendManager.isFriend(pl.getName().getString())) continue;
            } else if (!targets.is("Все")) {
                continue;
            }

            double d = eye.distanceTo(le.getBoundingBox().getCenter());
            if (d > range.get()) continue;

            Vec3d diff = le.getBoundingBox().getCenter().subtract(eye).normalize();
            double dot = p.getRotationVec(1f).dotProduct(diff);
            double ang = Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1, 1)));
            if (ang > fovHalf) continue;
            if (visibleOnly.get() && !p.canSee(le)) continue;

            double score = ang * 2.0 + d;
            if (score < bestScore) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }

    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        if (!hud.get() || mc.player == null) return;

        if (learnSamples != shownSamples) {
            if (learnSamples > shownSamples && shownSamples >= 0) flash = 1f;
            shownSamples = learnSamples;
        }
        flash *= 0.92f;

        int x = 8;
        int y = mc.getWindow().getScaledHeight() / 2 + 105;
        int w = 160;
        int h = 118;
        RenderUtils.drawRoundedRect(c, x, y, x + w, y + h, 6, 0xAA0C0C14);
        Fonts.draw(c, "НейроАим", x + 8, y + 6, 0xFFFFFFFF);
        Fonts.draw(c, mode.get(), x + 8, y + 17, 0xFF8F86FF);

        String big = String.valueOf(learnSamples);
        MatrixStack ms = c.getMatrices();
        ms.push();
        ms.translate(x + 8, y + 30, 0);
        ms.scale(2.0f, 2.0f, 1f);
        Fonts.draw(c, big, 0, 0, RenderUtils.lerp(0xFFFFFFFF, 0xFF4CE08A, flash));
        ms.pop();
        Fonts.draw(c, "сэмплов", x + 8 + Fonts.width(big) * 2 + 6, y + 40, 0xFF6E6E86);

        float mat = (float) Math.min(1.0, learnSamples / 500.0);
        bar(c, x + 8, y + 58, w - 16, mat, "Обученность");
        float style = (float) MathHelper.clamp(learnedSmooth, 0, 1);
        bar(c, x + 8, y + 74, w - 16, style, "Плавность стиля");

        Fonts.draw(c, why, x + 8, y + 96, 0xFFE0C070);
    }

    private void bar(DrawContext c, int x, int y, int w, float v, String label) {
        float f = MathHelper.clamp(v, 0f, 1f);
        Fonts.draw(c, label, x, y - 1, 0xFF6E6E86);
        String pct = Math.round(f * 100) + "%";
        Fonts.draw(c, pct, x + w - Fonts.width(pct), y - 1, 0xFF8F86FF);
        RenderUtils.drawRoundedRect(c, x, y + 9, x + w, y + 12, 1, 0xFF22222E);
        RenderUtils.drawRoundedRect(c, x, y + 9, x + Math.max(2, Math.round(w * f)), y + 12, 1, 0xFF7C6CFF);
    }
}