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

/**
 * Модель по ситуациям. У каждой ситуации (земля, подъём, падение, вода/лестница)
 * своя логистическая регрессия, поэтому "бить стоя" и "ждать крит в падении" учатся отдельно.
 */
public final class AiBrain {
    public static final int N = 10;
    public static final int S = 4;
    public static final int GROUND = 0, RISE = 1, FALL = 2, OTHER = 3;
    public static final String[] NAMES = {"Земля", "Подъём", "Падение", "Вода/лестница"};

    // bias, заряд, заряд>90%, дистанция, скорость падения, вот-вот приземлюсь, урон цели, спринт, стою на земле, держу прыжок
    private static final double[][] PRIOR = {
            /* Земля   */ {-4.5, 3.0, 3.5, -0.8, 0.0, 0.0, -2.5, 0.0, 0.8, -3.0},
            /* Подъём  */ {-6.0, 2.0, 2.0, -0.5, 0.0, 0.0, -2.5, 0.0, 0.0, 0.0},
            /* Падение */ {-4.0, 2.5, 5.5, -0.8, 1.0, 1.5, -2.5, -0.5, 0.0, 0.0},
            /* Прочее  */ {-4.5, 3.0, 3.5, -0.8, 0.0, 0.0, -2.5, 0.0, 0.0, 0.0}
    };
    private static final int VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AiBrain inst;

    public final double[][] w = new double[S][N];
    public final long[] n = new long[S];            // сколько раз модель ситуации обучалась
    public final long[] userHitsBy = new long[S];   // твои удары по ситуациям
    public final double[] cdEma = new double[S];    // средний заряд при твоих ударах
    public long samples, userHits, botHits, botGood, botBad;
    private int sinceSave = 0;

    public static AiBrain get() {
        if (inst == null) {
            inst = new AiBrain();
            inst.load();
        }
        return inst;
    }

    private AiBrain() {
        resetWeights();
    }

    private void resetWeights() {
        for (int s = 0; s < S; s++) System.arraycopy(PRIOR[s], 0, w[s], 0, N);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("bogempty_ai.json");
    }

    public double predict(int sit, double[] x) {
        double z = 0;
        for (int i = 0; i < N; i++) z += w[sit][i] * x[i];
        return 1.0 / (1.0 + Math.exp(-z));
    }

    public void train(int sit, double[] x, double y, double weight, double lr) {
        double p = predict(sit, x);
        double step = lr * Math.max(0.2, 1.0 / Math.sqrt(1.0 + n[sit] / 150.0)) * weight;
        double err = y - p;
        for (int i = 0; i < N; i++) {
            w[sit][i] += step * err * x[i] - step * 0.002 * (w[sit][i] - PRIOR[sit][i]);
            w[sit][i] = Math.max(-12.0, Math.min(12.0, w[sit][i]));
        }
        n[sit]++;
        samples++;
        if (++sinceSave >= 150) save();
    }

    /** Запоминает твой удар: в какой ситуации и с каким зарядом. */
    public void noteUserHit(int sit, double cd) {
        userHits++;
        userHitsBy[sit]++;
        cdEma[sit] = userHitsBy[sit] == 1 ? cd : cdEma[sit] * 0.9 + cd * 0.1;
    }

    public double accuracy() {
        long t = botGood + botBad;
        return t == 0 ? 0.0 : (double) botGood / t;
    }

    public double maturity() {
        return samples / (samples + 300.0);
    }

    public void reset() {
        resetWeights();
        for (int s = 0; s < S; s++) {
            n[s] = 0;
            userHitsBy[s] = 0;
            cdEma[s] = 0;
        }
        samples = 0;
        userHits = 0;
        botHits = 0;
        botGood = 0;
        botBad = 0;
        save();
    }

    public void save() {
        sinceSave = 0;
        try {
            JsonObject o = new JsonObject();
            o.addProperty("v", VERSION);
            JsonArray rows = new JsonArray();
            for (int s = 0; s < S; s++) {
                JsonArray a = new JsonArray();
                for (double d : w[s]) a.add(d);
                rows.add(a);
            }
            o.add("w", rows);
            o.add("n", longs(n));
            o.add("hitsBy", longs(userHitsBy));
            JsonArray ce = new JsonArray();
            for (double d : cdEma) ce.add(d);
            o.add("cdEma", ce);
            o.addProperty("samples", samples);
            o.addProperty("userHits", userHits);
            o.addProperty("botHits", botHits);
            o.addProperty("botGood", botGood);
            o.addProperty("botBad", botBad);
            Files.writeString(file(), GSON.toJson(o));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JsonArray longs(long[] arr) {
        JsonArray a = new JsonArray();
        for (long v : arr) a.add(v);
        return a;
    }

    private void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            // старый формат (одна линейная модель) не подходит: начинаем с чистого листа
            if (!o.has("v") || o.get("v").getAsInt() != VERSION) return;

            JsonArray rows = o.getAsJsonArray("w");
            for (int s = 0; s < S && s < rows.size(); s++) {
                JsonArray a = rows.get(s).getAsJsonArray();
                if (a.size() != N) continue;
                for (int i = 0; i < N; i++) w[s][i] = a.get(i).getAsDouble();
            }
            readLongs(o, "n", n);
            readLongs(o, "hitsBy", userHitsBy);
            if (o.has("cdEma")) {
                JsonArray a = o.getAsJsonArray("cdEma");
                for (int s = 0; s < S && s < a.size(); s++) cdEma[s] = a.get(s).getAsDouble();
            }
            if (o.has("samples")) samples = o.get("samples").getAsLong();
            if (o.has("userHits")) userHits = o.get("userHits").getAsLong();
            if (o.has("botHits")) botHits = o.get("botHits").getAsLong();
            if (o.has("botGood")) botGood = o.get("botGood").getAsLong();
            if (o.has("botBad")) botBad = o.get("botBad").getAsLong();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void readLongs(JsonObject o, String key, long[] out) {
        if (!o.has(key)) return;
        int i = 0;
        for (JsonElement el : o.getAsJsonArray(key)) {
            if (i >= out.length) break;
            out[i++] = el.getAsLong();
        }
    }
}