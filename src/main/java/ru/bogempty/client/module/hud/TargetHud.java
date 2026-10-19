package ru.bogempty.client.module.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;

import java.util.Locale;

public class TargetHud extends Module {
    private LivingEntity target;
    private long lastSeen;
    private float bar;

    public TargetHud() {
        super("TargetHud", "Info about the entity you look at", Category.HUD);
    }

    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        if (mc.player == null) return;

        if (mc.crosshairTarget instanceof EntityHitResult r && r.getEntity() instanceof LivingEntity le) {
            target = le;
            lastSeen = System.currentTimeMillis();
        }
        if (target == null || !target.isAlive() || System.currentTimeMillis() - lastSeen > 2000) {
            target = null;
            return;
        }

        float hp = target.getHealth();
        float max = Math.max(1f, target.getMaxHealth());
        float ratio = Math.max(0f, Math.min(1f, hp / max));
        bar += (ratio - bar) * 0.15f;

        int x = mc.getWindow().getScaledWidth() / 2 + 24;
        int y = mc.getWindow().getScaledHeight() / 2 + 24;
        int w = 130, h = 34;

        RenderUtils.drawRoundedRect(c, x, y, x + w, y + h, 6, 0xAA0C0C14);
        Fonts.draw(c, target.getName().getString(), x + 8, y + 6, 0xFFFFFFFF);
        String hpText = String.format(Locale.ROOT, "%.1f", hp);
        Fonts.draw(c, hpText, x + w - 8 - Fonts.width(hpText), y + 6, 0xFFB8B8D0);

        int bx = x + 8, bw = w - 16, by = y + 22;
        RenderUtils.drawRoundedRect(c, bx, by, bx + bw, by + 5, 2, 0xFF22222E);
        int fill = Math.max(2, Math.round(bw * bar));
        RenderUtils.drawRoundedRect(c, bx, by, bx + fill, by + 5, 2,
                RenderUtils.lerp(0xFFE74C3C, 0xFF2ECC71, bar));
    }
}