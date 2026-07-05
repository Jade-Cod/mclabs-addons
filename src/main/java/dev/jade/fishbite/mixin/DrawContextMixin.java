package dev.jade.fishbite.mixin;

import dev.jade.fishbite.item.ItemUses;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors vanilla's stack-count text (bottom-right) onto the bottom-left to
 * show remaining "uses" on single charge items. drawStackOverlay is the one
 * method every slot render (hotbar, inventory, any container screen) funnels
 * through, so one hook covers all of them.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
	@Inject(
			method = "drawStackOverlay(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
			at = @At("TAIL")
	)
	private void fishbite$drawRemainingUses(TextRenderer textRenderer, ItemStack stack,
			int x, int y, String countOverride, CallbackInfo ci) {
		int uses = ItemUses.remaining(stack);
		if (uses < 0) {
			return;
		}
		DrawContext self = (DrawContext) (Object) this;
		self.drawText(textRenderer, String.valueOf(uses), x + 2, y + 9, -1, true);
	}
}
