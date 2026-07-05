package dev.jade.fishbite.mixin;

import dev.jade.fishbite.item.ItemUses;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws remaining "uses" on single charge items in the top-left corner of
 * the slot, scaled down so 3-digit counts don't spill into the next slot.
 * drawStackOverlay is the one method every slot render (hotbar, inventory,
 * any container screen) funnels through, so one hook covers all of them.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
	private static final float USES_TEXT_SCALE = 0.5f;
	private static final int USES_TEXT_COLOR = 0xFF55FF55;

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
		Matrix3x2fStack matrices = self.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x + 2, y + 2);
		matrices.scale(USES_TEXT_SCALE);
		self.drawText(textRenderer, String.valueOf(uses), 0, 0, USES_TEXT_COLOR, true);
		matrices.popMatrix();
	}
}
