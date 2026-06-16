package dev.jade.fishbite.mixin;

import dev.jade.fishbite.hud.HudRenderDispatcher;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws all fishbite HUD elements at the tail of the vanilla HUD render.
 *
 * <p>This replaces Fabric's {@code HudElementRegistry.addLast} dispatch, which
 * client overlays such as Feather break by replacing the per-element vanilla
 * render anchors Fabric hangs its layers off of. Injecting at the return of
 * {@link InGameHud#render} is anchored to the method itself, not to any single
 * vanilla element, so it survives the overlay and still runs in plain vanilla.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Inject(
			method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
			at = @At("TAIL")
	)
	private void fishbite$renderHudElements(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		HudRenderDispatcher.renderAll(context, tickCounter);
	}
}
