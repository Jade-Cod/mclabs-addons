package dev.jade.labsaddons.mixin;

import dev.jade.labsaddons.double2.D2Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks for the Double² board, which stands in for the server's chest menu.
 *
 * <p>Fabric's screen API would be the tidier home, but in this version its render events
 * hand out a {@code GuiGraphicsExtractor} rather than the {@link DrawContext} every other
 * bit of drawing in this mod speaks. Injecting into the screen's own methods needs no
 * translation layer, and is the same trade {@link InGameHudMixin} already makes.
 *
 * <p>Both hooks no-op unless {@link D2Screen} recognises the open container, so every
 * other menu in the game behaves exactly as it did.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
	/**
	 * Draws the board in place of the menu's <em>entire</em> render.
	 *
	 * <p>Cancelling the whole method rather than just its main pass is deliberate. The
	 * chest frame and the slot tooltips are drawn outside that pass, so suppressing only
	 * the slots left an empty container outline framing the board and a tooltip hanging
	 * off the cursor. The board supplies its own screen dim in exchange.
	 */
	@Inject(
			method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void labsaddons$drawDouble2(DrawContext context, int mouseX, int mouseY,
			float deltaTicks, CallbackInfo ci) {
		if (D2Screen.render((HandledScreen<?>) (Object) this, context, mouseX, mouseY)) {
			ci.cancel();
		}
	}

	/**
	 * Drops the slot tooltip while the board is up.
	 *
	 * <p>{@code GenericContainerScreen#render} calls this <em>after</em> the super call we
	 * cancel, so suppressing the render alone still left a tooltip hanging off the cursor
	 * naming whatever decorative pane sat under it.
	 */
	@Inject(
			method = "drawMouseoverTooltip(Lnet/minecraft/client/gui/DrawContext;II)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void labsaddons$hideSlotTooltip(DrawContext context, int mouseX, int mouseY,
			CallbackInfo ci) {
		if (D2Screen.recognises((HandledScreen<?>) (Object) this)) {
			ci.cancel();
		}
	}

	/**
	 * Swallows clicks while the board is up. Our own controls forward a real click to the
	 * slot a player would have hit; anything else is dropped, because the container's
	 * slots are still live underneath and a stray click would place a bet.
	 */
	@Inject(
			method = "mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void labsaddons$clickDouble2(Click click, boolean doubled,
			CallbackInfoReturnable<Boolean> cir) {
		if (D2Screen.mouseClicked((HandledScreen<?>) (Object) this, click.x(), click.y())) {
			cir.setReturnValue(true);
		}
	}
}
