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
            "Record your mouse deltas when you aim at players.");
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
    private float lastYaw, lastPitch;

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

        if (learn.get()) samplePlayerAim(p);
        if (mode.is("Train only")) return;

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

        if (now < reactUntil) return;
        if (ThreadLocalRandom.current().nextDouble() * 100 < missChance.get()) return;

        aimAt(p, t);
    }

    private void samplePlayerAim(ClientPlayerEntity p) {
        float dy = MathHelper.wrapDegrees(p.getYaw() - lastYaw);
        float dp = p.getPitch() - lastPitch;
        lastYaw = p.getYaw();
        lastPitch = p.getPitch();

        double mag = Math.sqrt(dy * dy + dp * dp);
        if (mag < 0.05 || mag > 25) return;

        double s = MathHelper.clamp(mag / 18.0, 0.08, 1.0);
        learnedSpeed = learnedSpeed * 0.98 + s * 0.02;
        learnedSmooth = learnedSmooth * 0.99 + MathHelper.clamp(1.0 - mag / 30.