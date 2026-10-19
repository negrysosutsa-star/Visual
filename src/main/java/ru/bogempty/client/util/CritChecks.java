package ru.bogempty.client.util;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public final class CritChecks {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** States where you cannot fall (water, ladder, lava, vehicle...) - no waiting for a fall. */
    public static boolean cantFall() {
        ClientPlayerEntity p = mc.player;
        if (p == null) return false;
        return p.isTouchingWater() || p.isSwimming() || p.isClimbing()
                || p.isInsideWall() || p.isInLava() || p.hasVehicle();
    }

    public static boolean heavyWeapon() {
        if (mc.player == null) return false;
        ItemStack s = mc.player.getMainHandStack();
        return s.isIn(ItemTags.AXES) || s.isOf(Items.TRIDENT) || s.isOf(Items.MACE);
    }

    public static boolean isWeapon(ItemStack s) {
        return !s.isEmpty() && (s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES)
                || s.isOf(Items.TRIDENT) || s.isOf(Items.MACE));
    }

    public static boolean holdingWeapon() {
        if (mc.player == null) return false;
        return isWeapon(mc.player.getMainHandStack()) || isWeapon(mc.player.getOffHandStack());
    }

    public static boolean holding(Item item) {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().isOf(item) || mc.player.getOffHandStack().isOf(item);
    }

    /** Full block two blocks above the player (low ceiling). */
    public static boolean ceilingAbove() {
        if (mc.player == null || mc.world == null) return false;
        BlockPos pos = mc.player.getBlockPos().up(2);
        BlockState st = mc.world.getBlockState(pos);
        return !st.isAir() && st.isFullCube(mc.world, pos);
    }

    /** True when the player is falling fast enough for a crit. speed is positive, e.g. 0.01. */
    public static boolean falling(double speed) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return false;
        if (p.isOnGround() || p.isInLava() || p.isTouchingWater() || p.hasVehicle()) return false;
        double vy = p.getVelocity().y;
        if (ceilingAbove() && vy < -0.01) {
            double height = p.getY() - (p.getBlockPos().down().getY() + 1.0);
            if (height > 0.4) return true;
        }
        return vy < -speed;
    }

    /** The player lands within the next tick (the hit would count as a ground hit). */
    public static boolean aboutToLand() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || p.isOnGround()) return false;
        double vy = p.getVelocity().y;
        if (vy >= 0.0) return false;
        return !mc.world.isSpaceEmpty(p, p.getBoundingBox().stretch(0.0, vy, 0.0));
    }

    /** Jumping out of water. */
    public static boolean waterJump() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.options == null) return false;
        if (!p.isTouchingWater() || p.isSubmergedInWater() || p.isSwimming()) return false;
        return mc.options.jumpKey.isPressed() && (p.isOnGround() || p.getVelocity().y > 0.0);
    }

    /** Default cooldown threshold depending on state and weapon. */
    public static float defaultCooldown() {
        ClientPlayerEntity p = mc.player;
        if (p == null) return 0.8f;
        boolean heavy = heavyWeapon();
        if (!p.isOnGround() && !cantFall()) return heavy ? 0.85f : 0.75f;
        return heavy ? 0.95f : 0.8f;
    }

    public static void attack(Entity e) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.attackEntity(mc.player, e);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}