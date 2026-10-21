package ru.bogempty.client.module.combat;

import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;
import ru.bogempty.client.util.CritChecks;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class TriggerBot extends Module {
    private final Setting.Mode mode = add(new Setting.Mode("Mode", "Smart", "Only crits"),
            "Smart: crit in the air, normal hit when standing. Only crits: hit only while falling.");
    private final Setting.Mode targets = add(new Setting.Mode("Targets", "Players", "Mobs", "All"),
            "Which entities to attack.");
    private final Setting.Multi hit = add(new Setting.Multi("Conditions",
            new String[]{"Only with weapon", "Through grass", "Naked", "Invisible", "Only in armor"},
            "Through grass", "Naked", "Invisible"),
            "Extra conditions for attacking.");
    private final Setting.Num range = add(new Setting.Num("Range", 2.9, 1.0, 6.0, 0.1),
            "Maximum attack distance.");
    private final Setting.Bool through = add(new Setting.Bool("Through entities", false),
            "Hit the target through any other living entities.");
    private final Setting.Bool ownCd = add(new Setting.Bool("Own cooldown", false),
            "Ignore the vanilla threshold and use your own cooldown percent.");
    private final Setting.Num cd = add(new Setting.Num("Cooldown %", 80, 0, 100, 1),
            "Minimum weapon readiness for a hit (needs Own cooldown).");
    private final Setting.Num fall = add(new Setting.Num("Fall speed", 0.01, 0.0, 0.5, 0.01),
            "Vertical fall speed from which the fall counts as a crit.");
    private final Setting.Bool noSweep = add(new Setting.Bool("Skip landing hit", false),
            "Skip the last tick of the fall: the server counts such a hit as a ground hit.");
    private final Setting.Bool waterSkip = add(new Setting.Bool("Skip water jump", true),
            "Do not hit while jumping out of water.");
    private final Setting.Bool autoJump = add(new Setting.Bool("Auto jump", false),
            "Jump by itself when the weapon is charged and hit on the way down.");
    private final Setting.Num delay = add(new Setting.Num("Random delay", 0, 0, 150, 5),
            "Random extra delay before a hit, in ms.");

    private int groundTicks = 0;
    private boolean jumpHeld = false;
    private long nextAttack = 0L;

    public TriggerBot() {
        super("TriggerBot", "Attacks when the crosshair is on a target", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        releaseJump();
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
            releaseJump();
            return;
        }

        groundTicks = p.isOnGround() ? groundTicks + 1 : 0;
        if (jumpHeld && !p.isOnGround()) releaseJump();

        if (mc.interactionManager.isBreakingBlock()) return;
        if (CritChecks.holding(Items.SHIELD)) return;
        if (hit.has("Only with weapon") && !CritChecks.holdingWeapon()) return;

        LivingEntity t = findTarget(p);
        if (t == null) {
            releaseJump();
            return;
        }

        if (waterSkip.get() && CritChecks.waterJump()) return;

        boolean cantFall = CritChecks.cantFall();
        boolean falling = CritChecks.falling(fall.get());

        if (!cantFall && p.getVelocity().y > 0.0) return;
        if (falling && !cantFall && noSweep.get() && CritChecks.aboutToLand()) return;

        float cdNow = p.getAttackCooldownProgress(0.5f);

        if (p.getMainHandStack().isOf(Items.MACE)) {
            if (cdNow >= 0.4f && (falling || cantFall)) fire(t);
            return;
        }

        float need = ownCd.get() ? (float) (cd.get() / 100.0) : CritChecks.defaultCooldown();
        if (cdNow < need) return;

        boolean critReady = falling || cantFall;

        if (!critReady) {
            if (mode.is("Only crits")) {
                tryJump(p);
                return;
            }
            if (autoJump.get()) {
                tryJump(p);
                return;
            }
            boolean settled = p.isOnGround() && groundTicks >= 5 && !mc.options.jumpKey.isPressed();
            if (!settled) return;
        }

        if (System.currentTimeMillis() < nextAttack) return;
        fire(t);
    }

    private void fire(LivingEntity t) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.attackEntity(mc.player, t);
        mc.player.swingHand(mc.player.getActiveHand());
        long d = (long) delay.get();
        if (d > 0) nextAttack = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(0, d + 1);
        releaseJump();
    }

    private void tryJump(net.minecraft.client.network.ClientPlayerEntity p) {
        if (!autoJump.get() || jumpHeld || !p.isOnGround()) return;
        mc.options.jumpKey.setPressed(true);
        jumpHeld = true;
    }

    private void releaseJump() {
        if (!jumpHeld) return;
        mc.options.jumpKey.setPressed(false);
        jumpHeld = false;
    }

    private LivingEntity findTarget(net.minecraft.client.network.ClientPlayerEntity p) {
        HitResult hr = mc.crosshairTarget;
        if (hr == null || hr.getType() != HitResult.Type.ENTITY) return null;
        Entity e = ((EntityHitResult) hr).getEntity();
        if (!(e instanceof LivingEntity le)) return null;
        if (!valid(p, le)) return null;
        double dist = p.getEyePos().distanceTo(le.getBoundingBox().getCenter());
        if (dist > range.get()) return null;
        return le;
    }

    private boolean valid(net.minecraft.client.network.ClientPlayerEntity p, LivingEntity le) {
        if (le == p || !le.isAlive() || le.isRemoved()) return false;
        if (le instanceof ArmorStandEntity) return false;

        boolean isPlayer = le instanceof PlayerEntity;
        if (targets.is("Players") && !isPlayer) return false;
        if (targets.is("Mobs") && isPlayer) return false;

        if (hit.has("Invisible") && le.isInvisible()) return false;
        if (isPlayer) {
            PlayerEntity pl = (PlayerEntity) le;
            boolean naked = pl.getArmor() == 0;
            if (hit.has("Naked") && !naked) return false;
            if (hit.has("Only in armor") && naked) return false;
        }
        return true;
    }
}