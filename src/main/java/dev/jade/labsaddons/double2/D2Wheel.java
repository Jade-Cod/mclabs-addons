package dev.jade.labsaddons.double2;

import dev.jade.labsaddons.hud.GuiElements;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Paints the 25-segment ring, pointer at the top.
 *
 * <p>The ring itself is geometry rather than fills: {@link D2WheelRenderState} hands the GUI
 * one element holding every segment, peg, hub disc and the needle, so the whole wheel is a
 * single render-state object and the GPU does the rasterising. An earlier version drew it by
 * software-rasterising at device resolution, one fill per scanline, with per-pixel coverage at
 * the rim and the spokes; it looked right and cost hundreds of rectangles a frame.
 *
 * <p>What stays here is what wants the font: the lab glyphs around the band, and the hub. Both
 * are drawn in GUI units, because the font is already sharp at any scale.
 */
final class D2Wheel {
	static final double SEG_RAD = Math.PI * 2 / D2Ring.SIZE;
	static final int HUB_BG = 0xFF0A0C10;
	/** A segment of the wheel that has not come past the strip yet. */
	static final int UNSEEN = 0xFF2A2F3A;
	/** Below this ring thickness the glyphs are wider than the band, so they are dropped. */
	private static final int GLYPH_MIN_THICKNESS = 14;

	private D2Wheel() {
	}

	/**
	 * @param segments the wheel as far as it is known; a null has not been seen yet
	 * @param offset   the tweened ring position of the window's first segment
	 * @param hubText  lines for the middle, any of which may be null
	 */
	static void draw(GuiGraphicsExtractor context, Font font, int cx, int cy,
			int outerR, int innerR, Lab[] segments, float offset, int accent,
			String[] hubText, int hubNoteColor, float pointerAngle,
			Lab settledLab, int winningIndex, float settledPulse) {
		GuiElements.submit(context, new D2WheelRenderState(
				context.pose(), cx, cy, outerR, innerR, segments, offset, accent,
				settledLab, winningIndex, settledPulse, pointerAngle));

		if (outerR - innerR >= GLYPH_MIN_THICKNESS) {
			glyphs(context, font, segments, cx, cy, (innerR + outerR) / 2, offset,
					settledLab, winningIndex);
		}
		hub(context, font, cx, cy, innerR, hubText, hubNoteColor);
	}

	/** Lab glyphs, upright, in GUI units — the font is already sharp at any scale. */
	private static void glyphs(GuiGraphicsExtractor context, Font font, Lab[] segments,
			int cx, int cy, int radius, float offset, Lab settledLab, int winningIndex) {
		double pointerIndex = offset + D2Ring.POINTER;
		for (int i = 0; i < D2Ring.SIZE; i++) {
			double angle = (i - pointerIndex) * SEG_RAD - Math.PI / 2;
			Lab lab = segments[i];
			if (lab == null) {
				continue;
			}
			int glyphColor = lab.lift();
			if (settledLab != null && i != winningIndex) {
				glyphColor = 0x55808898;
			}
			int gx = cx + (int) Math.round(Math.cos(angle) * radius);
			int gy = cy + (int) Math.round(Math.sin(angle) * radius);
			context.text(font, lab.glyph(), gx - font.width(lab.glyph()) / 2,
					gy - font.lineHeight / 2, glyphColor, false);
		}
	}

	private static void hub(GuiGraphicsExtractor context, Font font,
			int cx, int cy, int innerR, String[] lines, int noteColor) {
		if (lines == null) {
			return;
		}
		int drawn = 0;
		for (String line : lines) {
			if (line != null) {
				drawn++;
			}
		}
		if (drawn == 0 || innerR < font.lineHeight) {
			return;
		}
		int lineH = font.lineHeight + 2;
		int y = cy - drawn * lineH / 2;
		for (int i = 0; i < lines.length; i++) {
			if (lines[i] == null) {
				continue;
			}
			int color = i == 0 ? 0xFFFFFFFF : i == 1 ? 0xFFEAEEF3 : noteColor;
			context.text(font, lines[i], cx - font.width(lines[i]) / 2, y, color, false);
			y += lineH;
		}
	}
}
