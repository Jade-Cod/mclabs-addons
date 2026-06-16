package dev.jade.fishbite.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the camera's world position (no public getter in 1.21.11). */
@Mixin(Camera.class)
public interface CameraAccessor {
	@Accessor("pos")
	Vec3d getPos();
}
