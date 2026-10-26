package ru.bogempty.client.module.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.bogempty.client.ai.SharedNeuralNetwork;
import ru.bogempty.client.friend.FriendManager;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class NeuralAimAssist extends Module {

    private final Setting.Mode mode = add(new Setting.Mode("Режим", "Ассист", "Легит", "Сайлент", "Только обучение"),
            "Ассист — обычный. Легит — медленнее и мягче. Сайлент — совсем слабо. Только обучение — не крутит, только учится на тебе.");
    private final Setting.Mode activation = add(new Setting.Mode("Активация", "Всегда", "При ЛКМ"),
            "Всегда — ведёт, пока есть цель. При ЛКМ — только пока зажата атака.");
    private final Setting.Mode targets = add(new Setting.Mode("Цели", "Игроки", "Все"),
            "На кого вести.");
    private final Setting.Mode hitbox = add(new Setting.Mode("Точка", "Умная", "Ближайшая", "Грудь", "Голова", "Ноги"),
            "Умная — ближайшая точка корпуса и головы, на теле стоит. Ближайшая — любая часть тела. Остальные — фиксированная точка.");
    private final Setting.Mode priority = add(new Setting.Mode("Приоритет", "Угол", "Дистанция", "Здоровье"),
            "Кого выбирать из нескольких: ближайшего к прицелу, к тебе или с наименьшим ХП.");
    private final Setting.Num leadMs = add(new Setting.Num("Упреждение мс", 70, 0, 200, 5),
            "Насколько вперёд по движению цели смещается точка. 0 — без упреждения.");
    private final Setting.Num track = add(new Setting.Num("Слежение %", 80, 0, 100, 5),
            "Насколько прицел повторяет движение цели по углу. Больше — цель держится ровнее.");
    private final Setting.Num fov = add(new Setting.Num("FOV", 60, 20, 360, 1),
            "Конус захвата, градусы.");
    private final Setting.Bool showFovCircle = add(new Setting.Bool("Круг FOV", true),
            "Показывать круг зоны захвата вокруг прицела.");
    private final Setting.Num fovCircleRadius = add(new Setting.Num("Радиус круга", 90, 20, 220, 5),
            "Размер круга FOV на экране в пикселях.");
    private final Setting.Num range = add(new Setting.Num("Дистанция", 4.8, 2.0, 6.0, 0.1),
            "Макс. дистанция до цели.");
    private final Setting.Num speed = add(new Setting.Num("Скорость наводки", 9.0, 2.0, 30.0, 0.5),
            "Как быстро подтягивает к цели. Больше — резче, меньше — мягче.");
    private final Setting.Num maxSpeed = add(new Setting.Num("Макс. скорость °/с", 420, 90, 1200, 10),
            "Потолок скорости поворота.");
    private final Setting.Num vertical = add(new Setting.Num("Вертикаль %", 70, 10, 100, 5),
            "Какую долю силы получает наводка вверх-вниз. Меньше — меньше дёрганий по высоте.");
    private final Setting.Num startMs = add(new Setting.Num("Плавный старт мс", 140, 0, 600, 10),
            "За сколько разгоняется после захвата цели.");
    private final Setting.Num deadZone = add(new Setting.Num("Мёртвая зона °", 0.4, 0.0, 3.0, 0.1),
            "Меньше этой ошибки не двигаем (для точек Грудь/Голова/Ноги).");
    private final Setting.Num reactMin = add(new Setting.Num("Реакция от мс", 30, 0, 200, 5),
            "Мин. задержка после захвата цели.");
    private final Setting.Num reactMax = add(new Setting.Num("Реакция до мс", 90, 0, 300, 5),
            "Макс. задержка после захвата цели.");
    private final Setting.Bool respectMouse = add(new Setting.Bool("Уважать мышь", true),
            "Когда крутишь мышь сам, аим уступает и возвращается плавно.");
    private final Setting.Bool learn = add(new Setting.Bool("Учиться на мне", true),
            "Подстраивает скорость под то, как ты сам наводишься на цели.");
    private final Setting.Num learnStrength = add(new Setting.Num("Сила обучения", 0.75, 0.1, 1.0, 0.05),
            "Насколько сильно берётся твой стиль после накопления сэмплов.");
    private final Setting.Bool visibleOnly = add(new Setting.Bool("Только видимые", true),
            "Не вести сквозь стены.");
    private final Setting.Bool ignoreFriends = add(new Setting.Bool("Игнор друзей", true),
            "Не вести на друзей из .friend.");
    private final Setting.Bool resetLearn = add(new Setting.Bool("Сбросить обучение", false),
            "Обнулить выученное (переключатель вернётся сам).");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Independent online-learning profiles by target distance (blocks).
    private static final int PROFILE_COUNT = 3;
    private final double[] profileK = {9.0, 9.0, 9.0};
    private final double[] profileMax = {420.0, 420.0, 420.0};
    private final int[] profileSamples = {0, 0, 0};
    private double learnedK = 9.0;
    private double learnedMax = 420.0;
    private int samples = 0;
    private int sinceSave = 0;
    private int activeProfile = 0;

    private LivingEntity cand = null; 
    private LivingEntity locked = null; 
    private boolean aimActive = false;
    private long lockAt = 0L;
    private long reactUntil = 0L;
    private Vec3d smPoint = null;
    private LivingEntity smTarget = null;

    private LivingEntity velTarget = null;
    private double velX = 0.0, velZ = 0.0;
    private float lastTy, lastTp;
    private boolean hasLastAng = false;
    private double omegaY = 0.0, omegaP = 0.0;

    private long lastNanos = 0L;
    private boolean hasExp = false;
    private float expYaw, expPitch;
    private long lastUserMs = 0L;
    private long lastSampleMs = 0L;

    private String why = "—";

    public NeuralAimAssist() {
        super("NeuralAimAssist", "Плавный аим без дёрганий, учится на твоей мыши", Category.AI);
        load();
    }

    @Override
    public void onDisable() {
        locked = null;
        cand = null;
        smPoint = null;
        hasExp = false;
        lastNanos = 0L;
        save();
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null || !p.isAlive()) {
            locked = null;
            cand = null;
            aimActive = false;
            why = "Нет мира";
            return;
        }

        if (!(p.getMainHandStack().getItem() instanceof SwordItem)) {
            locked = null;
            cand = null;
            aimActive = false;
            smPoint = null;
            hasLastAng = false;
            why = "Нужен меч";
            return;
        }

        if (resetLearn.get()) {
            resetLearn.value = false;
            learnedK = 9.0;
            learnedMax = 420.0;
            samples = 0;
            for (int i = 0; i < PROFILE_COUNT; i++) {
                profileK[i] = 9.0;
                profileMax[i] = 420.0;
                profileSamples[i] = 0;
            }
            save();
            why = "Обучение сброшено";
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

        if (mode.is("Только обучение")) {
            locked = null;
            aimActive = false;
            why = "Только обучение";
            return;
        }
        if (activation.is("При ЛКМ") && !mc.options.attackKey.isPressed()) {
            aimActive = false;
            why = "Жду ЛКМ";
            if (cand == null) locked = null;
            return;
        }
        if (cand == null) {
            locked = null;
            aimActive = false;
            why = "Нет цели";
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

    private void frame(ClientPlayerEntity p, float tickDelta) {
        long nowN = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.min(0.05f, (nowN - lastNanos) / 1.0e9f);
        lastNanos = nowN;
        long now = System.currentTimeMillis();

        if (!(p.getMainHandStack().getItem() instanceof SwordItem)) {
            locked = null;
            cand = null;
            aimActive = false;
            smPoint = null;
            hasLastAng = false;
            return;
        }

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

        if (learn.get() && cand != null && uMag > 0.05 && now - lastSampleMs >= 50) {
            double targetDistance = p.getEyePos().distanceTo(cand.getBoundingBox().getCenter());
            activeProfile = profileForDistance(targetDistance);
            Vec3d eye = p.getCameraPosVec(tickDelta);
            Vec3d look = p.getRotationVec(1.0f);
            float[] err = angles(p, eye, aimPoint(eye, look, cand, tickDelta, Vec3d.ZERO), yaw, pitch);
            double errMag = Math.sqrt(err[0] * err[0] + err[1] * err[1]);
            double spd = uMag / Math.max(dt, 0.004);
            boolean toward = (uy * err[0] + up * err[1]) > 0;
            if (toward && errMag > 4.0 && errMag < 45.0 && spd > 30.0 && spd < 1500.0) {
                double[] nnFeatures = neuralFeatures(errMag, spd, targetDistance, cand);
                SharedNeuralNetwork.get().train(nnFeatures, 1.0, MathHelper.clamp(1.0 - errMag / 60.0, 0.0, 1.0), 0.25);
                int bucket = activeProfile;
                double k = MathHelper.clamp(spd / errMag, 2.0, 40.0);
                // A small adaptive update prevents a single noisy mouse sample from dominating.
                profileK[bucket] = MathHelper.clamp(profileK[bucket] * 0.97 + k * 0.03, 2.0, 40.0);
                double observedCap = MathHelper.clamp(spd, 120.0, 1100.0);
                profileMax[bucket] = MathHelper.clamp(profileMax[bucket] * 0.95 + observedCap * 0.05, 120.0, 1100.0);
                profileSamples[bucket]++;
                learnedK = profileK[bucket];
                learnedMax = profileMax[bucket];
                samples++;
                lastSampleMs = now;
                if (++sinceSave >= 40) save();
            }
        }

        if (!aimActive || locked == null || !locked.isAlive() || locked.isRemoved()) {
            smPoint = null;
            hasLastAng = false;
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

        float[] err = angles(p, eye, smPoint, yaw, pitch);
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

        if (now < reactUntil) {
            why = "Реакция…";
            return;
        }

        double k = speed.get();
        double cap = maxSpeed.get();
        if (mode.is("Легит")) {
            k *= 0.6;
            cap *= 0.65;
        } else if (mode.is("Сайлент")) {
            k *= 0.35;
            cap *= 0.4;
        }
        activeProfile = profileForDistance(p.getEyePos().distanceTo(locked.getBoundingBox().getCenter()));
        int localSamples = profileSamples[activeProfile];
        if (learn.get() && localSamples >= 10) {
            double m = learnStrength.get() * Math.min(1.0, localSamples / 120.0);
            k = MathHelper.lerp(m, k, profileK[activeProfile]);
            cap = MathHelper.lerp(m, cap, profileMax[activeProfile]);
        }
        double distanceNow = p.getEyePos().distanceTo(locked.getBoundingBox().getCenter());
        double[] nn = SharedNeuralNetwork.get().predict(neuralFeatures(mag, Math.sqrt(omegaY * omegaY + omegaP * omegaP), distanceNow, locked));
        double nnInfluence = Math.min(0.20, SharedNeuralNetwork.get().samples() / 500.0 * 0.20);
        k *= 1.0 + (nn[0] - 0.5) * nnInfluence;
        cap *= 1.0 + (nn[1] - 0.5) * nnInfluence;

        double ramp = 1.0;
        double sMs = startMs.get();
        if (sMs > 0) {
            double x = MathHelper.clamp((now - reactUntil) / sMs, 0.0, 1.0);
            ramp = x * x * (3.0 - 2.0 * x);
        }

        double mouseF = 1.0;
        if (respectMouse.get() && now - lastUserMs < 260) {
            double x = (now - lastUserMs) / 260.0;
            mouseF = 0.12 + 0.88 * x * x;
            why = "Мышь: уступаю";
        } else {
            why = "Веду: " + locked.getName().getString();
        }

        boolean nearestType = hitbox.is("Ближайшая") || hitbox.is("Умная");
        if (!nearestType && mag < deadZone.get()) return;
        if (mag < 0.02) return;

        double fac = 1.0 - Math.exp(-k * dt * ramp * mouseF);
        double ff = (track.get() / 100.0) * ramp * mouseF;
        if (mag < 0.25) ff = 0.0;
        float sy = (float) (ey * fac + omegaY * dt * ff);
        float sp = (float) ((ep * fac + omegaP * dt * ff) * (vertical.get() / 100.0));

        double step = Math.sqrt(sy * sy + sp * sp);
        double maxStep = cap * dt * Math.max(0.2, mouseF);
        if (step > maxStep && step > 1e-6) {
            float s = (float) (maxStep / step);
            sy *= s;
            sp *= s;
        }

        p.changeLookDirection(sy / 0.15, sp / 0.15);
        expYaw = p.getYaw();
        expPitch = p.getPitch();
    }

    private Vec3d aimPoint(Vec3d eye, Vec3d look, LivingEntity t, float tickDelta, Vec3d off) {
        Vec3d lp = t.getLerpedPos(tickDelta);
        Box box = t.getBoundingBox().offset(lp.subtract(t.getPos()).add(off));
        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double h = box.maxY - box.minY;

        if (hitbox.is("Голова")) return new Vec3d(cx, box.minY + h * 0.90, cz);
        if (hitbox.is("Грудь")) return new Vec3d(cx, box.minY + h * 0.68, cz);
        if (hitbox.is("Ноги")) return new Vec3d(cx, box.minY + h * 0.22, cz);

        double mxz = Math.min(0.10, (box.maxX - box.minX) * 0.25);
        double lowY = box.minY + (hitbox.is("Умная") ? h * 0.45 : Math.min(0.12, h * 0.1));
        double highY = hitbox.is("Умная") ? box.minY + h * 0.92 : box.maxY - Math.min(0.12, h * 0.1);
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

    private float[] angles(ClientPlayerEntity p, Vec3d eye, Vec3d point, float yaw, float pitch) {
        Vec3d d = point.subtract(eye);
        double hd = Math.sqrt(d.x * d.x + d.z * d.z);
        float ty = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float tp = (float) -Math.toDegrees(Math.atan2(d.y, hd));
        return new float[]{MathHelper.wrapDegrees(ty - yaw), tp - pitch, ty, tp};
    }

    private double[] neuralFeatures(double error, double mouseSpeed, double distance, LivingEntity target) {
        double[] f = new double[SharedNeuralNetwork.INPUTS];
        f[0] = MathHelper.clamp(error / 45.0, 0.0, 1.0);
        f[1] = MathHelper.clamp(mouseSpeed / 1500.0, 0.0, 1.0);
        f[2] = MathHelper.clamp(distance / 6.0, 0.0, 1.0);
        f[3] = MathHelper.clamp(Math.sqrt(velX * velX + velZ * velZ) / 6.0, 0.0, 1.0);
        f[4] = MathHelper.clamp(Math.abs(target.getVelocity().y) / 1.0, 0.0, 1.0);
        f[5] = MathHelper.clamp(Math.abs(omegaY) / 900.0, 0.0, 1.0);
        f[6] = MathHelper.clamp(Math.abs(omegaP) / 900.0, 0.0, 1.0);
        f[7] = MathHelper.clamp(track.get() / 100.0, 0.0, 1.0);
        f[8] = MathHelper.clamp(leadMs.get() / 200.0, 0.0, 1.0);
        f[9] = target.isOnGround() ? 0.0 : 1.0;
        return f;
    }

    private int profileForDistance(double distance) {
        if (distance < 2.5) return 0;
        if (distance < 4.0) return 1;
        return 2;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("bogempty_aim.json");
    }

    private void save() {
        sinceSave = 0;
        try {
            JsonObject o = new JsonObject();
            o.addProperty("k", learnedK);
            o.addProperty("max", learnedMax);
            o.addProperty("samples", samples);
            com.google.gson.JsonArray ks = new com.google.gson.JsonArray();
            com.google.gson.JsonArray maxs = new com.google.gson.JsonArray();
            com.google.gson.JsonArray counts = new com.google.gson.JsonArray();
            for (int i = 0; i < PROFILE_COUNT; i++) {
                ks.add(profileK[i]);
                maxs.add(profileMax[i]);
                counts.add(profileSamples[i]);
            }
            o.add("profileK", ks);
            o.add("profileMax", maxs);
            o.add("profileSamples", counts);
            Files.writeString(file(), GSON.toJson(o));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            if (o.has("k")) learnedK = MathHelper.clamp(o.get("k").getAsDouble(), 2.0, 40.0);
            if (o.has("max")) learnedMax = MathHelper.clamp(o.get("max").getAsDouble(), 120.0, 1100.0);
            if (o.has("samples")) samples = Math.max(0, o.get("samples").getAsInt());
            if (o.has("profileK") && o.getAsJsonArray("profileK").size() >= PROFILE_COUNT) {
                for (int i = 0; i < PROFILE_COUNT; i++) profileK[i] = MathHelper.clamp(o.getAsJsonArray("profileK").get(i).getAsDouble(), 2.0, 40.0);
            }
            if (o.has("profileMax") && o.getAsJsonArray("profileMax").size() >= PROFILE_COUNT) {
                for (int i = 0; i < PROFILE_COUNT; i++) profileMax[i] = MathHelper.clamp(o.getAsJsonArray("profileMax").get(i).getAsDouble(), 120.0, 1100.0);
            }
            if (o.has("profileSamples") && o.getAsJsonArray("profileSamples").size() >= PROFILE_COUNT) {
                for (int i = 0; i < PROFILE_COUNT; i++) profileSamples[i] = Math.max(0, o.getAsJsonArray("profileSamples").get(i).getAsInt());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null) {
            hasExp = false;
            lastNanos = 0L;
            return;
        }
        frame(p, tickDelta);
        if (showFovCircle.get()) {
            drawFovCircle(c, fovCircleRadius.get());
        }
    }

    private void drawFovCircle(DrawContext c, double radius) {
        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;
        int segments = 96;
        int color = 0xB58A5CFF;
        double step = (Math.PI * 2.0) / segments;
        for (int i = 0; i < segments; i++) {
            double a1 = i * step;
            double a2 = (i + 1) * step;
            int x1 = cx + (int) Math.round(Math.cos(a1) * radius);
            int y1 = cy + (int) Math.round(Math.sin(a1) * radius);
            int x2 = cx + (int) Math.round(Math.cos(a2) * radius);
            int y2 = cy + (int) Math.round(Math.sin(a2) * radius);
            // Thin segment made from a few pixels wide; avoids extra rendering dependencies.
            int minX = Math.min(x1, x2);
            int minY = Math.min(y1, y2);
            int maxX = Math.max(x1, x2);
            int maxY = Math.max(y1, y2);
            if (maxX == minX) maxX++;
            if (maxY == minY) maxY++;
            c.fill(minX, minY, maxX + 1, maxY + 1, color);
        }
    }
}