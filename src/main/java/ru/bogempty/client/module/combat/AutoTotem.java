package ru.bogempty.client.module.combat;

import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotem extends Module {

    private final Setting.Num health = add(new Setting.Num("Health", 12.0, 1.0, 20.0, 0.5),
            "Swap when health is at or below this value.");
    private final Setting.Num delayMin = add(new Setting.Num("Delay min", 1, 0, 8, 1),
            "Minimum ticks before swap.");
    private final Setting.Num delayMax = add(new Setting.Num("Delay max", 3, 0, 12, 1),
            "Maximum ticks before swap.");
    private final Setting.Bool offhandOnly = add(new Setting.Bool("Offhand only", true),
            "Put totem only into offhand.");
    private final Setting.Bool silent = add(new Setting.Bool("Silent", true),
            "Do not open inventory screen.");
    private final Setting.Bool predict = add(new Setting.Bool("Predict pop", true),
            "Also swap after recent hard damage.");
    private final Setting.Mode mode = add(new Setting.Mode("Mode", "Legit", "Strict"),
            "Legit: random delay. Strict: minimal delay for hard situations.");

    private int waitTicks = 0;
    private float lastHp = 20f;
    private long lastDamageMs = 0L;

    public AutoTotem() {
        super("AutoTotem", "Puts a totem into offhand when low on health", Category.COMBAT);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null) return;

        float hp = p.getHealth() + p.getAbsorptionAmount();
        if (hp < lastHp - 2.0f) lastDamageMs = System.currentTimeMillis();
        lastHp = hp;

        ItemStack off = p.getOffHandStack();
        if (off.isOf(Items.TOTEM_OF_UNDYING)) {
            waitTicks = 0;
            return;
        }

        boolean need = hp <= health.get()
                || (predict.get() && System.currentTimeMillis() - lastDamageMs < 350);

        if (!need) {
            waitTicks = 0;
            return;
        }

        int slot = findTotemSlot(p);
        if (slot == -1) return;

        if (waitTicks <= 0) {
            int min = (int) delayMin.get();
            int max = Math.max(min, (int) delayMax.get());
            waitTicks = mode.is("Strict") ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
            return;
        }

        waitTicks--;
        if (waitTicks > 0) return;

        swapToOffhand(slot);
    }

    private int findTotemSlot(PlayerEntity p) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.TOTEM_OF_UNDYING)) return i < 9 ? i + 36 : i;
        }
        return -1;
    }

    private void swapToOffhand(int invSlot) {
        if (mc.interactionManager == null || mc.player == null) return;
        int sync = mc.player.currentScreenHandler.syncId;

        mc.interactionManager.clickSlot(sync, invSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(sync, 45, 0, SlotActionType.PICKUP, mc.player);

        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(sync, invSlot, 0, SlotActionType.PICKUP, mc.player);
        }
    }
}