package ru.bogempty.client.module.combat;

import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;
import ru.bogempty.client.util.CritChecks;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

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
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
            releaseJump();
            return;
        }

        groundTicks = p.isOnGround() ? groundTicks + 1 : 0;
        if (jumpHeld && !p.isOnGround()) releaseJump();

        if (mc.interactionManager.isBreakingBlock()) return;
        if (CritChecks.holding(Items.CROSSBOW)) return;
        if (hit.has("Only with weapon") && !CritChecks.holdingWeapon()) return;

        LivingEntity t = findTarget(p);
        if (t == null) {
            releaseJump();
            return;
        }

        if (waterSkip.get() && CritChecks.waterJump()) return;

        boolean cantFall = CritChecks.cantFall();
        boolean falling = CritChecks.falling(fall.get());

        // rising: wait for the descent
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
            // Smart
            if (autoJump.get()) {
                tryJump(p);
                return;
            }
            boolean settled = p.isOnGround() && groundTicks >= 5 && !mc.options.jumpKey.isPressed();
            if (!settled) return;
        }

        fire(t);
    }

    private void fire(LivingEntity t) {
        long now = System.currentTimeMillis();
        if (now < nextAttack) return;
        CritChecks.attack(t);
        releaseJump();
        int d = (int) delay.get();
        nextAttack = now + (d > 0 ? ThreadLocalRandom.current().nextInt(d + 1) : 0);
    }

    private void tryJump(ClientPlayerEntity p) {
        if (!autoJump.get() || jumpHeld) return;
        if (p.isOnGround() && groundTicks >= 2 && !mc.options.jumpKey.isPressed()) {
            mc.options.jumpKey.setPressed(true);
            jumpHeld = true;
        }
    }

    private void releaseJump() {
        if (jumpHeld) {
            if (mc.options != null) mc.options.jumpKey.setPressed(false);
            jumpHeld = false;
        }
    }

    // ---------- targeting ----------
    private LivingEntity findTarget(ClientPlayerEntity p) {
        double r = range.get();
        Vec3d eye = p.getCameraPosVec(1.0f);
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d end = eye.add(look.multiply(r));
        double maxDist = blockDistance(p, eye, end, r);
        Box box = p.getBoundingBox().stretch(look.multiply(r)).expand(1.0);

        if (through.get()) {
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            double lim = (maxDist + 0.1) * (maxDist + 0.1);
            for (LivingEntity le : mc.world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e != p && e.isAlive() && !e.isSpectator() && e.canHit())) {
                if (!valid(p, le)) continue;
                Box bb = le.getBoundingBox().expand(le.getTargetingMargin());
                double d;
                if (bb.contains(eye)) {
                    d = 0.0;
                } else {
                    Optional<Vec3d> o = bb.raycast(eye, end);
                    if (o.isEmpty()) continue;
                    d = eye.squaredDistanceTo(o.get());
                }
                if (d <= lim && d < bestD) {
                    bestD = d;
                    best = le;
                }
            }
            return best;
        }

        EntityHitResult res = ProjectileUtil.raycast(p, eye, end, box,
                e -> !e.isSpectator() && e.canHit() && e != p, r * r);
        if (res == null || !(res.getEntity() instanceof LivingEntity le)) return null;
        if (Math.sqrt(eye.squaredDistanceTo(res.getPos())) > maxDist + 0.1) return null;
        return valid(p, le) ? le : null;
    }

    private double blockDistance(ClientPlayerEntity p, Vec3d eye, Vec3d end, double r) {
        BlockHitResult bhr = mc.world.raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
        if (bhr.getType() != HitResult.Type.BLOCK) return r;
        BlockState st = mc.world.getBlockState(bhr.getBlockPos());
        boolean soft = st.getCollisionShape(mc.world, bhr.getBlockPos()).isEmpty();
        if (hit.has("Through grass") && soft) return r;
        return Math.sqrt(eye.squaredDistanceTo(bhr.getPos()));
    }

    private boolean valid(ClientPlayerEntity p, LivingEntity le) {
        if (le == p || !le.isAlive() || le instanceof ArmorStandEntity) return false;
        boolean isPlayer = le instanceof PlayerEntity;
        if (targets.is("Players") && !isPlayer) return false;
        if (targets.is("Mobs") && !(le instanceof MobEntity)) return false;
        if (le.isInvisible() && !hit.has("Invisible")) return false;
        boolean armored = hasArmor(le);
        if (hit.has("Only in armor") && !armored) return false;
        return armored || hit.has("Naked");
    }

    private boolean hasArmor(LivingEntity le) {
        return !le.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                || !le.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                || !le.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                || !le.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }
}