package dev.jade.labsaddons.gametest.mixin;

import dev.jade.labsaddons.gametest.Probe;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Times the vanilla HUD, so the mod's cost can be read against what the game already spends
 * there. It stops where the mod's pass starts; see {@code HudTimingMixin}.
 */
@Mixin(InGameHud.class)
public abstract class VanillaHudTimingMixin {
	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
			at = @At("HEAD"))
	private void labsaddonsTest$begin(DrawContext context, RenderTickCounter delta, CallbackInfo ci) {
		Probe.VANILLA_HUD.begin();
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
			at = @At("RETURN"))
	private void labsaddonsTest$end(DrawContext context, RenderTickCounter delta, CallbackInfo ci) {
		Probe.VANILLA_HUD.end();
	}
}
