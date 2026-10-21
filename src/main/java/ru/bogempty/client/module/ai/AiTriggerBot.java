package ru.bogempty.client.module.ai;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import ru.bogempty.client.ai.AiBrain;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;

import java.util.concurrent.ThreadLocalRandom;

public class AiTriggerBot extends Module {
    private final Setting.Mode mode = add(new Setting.Mode("Режим", "Бой + обучение", "Только бой", "Только обучение"),
            "Бой + обучение: бьёт сам и учится на твоих ударах и на итогах своих. Только бой: бьёт без обучения. Только обучение: сам не бьёт, учится на тебе.");
    private final Setting.Mode targets = add(new Setting.Mode("Цели", "Игроки", "Мобы", "Все"),
            "Каких существ атаковать.");
    private final Setting.Mode crits = add(new Setting.Mode("Криты", "Умные", "Только криты", "Не ждать"),
            "Умные: решает модель. Только криты: бьёт лишь при падении. Не ждать: бьёт как только готов.");
    private final Setting.Num reach = add(new Setting.Num("Дистанция", 3.0, 1.5, 3.0, 0.1),
            "Максимальная дистанция удара.");
    private final Setting.Num threshold = add(new Setting.Num("Порог уверенности %", 60, 30, 95, 1),
            "Бьёт, только если модель уверена не меньше этого процента. Выше — реже, но точнее.");
    private final Setting.Num minCd = add(new Setting.Num("Мин. заряд %", 70, 30, 100, 1),
            "Жёсткий минимум заряда оружия, ниже которого бот не бьёт.");
    private final Setting.Bool autoJump = add(new Setting.Bool("Авто-прыжок", false),
            "Прыгает сам при заряженном оружии и бьёт на спуске (крит).");
    private final Setting.Num delayMin = add(new Setting.Num("Реакция от, мс", 0, 0, 200, 5),
            "Минимальная задержка реакции перед ударом.");
    private final Setting.Num delayMax = add(new Setting.Num("Реакция до, мс", 40, 0, 300, 5),
            "Максимальная задержка реакции перед ударом (случайно между «от» и «до»).");
    private final Setting.Num lr = add(new Setting.Num("Скорость обучения", 0.12, 0.02, 0.5, 0.01),
            "Как резко модель меняется после каждого примера. С опытом снижается сама.");
    private final Setting.Num imitation = add(new Setting.Num("Влияние твоих ударов %", 70, 0, 100, 5),
            "Насколько сильно бот копирует твой стиль: когда и как ты бьёшь.");
    private final Setting.Bool selfLearn = add(new Setting.Bool("Самообучение", true),
            "Бот оценивает свои удары (попал, полный заряд, крит) и учится на них.");
    private final Setting.Bool hud = add(new Setting.Bool("Панель статистики", true),
            "Показывать на экране опыт, точность и уверенность модели.");
    private final Setting.Bool reset = add(new Setting.Bool("Сбросить обучение", false),
            "Включи, чтобы стереть весь опыт и начать заново (переключатель вернётся сам).");

    private final AiBrain brain = AiBrain.get();

    private boolean prevAttack = false;
    private double[] lastFeat = null;
    private double lastProb = 0.0;
    private long tick = 0L;
    private long lastUserHit = -1000L;
    private boolean armed = false;
    private long fireAt = 0L;
    private boolean jumpHeld = false;

    private LivingEntity pTarget = null;
    private double[] pFeat = null;
    private long pTick = 0L;
    private int pHurt0 = 0;

    public AiTriggerBot() {
        super("AiTriggerBot", "Триггербот с самообучением", Category.AI);
    }

    @Override
    public void onDisable() {
        releaseJump();
        lastFeat = null;
        pTarget = null;
        armed = false;
        brain.save();
    }

    @Override
    public void onTick() {
        tick++;
        if (reset.get()) {
            reset.value = false;
            brain.reset();
            lastFeat = null;
            pTarget = null;
        }

        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) {
            releaseJump();
            lastFeat = null;
            pTarget = null;
            return;
        }

