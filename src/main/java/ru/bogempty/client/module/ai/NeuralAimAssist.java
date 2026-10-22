package ru.bogempty.client.module.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.bogempty.client.friend.FriendManager;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SilentAim под FunTime (Grim).
 * Не меняет локальный yaw/pitch, а шлёт идеальный угол серверу через хук.
 * Угол нарастает с человеческой скоростью, с овершутами, джиттером и дыханием.
 * Учится на твоей мыши.
 */
public class NeuralAimAssist extends Module {

    private final Setting.Mode mode = add(new Setting.Mode("Режим", "Silent", "Legit", "Off"),
            "Silent — не крутит камеру, но бьёт точно. Legit — крутит камеру, но с хуманизацией. Off — выкл.");
    private final Setting.Mode activation = add(new Setting.Mode("Активация", "При ЛКМ", "Всегда"),
            "При ЛКМ — только пока зажата атака.");
    private final Setting.Mode targets = add(new Setting.Mode("Цели", "Игроки", "Все"),
            "На кого вести.");
    private final Setting.Mode hitbox = add(new Setting.Mode("Точка", "Голова", "Грудь", "Умная"),
            "Куда целиться.");
    private final Setting.Mode priority = add(new Setting.Mode("Приоритет", "Угол", "Дистанция", "Здоровье"),
            "Кого выбирать.");
    private final Setting.Num fov = add(new Setting.Num("FOV", 90, 20, 180, 1),
            "Конус захвата.");
    private final Setting.Num range = add(new Setting.Num("Дистанция", 4.5, 1.0, 6.0, 0.1),
            "Макс. дистанция.");
    private final Setting.Num speed = add(new Setting.Num("Скорость", 1.2, 0.5, 3.0, 0.1),
            "Скорость подтягивания серверного угла. 1.2 — средняя человеческая.");
    private final Setting.Num maxSpeed = add(new Setting.Num("Макс. скорость °/с", 320, 100, 800, 10),
            "Потолок скорости.");
    private final Setting.Num track = add(new Setting.Num("Слежение %", 75, 0, 100, 5),
            "Насколько серверный угол повторяет движение цели.");
    private final Setting.Num leadMs = add(new Setting.Num("Упреждение мс", 60, 0, 200, 5),
            "Упреждение по скорости цели.");
    private final Setting.Num reactMin = add(new Setting.Num("Реакция от мс", 90, 30, 250, 5),
            "Минимальная задержка (как у человека).");
    private final Setting.Num reactMax = add(new Setting.Num("Реакция до мс", 180, 80, 350, 5),
            "Максимальная задержка.");
    private final Setting.Num jitter = add(new Setting.Num("Джиттер °", 0.12, 0.0, 1.0, 0.01),
            "Микротряска серверного угла. 0.12 — едва заметно, 0.5 — как уставший человек.");
    private final Setting.Num overshoot = add(new Setting.Num("Овершут °", 1.5, 0.0, 8.0, 0.1),
            "Случайный перелёт и возврат. Должно быть больше 0, иначе FunTime ловит ровный трекинг.");
    private final Setting.Num breathing = add(new Setting.Num("Дыхание °", 0.15, 0.0, 1.5, 0.05),
            "Низкочастотное покачивание прицела, как у живого.");
    private final Setting.Bool visibleOnly = add(new Setting.Bool("Только видимые", true),
            "Не вести сквозь стены.");
    private final Setting.Bool ignoreFriends = add(new Setting.Bool("Игнор друзей", true),
            "Не вести на друзей.");
    private final Setting.Bool learn = add(new Setting.Bool("Учиться на мне", true),
            "Подстраивает скорость и джиттер под твой стиль.");
    private final Setting.Num learnStrength = add(new Setting.Num("Сила обучения", 0.75, 0.1, 1.0, 0.05),
            "Как сильно брать твой стиль.");
    private final Setting.Bool resetLearn = add(new Setting.Bool("Сбросить обучение", false),
            "Обнулить выученное.");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // выученное
    private double learnedK = 1.2;
    private double learnedJitter = 0.12;
    private double learnedOvershoot = 1.5;
    private int samples = 0;
    private int sinceSave = 0;

    // цель
    private LivingEntity cand = null;
    private LivingEntity locked = null;
    private boolean aimActive = false;
    private long lockAt = 0L;
    private long reactUntil = 0L;
    private Vec3d smPoint = null;
    private LivingEntity smTarget = null;

    // кинематика цели
    private LivingEntity velTarget = null;
    private double velX = 0.0, velZ = 0.0;
    private float lastTy, lastTp;
    private boolean hasLastAng = false;
    private double omegaY = 0.0, omegaP = 0.0;

