package dev.jade.labsaddons.mixin;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.item.ItemUses;
import dev.jade.labsaddons.item.ItemUsesCorner;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws remaining "uses" on single charge items in a corner of the slot,
 * scaled down so multi-digit counts don't spill into the next slot.
 * drawStackOverlay is the one method every slot render (hotbar, inventory,
 * any container screen) funnels through, so one hook covers all of them.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
	private static final int SLOT_SIZE = 16;
	private static final int INSET = 2;

	@Inject(
			method = "drawStackOverlay(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
			at = @At("TAIL")
	)
	private void fishbite$drawRemainingUses(TextRenderer textRenderer, ItemStack stack,
			int x, int y, String countOverride, CallbackInfo ci) {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		if (!config.itemUsesEnabled) {
			return;
		}
		int uses = ItemUses.remaining(stack);
		if (uses < 0) {
			return;
		}
		String text = String.valueOf(uses);
		float scale = config.itemUsesScale;
		ItemUsesCorner corner = ItemUsesCorner.valueOf(config.itemUsesCorner);
		boolean right = corner == ItemUsesCorner.TOP_RIGHT || corner == ItemUsesCorner.BOTTOM_RIGHT;
		boolean bottom = corner == ItemUsesCorner.BOTTOM_LEFT || corner == ItemUsesCorner.BOTTOM_RIGHT;
		int textX = right
				? x + SLOT_SIZE - INSET - Math.round(textRenderer.getWidth(text) * scale)
				: x + INSET;
		int textY = bottom
				? y + SLOT_SIZE - INSET - Math.round(textRenderer.fontHeight * scale)
				: y + INSET;

		DrawContext self = (DrawContext) (Object) this;
		Matrix3x2fStack matrices = self.getMatrices();
		matrices.pushMatrix();
		matrices.translate(textX, textY);
		matrices.scale(scale);
		self.drawText(textRenderer, text, 0, 0, config.itemUsesColor, true);
		matrices.popMatrix();
	}
}
