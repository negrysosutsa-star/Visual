
package ru.bogempty.client.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {

    @Invoker("setPos")
    void bogempty$invokeSetPos(double x, double y, double z);

    @Invoker("setRotation")
    void bogempty$invokeSetRotation(float yaw, float pitch);
}
