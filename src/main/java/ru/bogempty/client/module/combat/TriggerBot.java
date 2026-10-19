package ru.bogempty.client.module.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

import java.util.concurrent.ThreadLocalRandom;

public class TriggerBot extends Module {
    private final Setting.Mode target = add(new Setting.Mode("Target", "Players", "Mobs", "Any"));
    private final Setting.Num cooldown = add(new Setting.Num("Cooldown", 0.9, 0.5, 1.0, 0.05));
    private final Setting.Num delay = add(new Setting.Num("Random delay", 0, 0, 150, 5));
    private final Setting.Bool crits = add(new Setting.Bool("Only crits", false));
    private final Setting.Bool weapon = add(new Setting.Bool("Weapon only", true));
    private final Setting.Bool usingItem = add(new Setting.Bool("Skip using item", true));
    private final Setting.Bool invisible = add(new Setting.Bool("Ignore invisible", false));

    private long nextAttack = 0L;

    public TriggerBot() {
        super("TriggerBot", "Attacks when the crosshair is on a target", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;

        Entity e = hit.getEntity();
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e instanceof ArmorStandEntity) return;

        boolean isPlayer = e instanceof PlayerEntity;
        if (target.is("Players") && !isPlayer) return;
        if (target.is("Mobs") && isPlayer) return;
        if (invisible.get() && e.isInvisible()) return;

        if (usingItem.get() && mc.player.isUsingItem()) return;
        if (weapon.get() && !isWeapon(mc.player.getMainHandStack())) return;
        if (mc.player.getAttackCooldownProgress(0.5f) < cooldown.get()) return;
        if (crits.get() && !canCrit()) return;

        long now = System.currentTimeMillis();
        if (now < nextAttack) return;

        mc.interactionManager.attackEntity(mc.player, e);
        mc.player.swingHand(Hand.MAIN_HAND);

        int max = (int) delay.get();
        nextAttack = now + (max > 0 ? ThreadLocalRandom.current().nextInt(0, max + 1) : 0);
    }

    private boolean isWeapon(ItemStack s) {
        return s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES);
    }

    private boolean canCrit() {
        return mc.player.fallDistance > 0.0f
                && !mc.player.isOnGround()
                && !mc.player.isClimbing()
                && !mc.player.isTouchingWater()
                && !mc.player.hasVehicle();
    }
}