package ru.bogempty.client.module.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.RenderUtils;

public class ArmorHud extends Module {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public ArmorHud() {
        super("ArmorHud", "Armor and durability on the right", Category.HUD);
    }

    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        if (mc.player == null) return;
        int x = mc.getWindow().getScaledWidth() - 26;
        int y = mc.getWindow().getScaledHeight() / 2 - 36;

        RenderUtils.drawRoundedRect(c, x - 4, y - 4, x + 20, y + 72 + 4, 5, 0x880C0C14);
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack s = mc.player.getEquippedStack(SLOTS[i]);
            if (s.isEmpty()) continue;
            c.drawItem(s, x, y + i * 18);
            c.drawStackOverlay(mc.textRenderer, s, x, y + i * 18);
        }
    }
}