    // кадр
    private long lastNanos = 0L;
    private boolean hasExp = false;
    private float expYaw, expPitch;
    private long lastUserMs = 0L;
    private long lastSampleMs = 0L;

    // silent aim: серверный угол (для хука)
    private float silentYaw = 0f;
    private float silentPitch = 0f;
    private boolean hasSilent = false;

    // овершут / дыхание
    private float overY = 0f, overP = 0f;
    private long overUntil = 0L;
    private double breatheT = 0.0;
    private double breatheYaw = 0.0, breathePitch = 0.0;

    public NeuralAimAssist() {
        super("NeuralAimAssist", "Silent aim под FunTime с хуманизацией", Category.AI);
        load();
    }

    @Override
    public void onDisable() {
        locked = null;
        cand = null;
        smPoint = null;
        hasExp = false;
        hasSilent = false;
        lastNanos = 0L;
        save();
    }

    /**
     * Вызывается из миксина на ClientPlayNetworkHandler.sendPacket.
     * Если возвращает true — пакет нужно подменить на silentYaw/silentPitch.
     */
    public boolean getSilentAngles(float[] out) {
        if (!hasSilent || locked == null || !aimActive) return false;
        out[0] = silentYaw;
        out[1] = silentPitch;
        return true;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null || !p.isAlive()) {
            locked = null;
            cand = null;
            aimActive = false;
            hasSilent = false;
            return;
        }

        if (resetLearn.get()) {
            resetLearn.value = false;
            learnedK = 1.2;
            learnedJitter = 0.12;
            learnedOvershoot = 1.5;
            samples = 0;
            save();
        }

