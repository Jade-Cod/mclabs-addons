package dev.jade.labsaddons.mixin;

import dev.jade.labsaddons.double2.D2Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the chest texture when the Double² board is standing in for the menu.
 *
 * <p>{@code drawBackground} is reached from {@code renderBackground}, which runs
 * <em>before</em> {@code render} — so cancelling the render alone left the container's
 * outline framing the board. Only this call is suppressed, which leaves Minecraft's own
 * screen dim and blur in place: the board then sits on exactly the backdrop every other
 * menu gets, instead of on that plus one of its own.
 */
@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixin {
	@Inject(
			method = "drawBackground(Lnet/minecraft/client/gui/DrawContext;FII)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void labsaddons$hideChestTexture(DrawContext context, float deltaTicks,
			int mouseX, int mouseY, CallbackInfo ci) {
		if (D2Screen.recognises((HandledScreen<?>) (Object) this)) {
			ci.cancel();
		}
	}
}
