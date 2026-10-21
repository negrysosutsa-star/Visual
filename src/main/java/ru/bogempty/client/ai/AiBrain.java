package ru.bogempty.client.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Онлайн-модель (логистическая регрессия). Учится на твоих ударах и на результатах своих. */
public final class AiBrain {
    public static final int N = 11;
    // стартовые веса: бить при полном заряде, не бить в кулдауне чужого урона
    private static final double[] PRIOR = {-4.0, 3.0, 3.0, 0.0, 0.3, -1.0, -1.0, 0.0, -2.5, 1.0, 1.0};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AiBrain inst;

    public final double[] w = new double[N];
    public long samples, userHits, botHits, botGood, botBad;
    public double userCd = 0.0;
    private int sinceSave = 0;

    public static AiBrain get() {
        if (inst == null) {
            inst = new AiBrain();
            inst.load();
        }
        return inst;
    }

    private AiBrain() {
        System.arraycopy(PRIOR, 0, w, 0, N);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("bogempty_ai.json");
    }

    public double predict(double[] x) {
        double z = 0;
        for (int i = 0; i < N; i++) z += w[i] * x[i];
        return 1.0 / (1.0 + Math.exp(-z));
    }

    public void train(double[] x, double y, double weight, double lr) {
        double p = predict(x);
        double step = lr * Math.max(0.2, 1.0 / Math.sqrt(1.0 + samples / 300.0)) * weight;
        double err = y - p;
        for (int i = 0; i < N; i++) {
            w[i] += step * err * x[i] - step * 0.002 * (w[i] - PRIOR[i]);
            w[i] = Math.max(-12.0, Math.min(12.0, w[i]));
        }
        samples++;
        if (++sinceSave >= 150) save();
    }

    /** Точность собственных ударов бота, 0..1. */
    public double accuracy() {
        long t = botGood + botBad;
        return t == 0 ? 0.0 : (double) botGood / t;
    }

    /** Насколько модель "наелась" опыта, 0..1. */
    public double maturity() {
        return samples / (samples + 300.0);
    }

    public void reset() {
        System.arraycopy(PRIOR, 0, w, 0, N);
        samples = 0;
        userHits = 0;
        botHits = 0;
        botGood = 0;
        botBad = 0;
        userCd = 0;
        save();
    }

    public void save() {
        sinceSave = 0;
        try {
            JsonObject o = new JsonObject();
            JsonArray a = new JsonArray();
            for (double d : w) a.add(d);
            o.add("w", a);
            o.addProperty("samples", samples);
            o.addProperty("userHits", userHits);
            o.addProperty("botHits", botHits);
            o.addProperty("botGood", botGood);
            o.addProperty("botBad", botBad);
            o.addProperty("userCd", userCd);
            Files.writeString(file(), GSON.toJson(o));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            if (o.has("w")) {
                JsonArray a = o.getAsJsonArray("w");
                if (a.size() == N) {
                    int i = 0;
                    for (JsonElement el : a) w[i++] = el.getAsDouble();
                }
            }
            if (o.has("samples")) samples = o.get("samples").getAsLong();
            if (o.has("userHits")) userHits = o.get("userHits").getAsLong();
            if (o.has("botHits")) botHits = o.get("botHits").getAsLong();
            if (o.has("botGood")) botGood = o.get("botGood").getAsLong();
            if (o.has("botBad")) botBad = o.get("botBad").getAsLong();
            if (o.has("userCd")) userCd = o.get("userCd").getAsDouble();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}