package ru.bogempty.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import ru.bogempty.client.BogEmptyClient;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.RenderUtils;

import java.util.List;

public class ClickGui extends Screen {

    private float animProgress = 0f;
    private boolean closing = false;

    private int guiX = -1;
    private int guiY = -1;
    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;

    private Category currentCategory = Category.MOVEMENT;

    private static final int WIDTH = 320;
    private static final int HEIGHT = 260;
    private static final int ACCENT = 0xFF00FFAA;
    private static final int BG = 0xEE0B0B14;
    private static final int HEADER = 0xFF12121F;

    public ClickGui() {
        super(Text.literal("BogEmpty"));
    }

    @Override
    protected void init() {
        super.init();
        if (guiX == -1) {
            guiX = (width - WIDTH) / 2;
            guiY = -HEIGHT;
        }
        closing = false;
        animProgress = 0f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float target = closing ? 0f : 1f;
        animProgress += (target - animProgress) * 0.18f;

        int targetY = (height - HEIGHT) / 2;
        int currentY = (int) (guiY + (targetY - guiY) * animProgress);

        // Затемнение
        int alpha = (int) (180 * animProgress);
        context.fill(0, 0, width, height, (alpha << 24));

        // Окно
        RenderUtils.drawRoundedRect(context, guiX, currentY, guiX + WIDTH, currentY + HEIGHT, 8, BG);

        // Header
        RenderUtils.drawRoundedRect(context, guiX, currentY, guiX + WIDTH, currentY + 28, 8, HEADER);
        context.fill(guiX, currentY + 20, guiX + WIDTH, currentY + 28, HEADER);

        context.drawTextWithShadow(textRenderer, "§bBogEmpty §fClient", guiX + 12, currentY + 9, 0xFFFFFFFF);

        // Категории
        int catY = currentY + 40;
        for (Category cat : Category.values()) {
            boolean selected = cat == currentCategory;
            int color = selected ? ACCENT : 0xFFAAAAAA;

            if (selected) {
                RenderUtils.drawRect(context, guiX + 8, catY - 2, guiX + 11, catY + 12, ACCENT);
            }

            context.drawTextWithShadow(textRenderer, cat.name, guiX + 18, catY, color);
            catY += 18;
        }

        // Модули
        List<Module> modules = BogEmptyClient.moduleManager.getModulesByCategory(currentCategory);
        int modY = currentY + 40;
        int modX = guiX + 110;

        for (Module m : modules) {
            boolean hovered = mouseX >= modX && mouseX <= modX + 190 &&
                    mouseY >= modY && mouseY <= modY + 18;

            int bgColor = m.isEnabled() ? 0x3300FFAA : (hovered ? 0x22FFFFFF : 0x00000000);
            RenderUtils.drawRoundedRect(context, modX, modY, modX + 190, modY + 18, 4, bgColor);

            String status = m.isEnabled() ? "§aON" : "§7OFF";
            context.drawTextWithShadow(textRenderer, m.name, modX + 6, modY + 5, 0xFFFFFFFF);
            context.drawTextWithShadow(textRenderer, status, modX + 155, modY + 5, 0xFFFFFFFF);

            modY += 22;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int targetY = (height - HEIGHT) / 2;
        int currentY = (int) (guiY + (targetY - guiY) * animProgress);

        // Перетаскивание
        if (button == 0 && mouseX >= guiX && mouseX <= guiX + WIDTH &&
                mouseY >= currentY && mouseY <= currentY + 28) {
            dragging = true;
            dragOffsetX = (int) (mouseX - guiX);
            dragOffsetY = (int) (mouseY - currentY);
            return true;
        }

        // Категории
        int catY = currentY + 40;
        for (Category cat : Category.values()) {
            if (mouseX >= guiX + 8 && mouseX <= guiX + 100 &&
                    mouseY >= catY - 2 && mouseY <= catY + 14) {
                currentCategory = cat;
                return true;
            }
            catY += 18;
        }

        // Модули
        List<Module> modules = BogEmptyClient.moduleManager.getModulesByCategory(currentCategory);
        int modY = currentY + 40;
        int modX = guiX + 110;

        for (Module m : modules) {
            if (mouseX >= modX && mouseX <= modX + 190 &&
                    mouseY >= modY && mouseY <= modY + 18) {
                m.toggle();
                return true;
            }
            modY += 22;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            guiX = (int) (mouseX - dragOffsetX);
            guiY = (int) (mouseY - dragOffsetY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        closing = true;
        super.close();
    }
}