        long now = System.currentTimeMillis();

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le)) continue;
            double s = score(p, le, 1.0, 1.0);
            if (s >= 0 && s < bestScore) {
                bestScore = s;
                best = le;
            }
        }
        if (cand != null && cand != best) {
            double cs = score(p, cand, 1.15, 1.25);
            if (cs >= 0 && (best == null || bestScore > cs * 0.6) && now - lockAt > 350) {
                best = cand;
            }
        }
        if (best != cand) {
            cand = best;
            lockAt = now;
        }

        if (mode.is("Off")) {
            locked = null;
            aimActive = false;
            hasSilent = false;
            return;
        }
        if (activation.is("При ЛКМ") && !mc.options.attackKey.isPressed()) {
            aimActive = false;
            hasSilent = false;
            return;
        }
        if (cand == null) {
            locked = null;
            aimActive = false;
            hasSilent = false;
            return;
        }

        if (cand != locked) {
            locked = cand;
            int rmin = (int) reactMin.get();
            int rmax = Math.max(rmin, (int) reactMax.get());
            reactUntil = now + ThreadLocalRandom.current().nextInt(rmin, rmax + 1);
            smPoint = null;
        }
        aimActive = true;

        double dx = (locked.getX() - locked.prevX) * 20.0;
        double dz = (locked.getZ() - locked.prevZ) * 20.0;
        if (velTarget != locked) {
            velTarget = locked;
            velX = dx;
            velZ = dz;
        } else {
            velX += (dx - velX) * 0.35;
            velZ += (dz - velZ) * 0.35;
        }
    }

    private double score(ClientPlayerEntity p, LivingEntity le, double rangeMul, double fovMul) {
        if (le == p || !le.isAlive() || le.isSpectator() || le instanceof ArmorStandEntity) return -1;
        if (le instanceof PlayerEntity pl) {
            if (ignoreFriends.get() && FriendManager.isFriend(pl.getName().getString())) return -1;
        } else if (!targets.is("Все")) {
            return -1;
        }
        Vec3d eye = p.getEyePos();
        Vec3d center = le.getBoundingBox().getCenter();
        double dist = eye.distanceTo(center);
        if (dist > range.get() * rangeMul) return -1;

        Vec3d dir = center.subtract(eye).normalize();
        double dot = MathHelper.clamp(p.getRotationVec(1.0f).dotProduct(dir), -1.0, 1.0);
        double ang = Math.toDegrees(Math.acos(dot));
        if (ang > fov.get() * 0.5 * fovMul) return -1;
        if (visibleOnly.get() && !p.canSee(le)) return -1;
        if (priority.is("Дистанция")) return dist + ang * 0.05;
        if (priority.is("Здоровье")) return le.getHealth() + ang * 0.1;
        return ang * 1.8 + dist;
    }

    // Вызывается из миксина рендера каждый кадр
    public void frame(ClientPlayerEntity p, float tickDelta) {
        long nowN = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.min(0.05f, (nowN - lastNanos) / 1.0e9f);
        lastNanos = nowN;
        long now = System.currentTimeMillis();

        float yaw = p.getYaw();
        float pitch = p.getPitch();

        float uy = 0f, up = 0f;
        if (hasExp) {
            uy = MathHelper.wrapDegrees(yaw - expYaw);
            up = pitch - expPitch;
        }
        double uMag = Math.sqrt(uy * uy + up * up);
        if (uMag > 0.02) lastUserMs = now;
        expYaw = yaw;
        expPitch = pitch;
        hasExp = true;

        // обучение: как быстро ты сам тянешь мышь, насколько сильно дёргаешь
        if (learn.get() && cand != null && uMag > 0.05 && now - lastSampleMs >= 50) {
            Vec3d eye = p.getCameraPosVec(tickDelta);
            Vec3d look = p.getRotationVec(1.0f);
            float[] err = angles(eye, aimPoint(eye, look, cand, tickDelta, Vec3d.ZERO), yaw, pitch);
            double errMag = Math.sqrt(err[0] * err[0] + err[1] * err[1]);
            double spd = uMag / Math.max(dt, 0.004);
            boolean toward = (uy * err[0] + up * err[1]) > 0;
            if (toward && errMag > 4.0 && errMag < 45.0 && spd > 30.0 && spd < 1500.0) {
                double k = MathHelper.clamp(spd / errMag, 0.5, 3.0);
                learnedK = learnedK * 0.97 + k * 0.03;
                // учим джиттер: чем больше ты дёргаешь мышь, тем больше будет джиттер
                learnedJitter = MathHelper.clamp(learnedJitter * 0.95 + (uMag * 0.02) * 0.05, 0.05, 0.8);
                samples++;
                lastSampleMs = now;
                if (++sinceSave >= 200) save();
            }
        }

        if (!aimActive || locked == null || !locked.isAlive() || locked.isRemoved() || mode.is("Off")) {
            smPoint = null;
            hasLastAng = false;
            hasSilent = false;
            return;
        }

        Vec3d eye = p.getCameraPosVec(tickDelta);
        Vec3d look = p.getRotationVec(1.0f);

        double lead = leadMs.get() / 1000.0;
        Vec3d off = new Vec3d(velX * lead, 0.0, velZ * lead);
        double ol = off.length();
        if (ol > 1.2) off = off.multiply(1.2 / ol);

        Vec3d raw = aimPoint(eye, look, locked, tickDelta, off);
        if (smTarget != locked || smPoint == null) {
            smTarget = locked;
            smPoint = raw;
            hasLastAng = false;
            omegaY = 0.0;
            omegaP = 0.0;
        } else {
            double a = 1.0 - Math.exp(-28.0 * dt);
            smPoint = smPoint.add(raw.subtract(smPoint).multiply(a));
        }

        float[] err = angles(eye, smPoint, yaw, pitch);
        float ey = err[0];
        float ep = err[1];
        float ty = err[2];
        float tp = err[3];

        if (hasLastAng) {
            double wy = MathHelper.wrapDegrees(ty - lastTy) / Math.max(dt, 0.004);
            double wp = (tp - lastTp) / Math.max(dt, 0.004);
            double k2 = 1.0 - Math.exp(-dt / 0.06);
            omegaY += (MathHelper.clamp(wy, -900.0, 900.0) - omegaY) * k2;
            omegaP += (MathHelper.clamp(wp, -900.0, 900.0) - omegaP) * k2;
        }
        lastTy = ty;
        lastTp = tp;
        hasLastAng = true;

        double mag = Math.sqrt(ey * ey + ep * ep);

        if (now < reactUntil) return;

        double k = speed.get();
        double cap = maxSpeed.get();
        if (mode.is("Legit")) {
            k *= 0.6;
            cap *= 0.65;
        }
        if (learn.get() && samples >= 60) {
            double m = learnStrength.get() * Math.min(1.0, samples / 300.0);
            k = MathHelper.lerp(m, k, learnedK);
        }

        // хуманизация: овершут
        if (now > overUntil && mag > 3.0 && ThreadLocalRandom.current().nextDouble() < 0.02) {
            float os = (float) (overshoot.get() * (0.5 + ThreadLocalRandom.current().nextDouble()));
            overY = (float) (Math.signum(ey) * os * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1));
            overP = (float) (Math.signum(ep) * os * 0.5 * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1));
            overUntil = now + 80 + ThreadLocalRandom.current().nextInt(100);
        }
        if (now > overUntil) {
            overY *= 0.8f;
            overP *= 0.8f;
        }

        // хуманизация: дыхание
        breatheT += dt;
        breatheYaw = Math.sin(breatheT * 0.7) * breathing.get() * 0.5;
        breathePitch = Math.cos(breatheT * 0.9) * breathing.get() * 0.3;

        double fac = 1.0 - Math.exp(-k * dt);
        double ff = (track.get() / 100.0);
        if (mag < 0.25) ff = 0.0;
        float sy = (float) (ey * fac + omegaY * dt * ff + overY + breatheYaw);
        float sp = (float) (ep * fac + omegaP * dt * ff * 0.7 + overP + breathePitch);

        // хуманизация: джиттер
        double jit = jitter.get();
        if (learn.get() && samples >= 60) {
            double m = learnStrength.get() * Math.min(1.0, samples / 300.0);
            jit = MathHelper.lerp(m, jit, learnedJitter);
        }
        sy += (float) ((ThreadLocalRandom.current().nextGaussian() * jit));
        sp += (float) ((ThreadLocalRandom.current().nextGaussian() * jit * 0.7));

        double step = Math.sqrt(sy * sy + sp * sp);
        double maxStep = cap * dt;
        if (step > maxStep && step > 1e-6) {
            float s = (float) (maxStep / step);
            sy *= s;
            sp *= s;
        }

        // обновляем серверный угол
        if (hasSilent) {
            silentYaw = MathHelper.wrapDegrees(silentYaw + sy);
            silentPitch = MathHelper.clamp(silentPitch + sp, -90f, 90f);
        } else {
            silentYaw = MathHelper.wrapDegrees(yaw + sy);
            silentPitch = MathHelper.clamp(pitch + sp, -90f, 90f);
            hasSilent = true;
        }

        // в Legit-режиме ещё и крутим камеру локально
        if (mode.is("Legit")) {
            p.changeLookDirection(sy / 0.15, sp / 0.15);
            expYaw = p.getYaw();
            expPitch = p.getPitch();
        }
    }

    private Vec3d aimPoint(Vec3d eye, Vec3d look, LivingEntity t, float tickDelta, Vec3d off) {
        Vec3d lp = t.getLerpedPos(tickDelta);
        Box box = t.getBoundingBox().offset(lp.subtract(t.getPos()).add(off));
        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double h = box.maxY - box.minY;

        if (hitbox.is("Голова")) return new Vec3d(cx, box.minY + h * 0.90, cz);
        if (hitbox.is("Грудь")) return new Vec3d(cx, box.minY + h * 0.68, cz);

        double mxz = Math.min(0.10, (box.maxX - box.minX) * 0.25);
        double lowY = box.minY + h * 0.45;
        double highY = box.minY + h * 0.92;
        Box inner = new Box(box.minX + mxz, lowY, box.minZ + mxz, box.maxX - mxz, highY, box.maxZ - mxz);
        Optional<Vec3d> hit = inner.raycast(eye, eye.add(look.multiply(8.0)));
        if (hit.isPresent()) return hit.get();

        double d = eye.distanceTo(box.getCenter());
        Vec3d ray = eye.add(look.multiply(d));
        return new Vec3d(
                MathHelper.clamp(ray.x, inner.minX, inner.maxX),
                MathHelper.clamp(ray.y, inner.minY, inner.maxY),
                MathHelper.clamp(ray.z, inner.minZ, inner.maxZ));
    }

    private float[] angles(Vec3d eye, Vec3d point, float yaw, float pitch) {
        Vec3d d = point.subtract(eye);
        double hd = Math.sqrt(d.x * d.x + d.z * d.z);
        float ty = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float tp = (float) -Math.toDegrees(Math.atan2(d.y, hd));
        return new float[]{MathHelper.wrapDegrees(ty - yaw), tp - pitch, ty, tp};
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("bogempty_aim.json");
    }

    private void save() {
        sinceSave = 0;
        try {
            JsonObject o = new JsonObject();
            o.addProperty("k", learnedK);
            o.addProperty("jitter", learnedJitter);
            o.addProperty("overshoot", learnedOvershoot);
            o.addProperty("samples", samples);
            Files.writeString(file(), GSON.toJson(o));
        } catch (Exception ignored) {
        }
    }

    private void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            if (o.has("k")) learnedK = MathHelper.clamp(o.get("k").getAsDouble(), 0.5, 3.0);
            if (o.has("jitter")) learnedJitter = MathHelper.clamp(o.get("jitter").getAsDouble(), 0.05, 0.8);
            if (o.has("overshoot")) learnedOvershoot = MathHelper.clamp(o.get("overshoot").getAsDouble(), 0.5, 5.0);
            if (o.has("samples")) samples = o.get("samples").getAsInt();
        } catch (Exception ignored) {
        }
    }

    // Вызывается из миксина рендера
    public void onRender(ClientPlayerEntity p, float tickDelta) {
        if (p == null || mc.world == null || mc.currentScreen != null) {
            hasExp = false;
            lastNanos = 0L;
            return;
        }
        frame(p, tickDelta);
    }
}