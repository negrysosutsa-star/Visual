package ru.bogempty.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;

import java.util.concurrent.ThreadLocalRandom;

public class BogTitleScreen extends Screen {

    private final Star[] stars = new Star[80];
    private float time;

    public BogTitleScreen() {
        super(Text.literal("BogEmpty"));
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new Star(
                    ThreadLocalRandom.current().nextFloat(),
                    ThreadLocalRandom.current().nextFloat(),
                    0.3f + ThreadLocalRandom.current().nextFloat() * 0.7f
            );
        }
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2 - 20;
        int bw = 180;
        int bh = 22;
        int gap = 26;

        addDrawableChild(ButtonWidget.builder(Text.literal("Одиночная игра"),
                b -> client.setScreen(new SelectWorldScreen(this)))
                .dimensions(cx - bw / 2, y, bw, bh).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Сетевая игра"),
                b -> client.setScreen(new MultiplayerScreen(this)))
                .dimensions(cx - bw / 2, y + gap, bw, bh).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Альт-менеджер"),
                b -> client.setScreen(new AltManagerScreen(this)))
                .dimensions(cx - bw / 2, y + gap * 2, bw, bh).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Настройки"),
                b -> client.setScreen(new OptionsScreen(this, client.options)))
                .dimensions(cx - bw / 2, y + gap * 3, bw, bh).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Выйти"),
                b -> client.scheduleStop())
                .dimensions(cx - bw / 2, y + gap * 4, bw, bh).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        time += delta * 0.02f;

        // фон космос
        context.fill(0, 0, width, height, 0xFF05050C);
        // градиент пятна
        RenderUtils.drawRoundedRect(context, width / 2 - 200, height / 2 - 120, width / 2 + 200, height / 2 + 160, 40, 0x221A1040);
        RenderUtils.drawRoundedRect(context, 40, 40, 220, 180, 50, 0x187C6CFF);
        RenderUtils.drawRoundedRect(context, width - 240, height - 200, width - 20, height - 40, 60, 0x146C40A0);

        for (Star s : stars) {
            int sx = (int) (s.x * width);
            int sy = (int) (((s.y + time * s.speed * 0.05f) % 1.0f) * height);
            int a = (int) (80 + s.speed * 140);
            int col = (a << 24) | 0xC8C8FF;
            context.fill(sx, sy, sx + 1, sy + 1, col);
        }

        // лого
        String title = "BOGEMPTY";
        int tw = Fonts.width(title);
        Fonts.draw(context, title, width / 2 - tw / 2, height / 2 - 70, 0xFFB8B0FF);
        String sub = "CLIENT";
        int sw = Fonts.width(sub);
        Fonts.draw(context, sub, width / 2 - sw / 2, height / 2 - 56, 0xFF7C6CFF);

        super.render(context, mouseX, mouseY, delta);

        Fonts.draw(context, "§7FunTime ready", 8, height - 14, 0xFF6E6E86);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static class Star {
        final float x, y, speed;
        Star(float x, float y, float speed) {
            this.x = x;
            this.y = y;
            this.speed = speed;
        }
    }
}