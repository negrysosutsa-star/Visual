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

/**
 * Аим в духе SystemDLC: пружина + разгон, точка по телу, обучение под мышь.
 */
public class NeuralAimAssist extends Module {

    private final Setting.Mode mode = add(new Setting.Mode("Режим", "Ассист", "Легит", "Сайлент", "Только обучение"),
            "Ассист — обычный. Легит — медленнее и мягче. Сайлент — почти не видно. Только обучение — не крутит.");
    private final Setting.Mode targets = add(new Setting.Mode("Цели", "Игроки", "Все"),
            "На кого вести.");
    private final Setting.Mode hitbox = add(new Setting.Mode("Точка", "Тело", "Голова", "Ноги", "Рандом"),
            "Куда целиться на модели.");
    private final Setting.Num fov = add(new Setting.Num("FOV", 60, 20, 140, 1),
            "Конус захвата, градусы.");
    private final Setting.Num range = add(new Setting.Num("Дистанция", 4.8, 2.0, 6.0, 0.1),
            "Макс. дистанция.");
    private final Setting.Num speed = add(new Setting.Num("Скорость", 0.38, 0.08, 1.0, 0.01),
            "Базовая сила пружины.");
    private final Setting.Num damp = add(new Setting.Num("Демпфер", 0.78, 0.4, 0.95, 0.01),
            "Гашение скорости. Выше = меньше инерции и дёрганий.");
    private final Setting.Num accel = add(new Setting.Num("Разгон", 0.45, 0.1, 1.0, 0.01),
            "Как быстро набирает скорость к цели.");
    private final Setting.Num maxStep = add(new Setting.Num("Макс. шаг °", 4.2, 1.5, 10.0, 0.1),
            "Потолок градусов за тик.");
    private final Setting.Num sens = add(new Setting.Num("Чувств. рядом", 0.55, 0.15, 1.0, 0.05),
            "Множитель скорости когда прицел уже близко к цели.");
    private final Setting.Num missChance = add(new Setting.Num("Промах %", 6, 0, 30, 1),
            "Шанс тик не вести (человечность).");
    private final Setting.Num reactMin = add(new Setting.Num("Реакция от мс", 45, 0, 200, 5),
            "Мин. задержка после появления в FOV.");
    private final Setting.Num reactMax = add(new Setting.Num("Реакция до мс", 110, 0, 300, 5),
            "Макс. задержка реакции.");
    private final Setting.Bool learn = add(new Setting.Bool("Учиться на мне", true),
            "Подстраивается под твою мышь.");
    private final Setting.Num learnStrength = add(new Setting.Num("Сила обучения", 0.75, 0.1, 1.0, 0.05),
            "Вес твоего стиля после обучения.");
    private final Setting.Bool visibleOnly = add(new Setting.Bool("Только видимые", true),
            "Не вести сквозь стены.");
    private final Setting.Bool ignoreFriends = add(new Setting.Bool("Игнор друзей", true),
            "Пропуск .friend.");
    private final Setting.Bool hud = add(new Setting.Bool("Панель", true),
            "Сэмплы и обученность.");
    private final Setting.Bool resetLearn = add(new Setting.Bool("Сбросить обучение", false),
            "Обнулить выученное.");

    private double learnedSpeed = 0.38;
    private double learnedDamp = 0.78;
    private int learnSamples = 0;

    private LivingEntity locked = null;
    private long seeSince = 0L;
    private long reactUntil = 0L;
    private float lastYaw, lastPitch;
    private float velYaw, velPitch;
    private float bodyOffset = 0.45f;
    private long nextBodyShift = 0L;
    private float flash = 0f;
    private int shownSamples = -1;
    private String why = "—";

    public NeuralAimAssist() {
        super("NeuralAimAssist", "Аим SystemDLC-стиль с обучением", Category.AI);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null) {
            locked = null;
            velYaw = velPitch = 0;
            why = "Нет мира";
            return;
        }

        if (resetLearn.get()) {
            resetLearn.value = false;
            learnedSpeed = 0.38;
            learnedDamp = 0.78;
            learnSamples = 0;
            why = "Обучение сброшено";
        }

        if (learn.get()) samplePlayerAim(p);

        if (mode.is("Только обучение")) {
            why = "Только обучение";
            return;
        }

        LivingEntity t = findTarget(p);
        long now = System.currentTimeMillis();

        if (t == null) {
            locked = null;
            velYaw *= 0.55f;
            velPitch *= 0.55f;
            if (Math.abs(velYaw) < 0.02f) velYaw = 0;
            if (Math.abs(velPitch) < 0.02f) velPitch = 0;
            why = "Нет цели";
            return;
        }

        if (t != locked) {
            locked = t;
            seeSince = now;
            int rmin = (int) reactMin.get();
            int rmax = Math.max(rmin, (int) reactMax.get());
            if (learnSamples > 200) {
                rmin = Math.max(25, rmin - 15);
                rmax = Math.max(rmin + 20, rmax - 25);
            }
            reactUntil = now + ThreadLocalRandom.current().nextInt(rmin, rmax + 1);
            velYaw *= 0.25f;
            velPitch *= 0.25f;
            pickBodyOffset(true);
        }

        if (now >= nextBodyShift) pickBodyOffset(false);

        if (now < reactUntil) {
            why = "Реакция…";
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() * 100.0 < missChance.get()) {
            why = "Промах";
            return;
        }

