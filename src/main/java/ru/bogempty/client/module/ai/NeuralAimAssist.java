package ru.bogempty.client.module.ai;

import ru.bogempty.client.friend.FriendManager;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class NeuralAimAssist extends Module {

    private final Setting.Mode mode = add(new Setting.Mode("Mode", "Assist", "Silent", "Train only"),
            "Assist: soft aim. Silent: almost no visible move (riskier). Train only: only learns from you.");
    private final Setting.Mode targets = add(new Setting.Mode("Targets", "Players", "All"),
            "Who to aim at.");
    private final Setting.Num fov = add(new Setting.Num("FOV", 48, 10, 120, 1),
            "Only targets inside this FOV.");
    private final Setting.Num range = add(new Setting.Num("Range", 4.2, 2.0, 6.0, 0.1),
            "Max distance.");
    private final Setting.Num speed = add(new Setting.Num("Speed", 0.35, 0.05, 1.0, 0.01),
            "Base aim speed (lower = more human).");
    private final Setting.Num smooth = add(new Setting.Num("Smooth", 0.55, 0.1, 0.95, 0.01),
            "Smoothing factor.");
    private final Setting.Num jitter = add(new Setting.Num("Jitter", 0.12, 0.0, 0.5, 0.01),
            "Micro random noise on aim.");
    private final Setting.Num missChance = add(new Setting.Num("Miss %", 8, 0, 40, 1),
            "Chance to intentionally not fully lock this tick.");
    private final Setting.Num reactMin = add(new Setting.Num("React min ms", 40, 0, 200, 5),
            "Min reaction delay after target enters FOV.");
    private final Setting.Num reactMax = add(new Setting.Num("React max ms", 120, 0, 350, 5),
            "Max reaction delay.");
    private final Setting.Bool learn = add(new Setting.Bool("Learn from me", true),
            "Record your mouse deltas when you aim.");
    private final Setting.Num learnStrength = add(new Setting.Num("Learn strength", 0.4, 0.05, 1.0, 0.05),
            "How strongly your style overrides base speed.");
    private final Setting.Bool visibleOnly = add(new Setting.Bool("Visible only", true),
            "Do not aim through walls.");
    private final Setting.Bool ignoreFriends = add(new Setting.Bool("Ignore friends", true),
            "Skip friends.");

    private double learnedSpeed = 0.35;
    private double learnedSmooth = 0.55;
    private int learnSamples = 0;

    private LivingEntity locked = null;
    private long seeSince = 0L;
    private long reactUntil = 0L;
    private float lastYaw;
    private float lastPitch;

    public NeuralAimAssist() {
        super("NeuralAimAssist", "Human-like aim assist with style learning", Category.AI);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null) {
            locked = null;
            return;
        }

        if (learn.get()) {
            samplePlayerAim(p);
        }
        if (mode.is("Train only")) {
            return;
        }

        LivingEntity t = findTarget(p);
        long now = System.currentTimeMillis();

        if (t == null) {
            locked = null;
            return;
        }

        if (t != locked) {
            locked = t;
            seeSince = now;
            int rmin = (int) reactMin.get();
            int rmax = Math.max(rmin, (int) reactMax.get());
            reactUntil = now + ThreadLocalRandom.current().nextInt(rmin, rmax + 1);
        }

        if (now < reactUntil) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() * 100.0 < missChance.get()) {
            return;
        }

        aimAt(p, t);
    }

    private void samplePlayerAim(ClientPlayerEntity p) {
        float dy = MathHelper.wrapDegrees(p.getYaw() - lastYaw);
        float dp = p.getPitch() - lastPitch;
        lastYaw = p.getYaw();
        lastPitch = p.getPitch();

        double mag = Math.sqrt(dy * dy + dp * dp);
        if (mag < 0.05 || mag > 25.0) {
            return;
        }

        double s = MathHelper.clamp(mag / 18.0, 0.08, 1.0);
        learnedSpeed = learnedSpeed * 0.98 + s * 0.02;
        learnedSmooth = learnedSmooth * 0.99 + MathHelper.clamp(1.0 - mag / 30.0, 0.2, 0.9) * 0.01;
        learnSamples++;
    }

    private void aimAt(ClientPlayerEntity p, LivingEntity t) {
        Vec3d eye = p.getEyePos();
        Vec3d point = t.getBoundingBox().getCenter().add(0.0, t.getHeight() * 0.15, 0.0);
        Vec3d diff = point.subtract(eye);
        double dist = diff.length();
        if (dist < 1e-4) {
            return;
        }

        float targetYaw = (float) (MathHelper.atan2(diff.z, diff.x) * (180.0 / Math.PI)) - 90.0f;
        float targetPitch = (float) (-(MathHelper.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)) * (180.0 / Math.PI)));

        float yaw = p.getYaw();
        float pitch = p.getPitch();
        float dYaw = MathHelper.wrapDegrees(targetYaw - yaw);
        float dPitch = targetPitch - pitch;

        double spd = speed.get();
        double sm = smooth.get();
        if (learn.get() && learnSamples > 50) {
            double k = learnStrength.get();
            spd = spd * (1.0 - k) + learnedSpeed * k;
            sm = sm * (1.0 - k) + learnedSmooth * k;
        }

        double distFactor = MathHelper.clamp(1.2 - dist / range.get(), 0.35, 1.0);
        spd *= distFactor;

        long hold = System.currentTimeMillis() - seeSince;
        if (hold > 2500L) {
            spd *= 0.85;
        }

        float stepYaw = (float) (dYaw * (1.0 - sm) * spd);
        float stepPitch = (float) (dPitch * (1.0 - sm) * spd);

        double j = jitter.get();
        if (j > 0.0) {
            stepYaw += (ThreadLocalRandom.current().nextDouble() - 0.5) * j;
            stepPitch += (ThreadLocalRandom.current().nextDouble() - 0.5) * j * 0.6;
        }

        if (mode.is("Silent")) {
            stepYaw *= 0.25f;
            stepPitch *= 0.25f;
        }

        p.setYaw(yaw + stepYaw);
        p.setPitch(MathHelper.clamp(pitch + stepPitch, -90.0f, 90.0f));
    }

    private LivingEntity findTarget(ClientPlayerEntity p) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d eye = p.getEyePos();

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || le == p || !le.isAlive()) {
                continue;
            }

            if (le instanceof PlayerEntity pl) {
                if (!(targets.is("Players") || targets.is("All"))) {
                    continue;
                }
                if (ignoreFriends.get() && FriendManager.isFriend(pl.getName().getString())) {
                    continue;
                }
            } else if (!targets.is("All")) {
                continue;
            }

            double d = eye.distanceTo(le.getBoundingBox().getCenter());
            if (d > range.get()) {
                continue;
            }

            Vec3d diff = le.getBoundingBox().getCenter().subtract(eye).normalize();
            Vec3d look = p.getRotationVec(1.0f);
            double dot = look.dotProduct(diff);
            double ang = Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1.0, 1.0)));
            if (ang > fov.get() / 2.0) {
                continue;
            }

            if (visibleOnly.get() && !p.canSee(le)) {
                continue;
            }

            double score = ang * 2.0 + d;
            if (score < bestScore) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }
}