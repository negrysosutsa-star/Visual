
package ru.bogempty.client.module.visual;

import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

public class Freecam extends Module {

    private final Setting.Num speed =
            add(new Setting.Num("Speed", 0.5, 0.1, 3.0, 0.1),
                    "Скорость свободной камеры");

    private Vec3d cameraPosition;
    private float cameraYaw;
    private float cameraPitch;

    public Freecam() {
        super("Freecam", "Свободное перемещение камеры", Category.VISUAL);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.gameRenderer == null) {
            setEnabled(false);
            return;
        }

        cameraPosition = mc.gameRenderer.getCamera().getPos();
        cameraYaw = mc.player.getYaw();
        cameraPitch = mc.player.getPitch();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || cameraPosition == null) {
            setEnabled(false);
            return;
        }

        long window = mc.getWindow().getHandle();

        double forward = 0;
        double strafe = 0;
        double vertical = 0;

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) forward++;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) forward--;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) strafe++;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) strafe--;

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) vertical++;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) vertical--;

        double yaw = Math.toRadians(cameraYaw);

        double dx = -Math.sin(yaw) * forward + Math.cos(yaw) * strafe;
        double dz = Math.cos(yaw) * forward + Math.sin(yaw) * strafe;

        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > 0) {
            dx /= length;
            dz /= length;
        }

        double velocity = speed.get();

        cameraPosition = cameraPosition.add(
                dx * velocity,
                vertical * velocity,
                dz * velocity
        );

        cameraYaw = mc.player.getYaw();
        cameraPitch = mc.player.getPitch();
    }

    public Vec3d getCameraPosition() {
        return cameraPosition;
    }

    public float getCameraYaw() {
        return cameraYaw;
    }

    public float getCameraPitch() {
        return cameraPitch;
    }

    @Override
    public void onDisable() {
        cameraPosition = null;
    }
}
