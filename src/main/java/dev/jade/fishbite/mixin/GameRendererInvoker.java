package dev.jade.fishbite.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes GameRenderer's private getFov (no public equivalent in 1.21.11). */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
	@Invoker("getFov")
	float invokeGetFov(Camera camera, float tickProgress, boolean changingFov);
}
