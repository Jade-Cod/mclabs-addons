package dev.jade.labsaddons.double2;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Paints the 25-segment ring, pointer at the top.
 *
 * <p>Two things keep it smooth. First it is rasterised at <b>device</b> resolution
 * rather than GUI units: the matrix is counter-scaled and every radius multiplied up,
 * so one fill is one screen pixel instead of a GUI-scale-sized block. Drawing at GUI
 * scale and letting Minecraft magnify it was what made the edges look doubled.
 *
 * <p>Second, edges carry coverage. A pixel near the rim fades by how much of it the
 * ring covers, and a pixel near a segment boundary is mixed between the two segments,
 * so the spokes and the rim antialias themselves. That needs per-pixel work, but runs
 * of identical colour are emitted as a single fill, so the interior of a segment costs
 * one rectangle per scanline rather than one per pixel.
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
	 * @param segments      the wheel as far as it is known; a null has not been seen yet
	 * @param offset        the tweened ring position of the window's first segment
	 * @param unitsToPixels screen pixels per GUI unit (preserved for compatibility)
	 * @param hubText       lines for the middle, any of which may be null
	 */
	static void draw(DrawContext context, TextRenderer font, int cx, int cy,
			int outerR, int innerR, Lab[] segments, float offset, int accent,
			String[] hubText, int hubNoteColor, float unitsToPixels, float pointerAngle,
			Lab settledLab, int winningIndex, float settledPulse) {
		if (context instanceof DrawContextBridge bridge) {
			bridge.labsaddons$addSimpleElement(new D2WheelRenderState(
					context.getMatrices(), cx, cy, outerR, innerR, segments, offset, accent,
					settledLab, winningIndex, settledPulse, pointerAngle));
		}

		if (outerR - innerR >= GLYPH_MIN_THICKNESS) {
			glyphs(context, font, segments, cx, cy, (innerR + outerR) / 2, offset,
					settledLab, winningIndex);
		}
		hub(context, font, cx, cy, innerR, hubText, hubNoteColor);
	}

	/** Overload for compatibility. */
	static void draw(DrawContext context, TextRenderer font, int cx, int cy,
			int outerR, int innerR, Lab[] segments, float offset, int accent,
			String[] hubText, float unitsToPixels) {
		draw(context, font, cx, cy, outerR, innerR, segments, offset, accent,
				hubText, 0xFF9AA3AD, unitsToPixels, 0f, null, -1, 0f);
	}

	/** Lab glyphs, upright, in GUI units — the font is already sharp at any scale. */
	private static void glyphs(DrawContext context, TextRenderer font, Lab[] segments,
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
			context.drawText(font, lab.glyph(), gx - font.getWidth(lab.glyph()) / 2,
					gy - font.fontHeight / 2, glyphColor, false);
		}
	}

	private static void hub(DrawContext context, TextRenderer font,
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
		if (drawn == 0 || innerR < font.fontHeight) {
			return;
		}
		int lineH = font.fontHeight + 2;
		int y = cy - drawn * lineH / 2;
		for (int i = 0; i < lines.length; i++) {
			if (lines[i] == null) {
				continue;
			}
			int color = i == 0 ? 0xFFFFFFFF : i == 1 ? 0xFFEAEEF3 : noteColor;
			context.drawText(font, lines[i], cx - font.getWidth(lines[i]) / 2, y, color, false);
			y += lineH;
		}
	}
}
