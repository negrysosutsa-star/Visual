package ru.bogempty.client.util;

import net.minecraft.client.option.SimpleOption;

/** Временно подменяет настройку игры и умеет вернуть как было. */
public final class OptOverride<T> {
    private boolean has = false;
    private T orig;

    public void apply(SimpleOption<T> opt, boolean on, T value) {
        if (opt == null) return;
        if (on) {
            if (!has) {
                orig = opt.getValue();
                has = true;
            }
            if (!value.equals(opt.getValue())) opt.setValue(value);
        } else {
            restore(opt);
        }
    }

    public void restore(SimpleOption<T> opt) {
        if (opt == null || !has) return;
        opt.setValue(orig);
        has = false;
    }
}