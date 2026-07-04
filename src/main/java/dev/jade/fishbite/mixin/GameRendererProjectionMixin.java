package dev.jade.fishbite.mixin;

import dev.jade.fishbite.RenderFrameState;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Captures the real, render-time projection matrix straight from {@code renderWorld} instead of
 * having the bite marker recompute it via a second {@code getFov} call. A second call matched
 * vanilla's own arguments exactly (same camera, same tick progress, same {@code changingFov}
 * flag) but still drifted from the true value while a third-party zoom mod's eased FOV
 * transition was active, since such mods can key their interpolation off render-order-sensitive
 * state that a replayed call doesn't reproduce. Redirecting the call and forwarding its own
 * return value is exact by construction: it's the same matrix the frame actually uses.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererProjectionMixin {
	@Redirect(method = "renderWorld", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/GameRenderer;getBasicProjectionMatrix(F)Lorg/joml/Matrix4f;"))
	private Matrix4f fishbite$captureProjection(GameRenderer instance, float fov) {
		Matrix4f real = instance.getBasicProjectionMatrix(fov);
		RenderFrameState.PROJECTION.set(real);
		return real;
	}
}
