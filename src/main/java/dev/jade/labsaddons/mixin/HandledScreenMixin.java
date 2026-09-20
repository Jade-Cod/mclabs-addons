package dev.jade.labsaddons.mixin;

import dev.jade.labsaddons.casino.CasinoBoards;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks for the casino boards, which stand in for the server's chest menus.
 *
 * <p>Fabric's screen API would be the tidier home, but in this version its render events
 * hand out a {@code GuiGraphicsExtractor} rather than the {@link DrawContext} every other
 * bit of drawing in this mod speaks. Injecting into the screen's own methods needs no
 * translation layer, and is the same trade {@link InGameHudMixin} already makes.
 *
 * <p>Every hook no-ops unless some board in {@link CasinoBoards} recognises the open
 * container, so every other menu in the game behaves exactly as it did.
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
	private void labsaddons$drawCasinoBoard(DrawContext context, int mouseX, int mouseY,
			float deltaTicks, CallbackInfo ci) {
		if (CasinoBoards.render((HandledScreen<?>) (Object) this, context, mouseX, mouseY)) {
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
		if (CasinoBoards.recognises((HandledScreen<?>) (Object) this)) {
			ci.cancel();
		}
	}

	/**
	 * Gives the wheel to the board, so the coinflip lobby can scroll a list longer than it
	 * can show. Every other board ignores it and the menu behaves as it always did.
	 */
	@Inject(
			method = "mouseScrolled(DDDD)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void labsaddons$scrollCasinoBoard(double mouseX, double mouseY,
			double horizontalAmount, double verticalAmount,
			CallbackInfoReturnable<Boolean> cir) {
		if (CasinoBoards.mouseScrolled((HandledScreen<?>) (Object) this, verticalAmount)) {
			cir.setReturnValue(true);
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
	private void labsaddons$clickCasinoBoard(Click click, boolean doubled,
			CallbackInfoReturnable<Boolean> cir) {
		if (CasinoBoards.mouseClicked((HandledScreen<?>) (Object) this, click.x(), click.y())) {
			cir.setReturnValue(true);
		}
	}
}
