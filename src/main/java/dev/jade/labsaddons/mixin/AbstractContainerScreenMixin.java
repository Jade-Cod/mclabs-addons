package dev.jade.labsaddons.mixin;

import dev.jade.labsaddons.casino.CasinoBoards;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks for the casino boards, which stand in for the server's chest menus.
 *
 * <p>Fabric's screen API would be the tidier home, but in this version its render events
 * hand out a {@code GuiGraphicsExtractor} rather than the {@link GuiGraphicsExtractor} every other
 * bit of drawing in this mod speaks. Injecting into the screen's own methods needs no
 * translation layer, and is the same trade {@link HudMixin} already makes.
 *
 * <p>Every hook no-ops unless some board in {@link CasinoBoards} recognises the open
 * container, so every other menu in the game behaves exactly as it did.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	/** The slot under the cursor. Assigned only by the render we cancel — see below. */
	@Shadow
	protected Slot hoveredSlot;

	/**
	 * Draws the board in place of the menu's <em>entire</em> render.
	 *
	 * <p>Cancelling the whole method rather than just its main pass is deliberate. The
	 * chest frame and the slot tooltips are drawn outside that pass, so suppressing only
	 * the slots left an empty container outline framing the board and a tooltip hanging
	 * off the cursor. The board supplies its own screen dim in exchange.
	 */
	@Inject(
			method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void labsaddons$drawCasinoBoard(GuiGraphicsExtractor context, int mouseX, int mouseY,
			float deltaTicks, CallbackInfo ci) {
		if (CasinoBoards.render((AbstractContainerScreen<?>) (Object) this, context, mouseX, mouseY)) {
			// The slot under the cursor is only ever assigned by the render being cancelled
			// here, so left alone it keeps whatever was under the cursor on the last frame
			// vanilla drew — and the menu's own keyPressed still acts on it. The drop key
			// or a hotbar number would then send a real click to a slot the board has
			// hidden, which on these menus is a wager. Clearing it covers every reader of
			// the field rather than only the two we would have thought to guard.
			this.hoveredSlot = null;
			ci.cancel();
		}
	}

	/**
	 * Drops the slot tooltip while the board is up.
	 *
	 * <p>{@code ContainerScreen#render} calls this <em>after</em> the super call we
	 * cancel, so suppressing the render alone still left a tooltip hanging off the cursor
	 * naming whatever decorative pane sat under it.
	 */
	@Inject(
			method = "extractTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void labsaddons$hideSlotTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY,
			CallbackInfo ci) {
		if (CasinoBoards.recognises((AbstractContainerScreen<?>) (Object) this)) {
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
		if (CasinoBoards.mouseScrolled((AbstractContainerScreen<?>) (Object) this, verticalAmount)) {
			cir.setReturnValue(true);
		}
	}

	/**
	 * Swallows clicks while the board is up. Our own controls forward a real click to the
	 * slot a player would have hit; anything else is dropped, because the container's
	 * slots are still live underneath and a stray click would place a bet.
	 */
	@Inject(
			method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void labsaddons$clickCasinoBoard(MouseButtonEvent click, boolean doubled,
			CallbackInfoReturnable<Boolean> cir) {
		if (CasinoBoards.mouseClicked((AbstractContainerScreen<?>) (Object) this, click.x(), click.y(),
				click.button())) {
			cir.setReturnValue(true);
		}
	}

	/**
	 * Swallows the release that ends one of those clicks.
	 *
	 * <p>Vanilla's release finds its slot by mouse position rather than through
	 * {@code hoveredSlot}, and clicks it whenever the cursor holds anything. The boards no
	 * longer put anything there (see {@code CasinoPanel#sendClick}), but when they did, a
	 * release over your own inventory had the server lift one of your items and hand it back
	 * to the hotbar. Nothing a board draws is a real slot, so nothing under one is released on.
	 */
	@Inject(
			method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void labsaddons$releaseOverCasinoBoard(MouseButtonEvent click,
			CallbackInfoReturnable<Boolean> cir) {
		if (CasinoBoards.recognises((AbstractContainerScreen<?>) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	/** The same for a drag, which with a full cursor spreads it across the slots it crosses. */
	@Inject(
			method = "mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void labsaddons$dragOverCasinoBoard(MouseButtonEvent click, double offsetX,
			double offsetY, CallbackInfoReturnable<Boolean> cir) {
		if (CasinoBoards.recognises((AbstractContainerScreen<?>) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
