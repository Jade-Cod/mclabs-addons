package dev.jade.labsaddons.gametest.mixin;

import dev.jade.labsaddons.gametest.Probe;
import dev.jade.labsaddons.hud.HudObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times each widget's live draw, so a slow HUD pass names the widget behind it. */
@Mixin(value = HudObject.class, remap = false)
public abstract class WidgetTimingMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void labsaddonsTest$begin(GuiGraphicsExtractor context, boolean preview, CallbackInfo ci) {
		if (!preview) {
			Probe.widget(((HudObject) (Object) this).id()).begin();
		}
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void labsaddonsTest$end(GuiGraphicsExtractor context, boolean preview, CallbackInfo ci) {
		if (!preview) {
			Probe.widget(((HudObject) (Object) this).id()).end();
		}
	}
}
