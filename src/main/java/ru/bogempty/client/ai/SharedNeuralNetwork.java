package ru.bogempty.client.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Small shared online MLP for client-side prediction.
 * Inputs are normalized to [0,1]. Outputs are confidence and quality.
 */
public final class SharedNeuralNetwork {
    public static final int INPUTS = 10;
    private static final int HIDDEN = 12;
    private static final double RATE = 0.015;
    private static final SharedNeuralNetwork INSTANCE = new SharedNeuralNetwork();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final double[][] w1 = new double[HIDDEN][INPUTS];
    private final double[] b1 = new double[HIDDEN];
    private final double[] w2 = new double[2 * HIDDEN];
    private final double[] b2 = new double[2];
    private long samples;
    private long correct;
    private double lossEma = 0.25;

    public static SharedNeuralNetwork get() { return INSTANCE; }

    private SharedNeuralNetwork() {
        java.util.Random random = new java.util.Random(0xB06E_2026L);
        for (int h = 0; h < HIDDEN; h++) {
            for (int i = 0; i < INPUTS; i++) w1[h][i] = (random.nextDouble() - 0.5) * 0.18;
            for (int o = 0; o < 2; o++) w2[o * HIDDEN + h] = (random.nextDouble() - 0.5) * 0.18;
        }
        load();
    }

    public synchronized double[] predict(double[] features) {
        double[] x = normalize(features);
        double[] hidden = new double[HIDDEN];
        for (int h = 0; h < HIDDEN; h++) {
            double z = b1[h];
            for (int i = 0; i < INPUTS; i++) z += w1[h][i] * x[i];
            hidden[h] = Math.tanh(z);
        }
        double[] out = new double[2];
        for (int o = 0; o < 2; o++) {
            double z = b2[o];
            for (int h = 0; h < HIDDEN; h++) z += w2[o * HIDDEN + h] * hidden[h];
            out[o] = sigmoid(z);
        }
        return out;
    }

    public synchronized void train(double[] features, double confidenceTarget, double qualityTarget, double weight) {
        double[] x = normalize(features);
        confidenceTarget = clamp(confidenceTarget, 0, 1);
        qualityTarget = clamp(qualityTarget, 0, 1);
        weight = clamp(weight, 0.01, 1.0);
        double[] hidden = new double[HIDDEN];
        for (int h = 0; h < HIDDEN; h++) {
            double z = b1[h];
            for (int i = 0; i < INPUTS; i++) z += w1[h][i] * x[i];
            hidden[h] = Math.tanh(z);
        }
        double[] targets = {confidenceTarget, qualityTarget};
        double[] deltaOut = new double[2];
        double loss = 0;
        for (int o = 0; o < 2; o++) {
            double z = b2[o];
            for (int h = 0; h < HIDDEN; h++) z += w2[o * HIDDEN + h] * hidden[h];
            double pred = sigmoid(z);
            double error = pred - targets[o];
            loss += error * error;
            deltaOut[o] = error * pred * (1.0 - pred) * weight;
        }
        double[] deltaHidden = new double[HIDDEN];
        for (int h = 0; h < HIDDEN; h++) {
            double back = 0;
            for (int o = 0; o < 2; o++) back += w2[o * HIDDEN + h] * deltaOut[o];
            deltaHidden[h] = back * (1.0 - hidden[h] * hidden[h]);
        }
        for (int o = 0; o < 2; o++) {
            for (int h = 0; h < HIDDEN; h++) w2[o * HIDDEN + h] -= RATE * deltaOut[o] * hidden[h];
            b2[o] -= RATE * deltaOut[o];
        }
        for (int h = 0; h < HIDDEN; h++) {
            for (int i = 0; i < INPUTS; i++) w1[h][i] -= RATE * deltaHidden[h] * x[i];
            b1[h] -= RATE * deltaHidden[h];
        }
        samples++;
        if (Math.abs(predict(x)[0] - confidenceTarget) < 0.25) correct++;
        lossEma = lossEma * 0.97 + (loss / 2.0) * 0.03;
        if (samples % 20 == 0) save();
    }

    public synchronized long samples() { return samples; }
    public synchronized double accuracy() { return samples == 0 ? 0 : (double) correct / samples; }
    public synchronized double loss() { return lossEma; }

    private static double[] normalize(double[] input) {
        double[] x = new double[INPUTS];
        if (input != null) for (int i = 0; i < Math.min(INPUTS, input.length); i++) {
            double v = input[i];
            x[i] = Double.isFinite(v) ? clamp(v, -1.0, 1.0) : 0.0;
        }
        return x;
    }
    private static double sigmoid(double x) { return 1.0 / (1.0 + Math.exp(-clamp(x, -20, 20))); }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("bogempty_shared_nn.json"); }

    public synchronized void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("samples", samples);
            root.addProperty("correct", correct);
            root.addProperty("loss", lossEma);
            JsonArray a1 = new JsonArray();
            for (double[] row : w1) { JsonArray rowJson = new JsonArray(); for (double v : row) rowJson.add(v); a1.add(rowJson); }
            root.add("w1", a1);
            JsonArray ba = new JsonArray(); for (double v : b1) ba.add(v); root.add("b1", ba);
            JsonArray a2 = new JsonArray(); for (double v : w2) a2.add(v); root.add("w2", a2);
            JsonArray b2a = new JsonArray(); for (double v : b2) b2a.add(v); root.add("b2", b2a);
            Files.writeString(path(), GSON.toJson(root));
        } catch (Exception ignored) { }
    }

    private void load() {
        try {
            Path p = path();
            if (!Files.exists(p)) return;
            JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            JsonArray rows = root.getAsJsonArray("w1");
            if (rows == null || rows.size() != HIDDEN) return;
            for (int h = 0; h < HIDDEN; h++) {
                JsonArray row = rows.get(h).getAsJsonArray();
                if (row.size() != INPUTS) return;
                for (int i = 0; i < INPUTS; i++) w1[h][i] = clamp(row.get(i).getAsDouble(), -5, 5);
            }
            JsonArray ba = root.getAsJsonArray("b1"), a2 = root.getAsJsonArray("w2"), b2a = root.getAsJsonArray("b2");
            if (ba.size() != HIDDEN || a2.size() != 2 * HIDDEN || b2a.size() != 2) return;
            for (int i = 0; i < HIDDEN; i++) b1[i] = clamp(ba.get(i).getAsDouble(), -5, 5);
            for (int i = 0; i < 2 * HIDDEN; i++) w2[i] = clamp(a2.get(i).getAsDouble(), -5, 5);
            for (int i = 0; i < 2; i++) b2[i] = clamp(b2a.get(i).getAsDouble(), -5, 5);
            samples = Math.max(0, root.get("samples").getAsLong());
            correct = Math.max(0, root.get("correct").getAsLong());
            lossEma = clamp(root.get("loss").getAsDouble(), 0, 1);
        } catch (Exception ignored) { }
    }
}