package dev.jade.fishbite.hud.editor;

import dev.jade.fishbite.hud.HudObject;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Stateless drawing primitives for the HUD Studio editor. The screen owns layout
 * and state; this class only paints pixels (using {@code fill} since 1.21.11's
 * {@code DrawContext} has no {@code drawBorder}). Reuses
 * {@link HudObject#drawRoundedRect} for soft corners. Nothing here allocates
 * long-lived state, and none of it runs outside the open editor screen.
 */
public final class EditorPainter {
	private EditorPainter() {
	}

	/** Faint alignment grid across the whole screen. */
	public static void gridOverlay(DrawContext ctx, int width, int height, int step, int color) {
		for (int x = step; x < width; x += step) {
			ctx.fill(x, 0, x + 1, height, color);
		}
		for (int y = step; y < height; y += step) {
			ctx.fill(0, y, width, y + 1, color);
		}
	}

	/** 1px rectangular frame. */
	public static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
		ctx.fill(x, y, x + w, y + 1, color);
		ctx.fill(x, y + h - 1, x + w, y + h, color);
		ctx.fill(x, y, x + 1, y + h, color);
		ctx.fill(x + w - 1, y, x + w, y + h, color);
	}

	/** A small square resize grip: dark border behind an accent core, drawn around (cx,cy). */
	public static void resizeHandle(DrawContext ctx, int cx, int cy, int size, int fill, int border) {
		int half = size / 2;
		ctx.fill(cx - half - 1, cy - half - 1, cx + half + 1, cy + half + 1, border);
		ctx.fill(cx - half, cy - half, cx + half, cy + half, fill);
	}

	/** A readable name pill: rounded translucent background + shadowed text. */
	public static void nameChip(DrawContext ctx, TextRenderer tr, Text label, int x, int y, int textColor) {
		int w = tr.getWidth(label);
		HudObject.drawRoundedRect(ctx, x - 3, y - 2, w + 6, tr.fontHeight + 3, EditorTheme.CHIP_BG);
		ctx.drawText(tr, label, x, y, textColor, true);
	}

	/** Rounded panel fill + a crisp 1px border. */
	public static void panel(DrawContext ctx, int[] rect, int bg, int border) {
		HudObject.drawRoundedRect(ctx, rect[0], rect[1], rect[2], rect[3], bg);
		outline(ctx, rect[0], rect[1], rect[2], rect[3], border);
	}

	/** Colour swatch over a checkerboard so transparency reads clearly. */
	public static void swatch(DrawContext ctx, int x, int y, int size, int color) {
		for (int i = 0; i * 5 < size; i++) {
			for (int j = 0; j * 5 < size; j++) {
				int check = (i + j) % 2 == 0 ? EditorTheme.CHECK_A : EditorTheme.CHECK_B;
				int x2 = Math.min(x + i * 5 + 5, x + size);
				int y2 = Math.min(y + j * 5 + 5, y + size);
				ctx.fill(x + i * 5, y + j * 5, x2, y2, check);
			}
		}
		ctx.fill(x, y, x + size, y + size, color);
		outline(ctx, x, y, size, size, EditorTheme.TOGGLE_OFF);
	}
}
