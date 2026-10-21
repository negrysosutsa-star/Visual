package ru.bogempty.client.module.combat;

import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

public class AutoSwap extends Module {

    private final Setting.Mode mode = add(new Setting.Mode("Режим",
            "Сфера на сферу", "Тотем на тотем", "Сфера на тотем"),
            "Что менять.");
    private final Setting.Bool onKey = add(new Setting.Bool("По клавише", true),
            "Свап только по бинду ниже.");
    private final Setting.Num swapKey = add(new Setting.Num("Бинд (GLFW)", GLFW.GLFW_KEY_X, 0, 400, 1),
            "Код клавиши GLFW. В ClickGui зажми модуль-биндом или поставь сюда код (X=88).");
    private final Setting.Num delay = add(new Setting.Num("Задержка", 1, 0, 6, 1),
            "Тики между кликами.");
    private final Setting.Bool preferHotbar = add(new Setting.Bool("Сначала хотбар", true),
            "Искать сначала в хотбаре.");

    private int cooldown = 0;
    private boolean keyWasDown = false;

    public AutoSwap() {
        super("AutoSwap", "Свап сфер и тотемов", Category.COMBAT);
        key = GLFW.GLFW_KEY_X;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        int k = (int) swapKey.get();
        if (k <= 0) k = key > 0 ? key : GLFW.GLFW_KEY_X;

        boolean pressed = GLFW.glfwGetKey(mc.getWindow().getHandle(), k) == GLFW.GLFW_PRESS;

        if (onKey.get()) {
            if (!pressed || keyWasDown) {
                keyWasDown = pressed;
                return;
            }
            keyWasDown = true;
        } else if (!shouldAuto(p)) {
            return;
        }

        doSwap(p);
        cooldown = (int) delay.get() + ThreadLocalRandom.current().nextInt(0, 2);
    }

    private boolean shouldAuto(ClientPlayerEntity p) {
        ItemStack main = p.getMainHandStack();
        ItemStack off = p.getOffHandStack();
        return switch (mode.get()) {
            case "Сфера на сферу" -> !main.isOf(Items.END_CRYSTAL) && has(p, Items.END_CRYSTAL);
            case "Тотем на тотем" -> !off.isOf(Items.TOTEM_OF_UNDYING) && has(p, Items.TOTEM_OF_UNDYING);
            case "Сфера на тотем" -> main.isOf(Items.END_CRYSTAL) && has(p, Items.TOTEM_OF_UNDYING);
            default -> false;
        };
    }

    private void doSwap(ClientPlayerEntity p) {
        switch (mode.get()) {
            case "Сфера на сферу" -> swapItem(p, Items.END_CRYSTAL, false);
            case "Тотем на тотем" -> swapItem(p, Items.TOTEM_OF_UNDYING, true);
            case "Сфера на тотем" -> {
                if (!p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                    int t = find(p, Items.TOTEM_OF_UNDYING);
                    if (t != -1) clickSwap(t, 45);
                }
            }
            default -> {}
        }
    }

    private void swapItem(ClientPlayerEntity p, Item item, boolean toOffhand) {
        int targetSlot = toOffhand ? 45 : p.getInventory().selectedSlot + 36;
        ItemStack cur = toOffhand ? p.getOffHandStack() : p.getMainHandStack();
        if (cur.isOf(item)) return;
        int from = find(p, item);
        if (from == -1) return;
        clickSwap(from, targetSlot);
    }

    private int find(PlayerEntity p, Item item) {
        int start = preferHotbar.get() ? 0 : 9;
        int end = preferHotbar.get() ? 9 : 36;
        for (int i = start; i < end; i++) {
            if (p.getInventory().getStack(i).isOf(item)) return i < 9 ? i + 36 : i;
        }
        for (int i = 0; i < 36; i++) {
            if (p.getInventory().getStack(i).isOf(item)) return i < 9 ? i + 36 : i;
        }
        return -1;
    }

    private boolean has(PlayerEntity p, Item item) {
        return find(p, item) != -1;
    }

    private void clickSwap(int from, int to) {
        if (mc.interactionManager == null || mc.player == null) return;
        int sync = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(sync, from, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(sync, to, 0, SlotActionType.PICKUP, mc.player);
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(sync, from, 0, SlotActionType.PICKUP, mc.player);
        }
    }
}