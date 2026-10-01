package dev.jade.labsaddons.hud.editor;

import dev.jade.labsaddons.hud.GuiElements;
import dev.jade.labsaddons.hud.HudObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Stateless drawing primitives for the HUD Studio editor. The screen owns layout
 * and state; this class only paints pixels (using {@code fill} since 1.21.11's
 * {@code GuiGraphicsExtractor} has no {@code drawBorder}). Reuses
 * {@link HudObject#drawRoundedRect} for soft corners. Nothing here allocates
 * long-lived state, and none of it runs outside the open editor screen.
 */
public final class EditorPainter {
	private EditorPainter() {
	}

	/** Faint alignment grid across the whole screen. */
	public static void gridOverlay(GuiGraphicsExtractor ctx, int width, int height, int step, int color) {
		for (int x = step; x < width; x += step) {
			ctx.fill(x, 0, x + 1, height, color);
		}
		for (int y = step; y < height; y += step) {
			ctx.fill(0, y, width, y + 1, color);
		}
	}

	/** 1px rectangular frame. */
	public static void outline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
		ctx.fill(x, y, x + w, y + 1, color);
		ctx.fill(x, y + h - 1, x + w, y + h, color);
		ctx.fill(x, y, x + 1, y + h, color);
		ctx.fill(x + w - 1, y, x + w, y + h, color);
	}

	/** A small square resize grip: dark border behind an accent core, drawn around (cx,cy). */
	public static void resizeHandle(GuiGraphicsExtractor ctx, int cx, int cy, int size, int fill, int border) {
		int half = size / 2;
		ctx.fill(cx - half - 1, cy - half - 1, cx + half + 1, cy + half + 1, border);
		ctx.fill(cx - half, cy - half, cx + half, cy + half, fill);
	}

	/** A readable name pill: rounded translucent background + shadowed text. */
	public static void nameChip(GuiGraphicsExtractor ctx, Font tr, Component label, int x, int y, int textColor) {
		int w = tr.width(label);
		HudObject.drawRoundedRect(ctx, x - 3, y - 2, w + 6, tr.lineHeight + 3, EditorTheme.CHIP_BG);
		ctx.text(tr, label, x, y, textColor, true);
	}

	/** Rounded panel fill + a crisp 1px border. */
	public static void panel(GuiGraphicsExtractor ctx, int[] rect, int bg, int border) {
		HudObject.drawRoundedRect(ctx, rect[0], rect[1], rect[2], rect[3], bg);
		outline(ctx, rect[0], rect[1], rect[2], rect[3], border);
	}

	/**
	 * A stadium/pill shape: a rect with fully rounded, anti-aliased ends, drawn as one GUI
	 * element (see {@link PillRenderState}). Falls back to a plain rect if the element can't be
	 * queued, which only happens when the mod's own DrawContext mixin failed to apply.
	 */
	public static void pill(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		if (!GuiElements.submit(ctx, new PillRenderState(ctx.pose(), x, y, w, h, color))) {
			ctx.fill(x, y, x + w, y + h, color);
		}
	}

	/**
	 * Colour swatch over a checkerboard so transparency reads clearly. {@code alpha}
	 * scales the whole thing, checkerboard included, so a ghosted panel ghosts evenly.
	 */
	public static void swatch(GuiGraphicsExtractor ctx, int x, int y, int size, int color, float alpha) {
		for (int i = 0; i * 5 < size; i++) {
			for (int j = 0; j * 5 < size; j++) {
				int check = (i + j) % 2 == 0 ? EditorTheme.CHECK_A : EditorTheme.CHECK_B;
				int x2 = Math.min(x + i * 5 + 5, x + size);
				int y2 = Math.min(y + j * 5 + 5, y + size);
				ctx.fill(x + i * 5, y + j * 5, x2, y2, EditorTheme.withAlpha(check, alpha));
			}
		}
		ctx.fill(x, y, x + size, y + size, EditorTheme.withAlpha(color, alpha));
		outline(ctx, x, y, size, size, EditorTheme.withAlpha(EditorTheme.TOGGLE_OFF, alpha));
	}
}
