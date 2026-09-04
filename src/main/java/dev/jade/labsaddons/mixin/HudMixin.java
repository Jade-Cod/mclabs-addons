package dev.jade.labsaddons.mixin;

import dev.jade.labsaddons.hud.HudRenderDispatcher;
import dev.jade.labsaddons.mcmmo.McmmoCooldownTracker;
import dev.jade.labsaddons.pititem.PitItemCooldownTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws all labsaddons HUD elements at the tail of the vanilla HUD render.
 *
 * <p>This replaces Fabric's {@code HudElementRegistry.addLast} dispatch, which
 * client overlays such as Feather break by replacing the per-element vanilla
 * render anchors Fabric hangs its layers off of. Injecting at the return of
 * {@link Hud#render} is anchored to the method itself, not to any single
 * vanilla element, so it survives the overlay and still runs in plain vanilla.
 */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			at = @At("TAIL")
	)
	private void labsaddons$renderHudElements(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
		HudRenderDispatcher.renderAll(context, tickCounter);
	}

	/**
	 * Actionbar text can arrive two ways: system chat with the overlay flag, or
	 * the dedicated Set Action Bar Component packet. Fabric's
	 * {@code ClientReceiveMessageEvents.GAME} only fires for the former — mcMMO
	 * on Paper uses the packet, so both paths are captured here where they
	 * converge instead.
	 */
	@Inject(
			method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V",
			at = @At("HEAD")
	)
	private void labsaddons$captureOverlayMessage(Component message, boolean tinted, CallbackInfo ci) {
		if (message != null) {
			McmmoCooldownTracker.onMessage(message.getString());
			PitItemCooldownTracker.onMessage(message.getString());
		}
	}
}