        aimSpring(p, t);
        why = "Веду: " + t.getName().getString();
    }

    private void pickBodyOffset(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now < nextBodyShift) return;
        nextBodyShift = now + 180 + ThreadLocalRandom.current().nextInt(220);

        switch (hitbox.get()) {
            case "Голова" -> bodyOffset = 0.88f + ThreadLocalRandom.current().nextFloat() * 0.08f;
            case "Ноги" -> bodyOffset = 0.18f + ThreadLocalRandom.current().nextFloat() * 0.12f;
            case "Рандом" -> bodyOffset = 0.25f + ThreadLocalRandom.current().nextFloat() * 0.6f;
            default -> bodyOffset = 0.42f + ThreadLocalRandom.current().nextFloat() * 0.22f; // тело
        }
    }

    private void samplePlayerAim(ClientPlayerEntity p) {
        float dy = MathHelper.wrapDegrees(p.getYaw() - lastYaw);
        float dp = p.getPitch() - lastPitch;
        lastYaw = p.getYaw();
        lastPitch = p.getPitch();

        double mag = Math.sqrt(dy * dy + dp * dp);
        if (mag < 0.08 || mag > 24.0) return;

        // скорость мыши → пружина
        double s = MathHelper.clamp(mag / 14.0, 0.1, 0.95);
        double d = MathHelper.clamp(1.0 - mag / 30.0, 0.55, 0.9);
        learnedSpeed = learnedSpeed * 0.986 + s * 0.014;
        learnedDamp = learnedDamp * 0.99 + d * 0.01;
        learnSamples++;
    }

    private void aimSpring(ClientPlayerEntity p, LivingEntity t) {
        Vec3d eye = p.getEyePos();
        Vec3d point = t.getPos().add(0.0, t.getHeight() * bodyOffset, 0.0);

        // лёгкий предикт по скорости цели (SystemDLC-вайб)
        Vec3d vel = t.getVelocity();
        point = point.add(vel.x * 0.08, vel.y * 0.03, vel.z * 0.08);

        Vec3d diff = point.subtract(eye);
        double dist = diff.length();
        if (dist < 1e-4) return;

        float targetYaw = (float) (MathHelper.atan2(diff.z, diff.x) * (180.0 / Math.PI)) - 90f;
        float targetPitch = (float) (-(MathHelper.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)) * (180.0 / Math.PI)));

        float yaw = p.getYaw();
        float pitch = p.getPitch();
        float errYaw = MathHelper.wrapDegrees(targetYaw - yaw);
        float errPitch = targetPitch - pitch;
        float err = (float) Math.sqrt(errYaw * errYaw + errPitch * errPitch);

        double spd = speed.get();
        double damping = damp.get();
        double acc = accel.get();

        if (learn.get() && learnSamples > 40) {
            double k = learnStrength.get() * Math.min(1.0, learnSamples / 280.0);
            spd = spd * (1.0 - k) + learnedSpeed * k;
            damping = damping * (1.0 - k) + learnedDamp * k;
        }

        // режим
        if (mode.is("Легит")) {
            spd *= 0.55;
            damping = Math.min(0.92, damping + 0.08);
            acc *= 0.7;
        } else if (mode.is("Сайлент")) {
            spd *= 0.22;
            damping = Math.min(0.94, damping + 0.1);
        }

        // ближе к центру — медленнее (как живой)
        float near = (float) sens.get();
        if (err < 12f) spd *= MathHelper.lerp(err / 12f, near, 1.0);
        if (err < 2.5f) spd *= 0.45;

        // дистанция
        spd *= MathHelper.clamp(1.15 - dist / range.get(), 0.4, 1.0);

        // пружина: ускоряемся к ошибке, гасим скорость
        float forceYaw = (float) (errYaw * spd * acc);
        float forcePitch = (float) (errPitch * spd * acc);
        velYaw = (float) ((velYaw + forceYaw) * damping);
        velPitch = (float) ((velPitch + forcePitch) * damping);

        float cap = (float) maxStep.get();
        if (learnSamples > 250) cap = Math.min(cap + 1.0f, 7.0f);
        velYaw = MathHelper.clamp(velYaw, -cap, cap);
        velPitch = MathHelper.clamp(velPitch, -cap, cap);

        // мёртвая зона — не дрожим
        if (Math.abs(errYaw) < 0.2f) velYaw = 0;
        if (Math.abs(errPitch) < 0.2f) velPitch = 0;

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

            double score = ang * 1.8 + d;
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
        int w = 168;
        int h = 122;
        RenderUtils.drawRoundedRect(c, x, y, x + w, y + h, 6, 0xAA0C0C14);
        Fonts.draw(c, "НейроАим", x + 8, y + 6, 0xFFFFFFFF);
        Fonts.draw(c, mode.get() + " · " + hitbox.get(), x + 8, y + 17, 0xFF8F86FF);

        String big = String.valueOf(learnSamples);
        MatrixStack ms = c.getMatrices();
        ms.push();
        ms.translate(x + 8, y + 30, 0);
        ms.scale(2.0f, 2.0f, 1f);
        Fonts.draw(c, big, 0, 0, RenderUtils.lerp(0xFFFFFFFF, 0xFF4CE08A, flash));
        ms.pop();
        Fonts.draw(c, "сэмплов", x + 8 + Fonts.width(big) * 2 + 6, y + 40, 0xFF6E6E86);

        float mat = (float) Math.min(1.0, learnSamples / 400.0);
        bar(c, x + 8, y + 58, w - 16, mat, "Обученность");
        bar(c, x + 8, y + 74, w - 16, (float) MathHelper.clamp(learnedDamp, 0, 1), "Плавность стиля");
        Fonts.draw(c, why, x + 8, y + 98, 0xFFE0C070);
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