package dev.jade.labsaddons.gametest.mixin;

import dev.jade.labsaddons.gametest.Probe;
import dev.jade.labsaddons.hud.HudRenderDispatcher;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times the mod's whole per-frame HUD pass: the bite marker and every widget. */
@Mixin(value = HudRenderDispatcher.class, remap = false)
public abstract class HudTimingMixin {
	@Inject(method = "renderAll", at = @At("HEAD"))
	private static void labsaddonsTest$begin(DrawContext context, RenderTickCounter delta, CallbackInfo ci) {
		// Vanilla's HUD ends where ours begins. Its own end at the method's return may run
		// before or after this pass, depending on injector order, so this settles it.
		Probe.VANILLA_HUD.end();
		Probe.HUD.begin();
	}

	@Inject(method = "renderAll", at = @At("RETURN"))
	private static void labsaddonsTest$end(DrawContext context, RenderTickCounter delta, CallbackInfo ci) {
		Probe.HUD.end();
	}
}