        boolean pressed = mc.options.attackKey.isPressed();
        boolean userClick = pressed && !prevAttack && mc.currentScreen == null;
        prevAttack = pressed;

        boolean fight = !mode.is("Только обучение");
        boolean learnUser = !mode.is("Только бой");
        boolean learnSelf = mode.is("Бой + обучение") && selfLearn.get();
        double lrv = lr.get();
        double imi = imitation.get() / 100.0;

        evalPending(learnSelf, lrv);

        if (mc.currentScreen != null) {
            releaseJump();
            lastFeat = null;
            armed = false;
            return;
        }

        if (jumpHeld && !p.isOnGround()) releaseJump();

        LivingEntity t = findTarget(p);
        if (t == null) {
            lastFeat = null;
            lastProb = 0.0;
            armed = false;
            releaseJump();
            return;
        }

        double dist = mc.crosshairTarget.getPos().distanceTo(p.getEyePos());
        double[] x = features(p, t, dist);

        // твой удар: положительный пример (признаки берём с прошлого тика, до удара)
        if (userClick) {
            if (learnUser && lastFeat != null && imi > 0) {
                brain.train(lastFeat, 1.0, imi, lrv);
                brain.userHits++;
                brain.userCd = brain.userCd == 0.0 ? lastFeat[1] : brain.userCd * 0.9 + lastFeat[1] * 0.1;
            }
            lastUserHit = tick;
            lastFeat = x;
            return;
        }

        lastProb = brain.predict(x);
        lastFeat = x;

        if (!fight) {
            // ждёт вместе с тобой: учится, когда ты в бою, но не бьёшь
            if (learnUser && imi > 0 && tick - lastUserHit < 40 && tick - lastUserHit > 2) {
                brain.train(x, 0.0, 0.05 * imi, lrv);
            }
            return;
        }

        if (mc.interactionManager.isBreakingBlock() || p.isUsingItem()) {
            armed = false;
            return;
        }

        boolean critReady = x[9] > 0.5;
        boolean cantFall = p.isTouchingWater() || p.isClimbing() || p.hasVehicle();

        if (autoJump.get() && !cantFall) {
            if (!critReady && p.isOnGround() && x[1] >= 0.85 && !jumpHeld && !mc.options.jumpKey.isPressed()) {
                mc.options.jumpKey.setPressed(true);
                jumpHeld = true;
                armed = false;
                return;
            }
            if (jumpHeld && !critReady) {
                armed = false;
                return;
            }
        }

        if (crits.is("Только криты") && !critReady && !cantFall) {
            armed = false;
            return;
        }

        boolean ok = x[1] >= minCd.get() / 100.0 && lastProb >= threshold.get() / 100.0;
        if (!ok) {
            armed = false;
            return;
        }

        long now = System.currentTimeMillis();
        if (!armed) {
            armed = true;
            int lo = (int) delayMin.get();
            int hi = Math.max(lo, (int) delayMax.get());
            fireAt = now + (hi > 0 ? ThreadLocalRandom.current().nextInt(lo, hi + 1) : 0);
        }
        if (now < fireAt) return;

        mc.interactionManager.attackEntity(p, t);
        p.swingHand(Hand.MAIN_HAND);
        releaseJump();
        armed = false;
        brain.botHits++;

