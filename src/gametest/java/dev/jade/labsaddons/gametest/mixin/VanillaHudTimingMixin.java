package dev.jade.labsaddons.gametest.mixin;

import dev.jade.labsaddons.gametest.Probe;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Times the vanilla HUD, so the mod's cost can be read against what the game already spends
 * there. It stops where the mod's pass starts; see {@code HudTimingMixin}.
 */
@Mixin(Hud.class)
public abstract class VanillaHudTimingMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			at = @At("HEAD"))
	private void labsaddonsTest$begin(GuiGraphicsExtractor context, DeltaTracker delta, CallbackInfo ci) {
		Probe.VANILLA_HUD.begin();
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			at = @At("RETURN"))
	private void labsaddonsTest$end(GuiGraphicsExtractor context, DeltaTracker delta, CallbackInfo ci) {
		Probe.VANILLA_HUD.end();
	}
}
