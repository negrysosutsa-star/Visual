package ru.bogempty.client.module.combat;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

/**
 * Keeps a totem in the offhand whenever one is available in the inventory.
 * Uses a single SWAP click, so it does not leave an item on the cursor.
 */
public class AutoTotem extends Module {

    private final Setting.Bool predict = add(new Setting.Bool("Predict pop", true),
            "Prioritizes an immediate swap after taking heavy damage.");
    private final Setting.Bool offhandOnly = add(new Setting.Bool("Offhand only", true),
            "Keep the totem in the offhand.");
    private final Setting.Num health = add(new Setting.Num("Health", 12.0, 1.0, 20.0, 0.5),
            "Health threshold for urgent replacement.");

    private float lastHp = 20.0f;
    private long lastDamageMs = 0L;
    private int cooldownTicks = 0;

    public AutoTotem() {
        super("AutoTotem", "Automatically keeps a totem in the offhand", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        lastHp = mc.player == null ? 20.0f : mc.player.getHealth() + mc.player.getAbsorptionAmount();
        lastDamageMs = 0L;
        cooldownTicks = 0;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;

        // Never send inventory clicks while another screen/container is open.
        if (mc.currentScreen != null || p.currentScreenHandler != p.playerScreenHandler) return;

        float hp = p.getHealth() + p.getAbsorptionAmount();
        long now = System.currentTimeMillis();
        if (hp < lastHp - 2.0f) lastDamageMs = now;
        lastHp = hp;

        ItemStack offhand = p.getOffHandStack();
        if (offhand.isOf(Items.TOTEM_OF_UNDYING)) {
            cooldownTicks = 0;
            return;
        }

        int slot = findTotemSlot(p);
        if (slot < 0) return;

        boolean urgent = hp <= health.get()
                || (predict.get() && now - lastDamageMs < 350L);

        if (cooldownTicks > 0 && !urgent) {
            cooldownTicks--;
            return;
        }

        // SlotActionType.SWAP with button 40 exchanges the chosen inventory
        // slot directly with the offhand (slot 45) in one server action.
        mc.interactionManager.clickSlot(
                p.playerScreenHandler.syncId,
                slot,
                40,
                SlotActionType.SWAP,
                p
        );
        cooldownTicks = 2;
    }

    private int findTotemSlot(ClientPlayerEntity p) {
        // PlayerScreenHandler slot IDs: 9..35 inventory, 36..44 hotbar.
        // Search hotbar first, then the rest of the inventory.
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return 36 + i;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onDisable() {
        cooldownTicks = 0;
    }
}