        pTarget = t;
        pFeat = x.clone();
        pTick = tick;
        pHurt0 = t.hurtTime;
    }

    // итог собственного удара: попал ли, с полным ли зарядом, крит ли
    private void evalPending(boolean learnSelf, double lrv) {
        if (pTarget == null || pFeat == null) return;
        long age = tick - pTick;
        boolean dead = !pTarget.isAlive();
        boolean landed = pTarget.hurtTime > 0 && pHurt0 == 0;
        if (!landed && !dead && age < 8) return;
        if (pHurt0 > 0 && !dead) {
            pTarget = null;
            pFeat = null;
            return;
        }
        boolean good;
        double weight;
        if (dead || landed) {
            good = pFeat[1] >= 0.9;
            weight = good ? (pFeat[9] > 0.5 ? 1.3 : 1.0) : 0.9;
        } else {
            good = false;
            weight = 0.7;
        }
        if (good) brain.botGood++;
        else brain.botBad++;
        if (learnSelf) brain.train(pFeat, good ? 1.0 : 0.0, weight, lrv);
        pTarget = null;
        pFeat = null;
    }

    private LivingEntity findTarget(ClientPlayerEntity p) {
        HitResult hr = mc.crosshairTarget;
        if (!(hr instanceof EntityHitResult ehr)) return null;
        if (!(ehr.getEntity() instanceof LivingEntity le)) return null;
        if (le == p || !le.isAlive() || le instanceof ArmorStandEntity || le.isSpectator()) return null;
        boolean isPlayer = le instanceof PlayerEntity;
        if (targets.is("Игроки") && !isPlayer) return null;
        if (targets.is("Мобы") && !(le instanceof MobEntity)) return null;
        double d = hr.getPos().distanceTo(p.getEyePos());
        if (d > reach.get() + 0.05) return null;
        return le;
    }

    private double[] features(ClientPlayerEntity p, LivingEntity t, double dist) {
        double cd = Math.max(0.0, Math.min(1.0, p.getAttackCooldownProgress(0.5f)));
        double vy = p.getVelocity().y;
        boolean ground = p.isOnGround();
        boolean sprint = p.isSprinting();
        boolean crit = !ground && vy < -0.01 && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasVehicle() && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !sprint;

        double[] x = new double[AiBrain.N];
        x[0] = 1.0;
        x[1] = cd;
        x[2] = cd >= 0.9 ? 1.0 : 0.0;
        x[3] = ground ? 1.0 : 0.0;
        x[4] = Math.max(0.0, Math.min(1.0, -vy / 0.6));
        x[5] = vy > 0.02 ? 1.0 : 0.0;
        x[6] = dist / 6.0;
        x[7] = sprint ? 1.0 : 0.0;
        x[8] = Math.min(t.hurtTime, 10) / 10.0;
        x[9] = crit ? 1.0 : 0.0;
        x[10] = crit ? cd : 0.0;
        return x;
    }

    private void releaseJump() {
        if (jumpHeld) {
            if (mc.options != null) mc.options.jumpKey.setPressed(false);
            jumpHeld = false;
        }
    }

    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        if (!hud.get() || mc.player == null) return;
        int x = 8;
        int y = mc.getWindow().getScaledHeight() / 2 - 44;
        int w = 132, h = 88;
        RenderUtils.drawRoundedRect(c, x, y, x + w, y + h, 6, 0xAA0C0C14);
        Fonts.draw(c, "ИИ ТриггерБот", x + 8, y + 6, 0xFFFFFFFF);
        Fonts.draw(c, mode.get(), x + 8, y + 18, 0xFF8F86FF);
        Fonts.draw(c, "Опыт: " + brain.samples + "  (твои: " + brain.userHits + ")", x + 8, y + 30, 0xFFB8B8D0);
        String acc = (brain.botGood + brain.botBad) == 0 ? "—" : Math.round(brain.accuracy() * 100) + "%";
        Fonts.draw(c, "Точность: " + acc, x + 8, y + 42, 0xFFB8B8D0);

        bar(c, x + 8, y + 56, w - 16, brain.maturity(), "Обученность");
        bar(c, x + 8, y + 72, w - 16, lastProb, "Уверенность");
    }

    private void bar(DrawContext c, int x, int y, int w, double v, String label) {
        float f = (float) Math.max(0.0, Math.min(1.0, v));
        Fonts.draw(c, label, x, y - 1, 0xFF6E6E86);
        String pct = Math.round(f * 100) + "%";
        Fonts.draw(c, pct, x + w - Fonts.width(pct), y - 1, 0xFF8F86FF);
        RenderUtils.drawRoundedRect(c, x, y + 9, x + w, y + 12, 1, 0xFF22222E);
        RenderUtils.drawRoundedRect(c, x, y + 9, x + Math.max(2, Math.round(w * f)), y + 12, 1, 0xFF7C6CFF);
    }
}