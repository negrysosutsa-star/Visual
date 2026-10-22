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
 * Свап по клавише. Клавиша модуля (СКМ по модулю в меню) НЕ включает его, а делает свап.
 * Включи модуль в меню, назначь клавишу и жми её в игре.
 */
public class AutoSwap extends Module {
    private static final String[] ITEMS = {
            "Тотем", "Сфера", "Талисман", "Щит", "Золотое яблоко", "Жемчуг Эндера", "Кристалл Энда"
    };

    private final Setting.Mode mode = add(new Setting.Mode("Режим",
            "Оффхенд A⇄B", "Рука A⇄B", "Только A в оффхенд"),
            "Оффхенд A⇄B: в левую руку то A, то B. Рука A⇄B: в основную руку то A, то B. Только A в оффхенд: всегда A.");
    private final Setting.Mode itemA = add(new Setting.Mode("Предмет A", ITEMS),
            "Первый предмет. Тотем — обычный тотем. Сфера и Талисман ищутся по названию предмета.");
    private final Setting.Mode itemB = add(new Setting.Mode("Предмет B", ITEMS),
            "Второй предмет для обмена.");
    private final Setting.Num delay = add(new Setting.Num("Задержка, тики", 0, 0, 5, 1),
            "Через сколько тиков после нажатия сделать свап.");
    private final Setting.Bool preferHotbar = add(new Setting.Bool("Сначала хотбар", true),
            "Искать предмет сначала в хотбаре, потом в инвентаре.");
    private final Setting.Bool toastOn = add(new Setting.Bool("Уведомление", true),
            "Показывать на экране, что именно было свапнуто.");

    private int pending = -1;
    private String toast = "";
    private long toastUntil = 0L;

    public AutoSwap() {
        super("AutoSwap", "Свап предметов по клавише (тотем, сфера, талисман и др.)", Category.COMBAT);
        keyAction = true;
        key = GLFW.GLFW_KEY_X;
        itemB.index = 1;
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
        if (p == null || mc.interactionManager == null || mc.currentScreen != null) {
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
        String a = itemA.get();
        String b = itemB.get();

        if (mode.is("Только A в оффхенд")) {
            if (matches(p.getOffHandStack(), a)) {
                say("Уже в оффхенде: " + a);
                return;
            }
            toOffhand(p, a);
            return;
        }

        if (mode.is("Рука A⇄B")) {
            String want = matches(p.getMainHandStack(), a) ? b : a;
            toMainHand(p, want);
            return;
        }

        // Оффхенд A⇄B: если там A, кладём B, иначе A
        String want = matches(p.getOffHandStack(), a) ? b : a;
        toOffhand(p, want);
    }

    private void toOffhand(ClientPlayerEntity p, String want) {
        if (matches(p.getOffHandStack(), want)) {
            say("Уже в оффхенде: " + want);
            return;
        }
        int slot = find(p, want, false);
        if (slot == -1) {
            say("Нет предмета: " + want);
            return;
        }
        // один клик SWAP с кнопкой 40 меняет слот и оффхенд местами
        click(p, slot, 40);
        say("Оффхенд: " + want);
    }

    private void toMainHand(ClientPlayerEntity p, String want) {
        if (matches(p.getMainHandStack(), want)) {
            say("Уже в руке: " + want);
            return;
        }
        int slot = find(p, want, true);
        if (slot == -1) {
            say("Нет предмета: " + want);
            return;
        }
        if (slot >= 36) {
            // предмет в хотбаре: просто выбираем слот
            p.getInventory().selectedSlot = slot - 36;
        } else {
            // предмет в инвентаре: меняем с выбранным слотом хотбара
            click(p, slot, p.getInventory().selectedSlot);
        }
        say("В руке: " + want);
    }

    /** Ищет предмет. Возвращает id слота в PlayerScreenHandler: 9..35 инвентарь, 36..44 хотбар. */
    private int find(ClientPlayerEntity p, String want, boolean hotbarFirstForced) {
        boolean hotbarFirst = hotbarFirstForced || preferHotbar.get();
        if (hotbarFirst) {
            for (int i = 0; i < 9; i++) if (matches(p.getInventory().getStack(i), want)) return 36 + i;
            for (int i = 9; i < 36; i++) if (matches(p.getInventory().getStack(i), want)) return i;
        } else {
            for (int i = 9; i < 36; i++) if (matches(p.getInventory().getStack(i), want)) return i;
            for (int i = 0; i < 9; i++) if (matches(p.getInventory().getStack(i), want)) return 36 + i;
        }
        return -1;
    }

    private void click(ClientPlayerEntity p, int slot, int button) {
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, button, SlotActionType.SWAP, p);
    }

    private static String plainName(ItemStack s) {
        return s.getName().getString().toLowerCase(Locale.ROOT).replaceAll("§.", "");
    }

    private static boolean matches(ItemStack s, String option) {
        if (s == null || s.isEmpty()) return false;
        switch (option) {
            case "Тотем": {
                String n = plainName(s);
                return s.isOf(Items.TOTEM_OF_UNDYING) && !n.contains("сфер") && !n.contains("талисман");
            }
            case "Сфера":
                return plainName(s).contains("сфер");
            case "Талисман":
                return plainName(s).contains("талисман");
            case "Щит":
                return s.isOf(Items.SHIELD);
            case "Золотое яблоко":
                return s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE);
            case "Жемчуг Эндера":
                return s.isOf(Items.ENDER_PEARL);
            case "Кристалл Энда":
                return s.isOf(Items.END_CRYSTAL);
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