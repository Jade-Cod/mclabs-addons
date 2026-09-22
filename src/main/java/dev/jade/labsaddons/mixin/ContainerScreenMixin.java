package dev.jade.labsaddons.mixin;

import dev.jade.labsaddons.casino.CasinoBoards;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the chest texture when a casino board is standing in for the menu.
 *
 * <p>{@code drawBackground} is reached from {@code renderBackground}, which runs
 * <em>before</em> {@code render} — so cancelling the render alone left the container's
 * outline framing the board. Only this call is suppressed, which leaves Minecraft's own
 * screen dim and blur in place: the board then sits on exactly the backdrop every other
 * menu gets, instead of on that plus one of its own.
 */
@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin {
	@Inject(
			method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void labsaddons$hideChestTexture(GuiGraphicsExtractor context, int mouseX,
			int mouseY, float deltaTicks, CallbackInfo ci) {
		if (CasinoBoards.recognises((AbstractContainerScreen<?>) (Object) this)) {
			ci.cancel();
		}
	}
}
