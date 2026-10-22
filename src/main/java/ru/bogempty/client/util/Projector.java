package ru.bogempty.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;

/** Перевод точки мира в координаты экрана по настоящей камере и настоящему FOV. */
public final class Projector {
    private static Method fovMethod;
    private static boolean fovSearched = false;

    private Vec3d camPos = Vec3d.ZERO;
    private Quaternionf inv = new Quaternionf();
    private double tanHalf = 1.0;
    private double aspect = 1.0;
    public int sw, sh;

    public boolean begin(MinecraftClient mc, DrawContext ctx, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.gameRenderer == null) return false;
        Camera cam = mc.gameRenderer.getCamera();
        camPos = cam.getPos();
        inv = new Quaternionf(cam.getRotation()).conjugate();
        tanHalf = Math.tan(Math.toRadians(fov(mc, cam, tickDelta)) * 0.5);
        aspect = (double) mc.getWindow().getFramebufferWidth() / Math.max(1, mc.getWindow().getFramebufferHeight());
        sw = ctx.getScaledWindowWidth();
        sh = ctx.getScaledWindowHeight();
        return true;
    }

    /** {x, y, глубина} или null, если точка позади камеры. */
    public float[] project(double x, double y, double z) {
        Vector3f v = new Vector3f((float) (x - camPos.x), (float) (y - camPos.y), (float) (z - camPos.z));
        inv.transform(v);
        double depth = -v.z;
        if (depth < 0.1) return null;
        double nx = v.x / (depth * tanHalf * aspect);
        double ny = v.y / (depth * tanHalf);
        return new float[]{(float) ((nx * 0.5 + 0.5) * sw), (float) ((0.5 - ny * 0.5) * sh), (float) depth};
    }

    private static double fov(MinecraftClient mc, Camera cam, float tickDelta) {
        try {
            if (!fovSearched) {
                fovSearched = true;
                for (Method m : GameRenderer.class.getDeclaredMethods()) {
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length == 3 && pt[0] == Camera.class && pt[1] == float.class && pt[2] == boolean.class
                            && (m.getReturnType() == double.class || m.getReturnType() == float.class)) {
                        m.setAccessible(true);
                        fovMethod = m;
                        break;
                    }
                }
            }
            if (fovMethod != null) {
                return ((Number) fovMethod.invoke(mc.gameRenderer, cam, tickDelta, true)).doubleValue();
            }
        } catch (Throwable ignored) {
        }
        return mc.options.getFov().getValue();
    }
}