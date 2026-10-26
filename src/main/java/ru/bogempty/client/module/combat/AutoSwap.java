package ru.bogempty.client.module.combat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;

import java.util.Locale;

/**
 * Один общий bind; выбранный профиль задаёт пару предметов для переключения.
 */
public class AutoSwap extends Module {
    private final Setting.Mode profile = add(new Setting.Mode("Профиль",
            "Тотем → Тотем", "Сфера → Сфера", "Тотем ⇄ Сфера"),
            "Выбери профиль здесь. Все профили используют одну клавишу AutoSwap.");
    private final Setting.Mode handMode = add(new Setting.Mode("Куда свапать",
            "Оффхенд", "Основная рука"),
            "Выбери, куда переключать предмет.");
    private final Setting.Num delay = add(new Setting.Num("Задержка, тики", 1, 0, 10, 1),
            "Задержка между нажатием бинда и свапом.");
    private final Setting.Bool preferHotbar = add(new Setting.Bool("Сначала хотбар", true),
            "Искать предмет сначала в хотбаре, потом в инвентаре.");
    private final Setting.Bool toastOn = add(new Setting.Bool("Уведомление", true),
            "Показывать результат свапа на экране.");

    private int pending = -1;
    private String toast = "";
    private long toastUntil = 0L;

    public AutoSwap() {
        super("AutoSwap", "Свап предметов по одному бинду с выбираемыми профилями", Category.COMBAT);
        keyAction = true;
        key = GLFW.GLFW_KEY_X;
    }

    @Override
    public void onKey() {
        pending = (int) delay.get();
    }

    @Override
    public void onDisable() {
        pending = -1;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null
                || p.currentScreenHandler != p.playerScreenHandler) {
            pending = -1;
            return;
        }
        if (pending < 0) return;
        if (pending > 0) {
            pending--;
            return;
        }
        pending = -1;
        run(p);
    }

    private void run(ClientPlayerEntity p) {
        String a;
        String b;
        switch (profile.get()) {
            case "Сфера → Сфера":
                a = "Сфера";
                b = "Сфера";
                break;
            case "Тотем ⇄ Сфера":
                a = "Тотем";
                b = "Сфера";
                break;
            default:
                a = "Тотем";
                b = "Тотем";
                break;
        }

        if (handMode.is("Основная рука")) {
            String current = itemName(p.getMainHandStack());
            String want = current.equals(a) ? b : a;
            toMainHand(p, want);
        } else {
            String current = itemName(p.getOffHandStack());
            String want = current.equals(a) ? b : a;
            // Для профилей A→A ищем следующий предмет того же типа, чтобы можно было
            // обновить стак/предмет из инвентаря; если уже стоит и других нет — ничего не меняем.
            toOffhand(p, want);
        }
    }

    private void toOffhand(ClientPlayerEntity p, String want) {
        if (matches(p.getOffHandStack(), want)) {
            int alternate = find(p, want, true, true);
            if (alternate < 0) {
                say("Уже стоит: " + want);
                return;
            }
            click(p, alternate, 40);
        } else {
            int slot = find(p, want, true, false);
            if (slot < 0) {
                say("Нет предмета: " + want);
                return;
            }
            click(p, slot, 40);
        }
        say("Оффхенд: " + want);
    }

    private void toMainHand(ClientPlayerEntity p, String want) {
        if (matches(p.getMainHandStack(), want)) {
            say("Уже в руке: " + want);
            return;
        }
        int slot = find(p, want, true, false);
        if (slot < 0) {
            say("Нет предмета: " + want);
            return;
        }
        if (slot >= 36) p.getInventory().selectedSlot = slot - 36;
        else click(p, slot, p.getInventory().selectedSlot);
        say("В руке: " + want);
    }

    private int find(ClientPlayerEntity p, String want, boolean hotbarFirst, boolean excludeOffhandTwin) {
        if (hotbarFirst && preferHotbar.get()) {
            for (int i = 0; i < 9; i++) {
                int id = 36 + i;
                if (matches(p.getInventory().getStack(i), want)
                        && (!excludeOffhandTwin || id != 45)) return id;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (matches(p.getInventory().getStack(i), want)) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (matches(p.getInventory().getStack(i), want)) return 36 + i;
        }
        return -1;
    }

    private void click(ClientPlayerEntity p, int slot, int button) {
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, button, SlotActionType.SWAP, p);
    }

    private static String itemName(ItemStack stack) {
        if (matches(stack, "Тотем")) return "Тотем";
        if (matches(stack, "Сфера")) return "Сфера";
        return "";
    }

    private static String plainName(ItemStack s) {
        return s.getName().getString().toLowerCase(Locale.ROOT).replaceAll("§.", "");
    }

    private static boolean matches(ItemStack s, String option) {
        if (s == null || s.isEmpty()) return false;
        switch (option) {
            case "Тотем":
                return s.isOf(Items.TOTEM_OF_UNDYING)
                        && !plainName(s).contains("сфер") && !plainName(s).contains("талисман");
            case "Сфера":
                return plainName(s).contains("сфер");
            default:
                return false;
        }
    }

    private void say(String s) {
        toast = s;
        toastUntil = System.currentTimeMillis() + 1400L;
    }

    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        if (!toastOn.get()) return;
        long left = toastUntil - System.currentTimeMillis();
        if (left <= 0 || toast.isEmpty()) return;
        float a = Math.min(1f, left / 400f);
        int w = c.getScaledWindowWidth();
        int h = c.getScaledWindowHeight();
        int tw = Fonts.width(toast);
        int x = (w - tw) / 2;
        int y = h - 62;
        RenderUtils.drawRoundedRect(c, x - 8, y - 4, x + tw + 8, y + 13, 5, RenderUtils.alpha(0xCC0C0C14, a));
        RenderUtils.drawRect(c, x - 8, y + 12, x + tw + 8, y + 13, RenderUtils.alpha(0xFF7C6CFF, a));
        Fonts.draw(c, toast, x, y, RenderUtils.alpha(0xFFFFFFFF, a));
    }
}
