package dev.jade.labsaddons.crate;

import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.editor.EditorTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/**
 * The painting half of the crate board: the chamber, the candidates hanging in it, and the
 * light that tracks the best rarity still alive.
 *
 * <p>Split out from {@link CrateSpinBoard} because that class is busy enough deciding where
 * things are; everything here just draws where it is told.
 *
 * <p>Two things are deliberate. The candidates are drawn as the server's own item icons
 * rather than as glyphs, because a player already recognises a Geo-Generator by its texture
 * and would have to learn a stand-in. And the glow is built from stacked translucent rounded
 * rectangles rather than a real radial gradient, which Minecraft's {@code DrawContext} has
 * no primitive for — six rings is plenty at this size and costs nothing.
 */
final class CrateChamber {
	/** Native item icon size. Everything here is sized off it. */
	static final int ICON = 16;
	/** The box a candidate sits in, a couple of pixels wider than its icon. */
	static final int MOTE = 22;
	/**
	 * Rings and the alpha of each, not of the whole.
	 *
	 * <p>Twelve composite to about a third opaque at the centre, the same as six at fifteen
	 * would — but at six the individual circles are visible as bands around anything as large
	 * as the winner, and at twelve they are not.
	 */
	private static final int GLOW_RINGS = 12;
	private static final int GLOW_ALPHA = 8;
	private static final int VIGNETTE_MAX = 150;
	private static final int VIGNETTE_BAND = 5;

	private CrateChamber() {
	}

	/**
	 * A soft round glow, centred on whatever it belongs to.
	 *
	 * <p>Built from concentric discs rather than rounded rectangles. The rounded-rect helper
	 * has a fixed two-pixel radius, so nesting it at any useful size drew a stack of plain
	 * rectangles with hard edges — which read as a rendering fault rather than as light, and
	 * did so most visibly at the moment the winner landed.
	 *
	 * <p>Each ring is drawn at the same low alpha and they composite, so the falloff is
	 * steepest at the centre without any one step being visible.
	 */
	static void glow(DrawContext context, float cx, float cy, float radius, int colour,
			float intensity) {
		float strength = Math.clamp(intensity, 0f, 1f);
		if (strength <= 0.01f || radius <= 1f) {
			return;
		}
		int alpha = Math.round(GLOW_ALPHA * strength);
		if (alpha <= 0) {
			return;
		}
		int shade = tint(colour, alpha);
		for (int ring = GLOW_RINGS; ring >= 1; ring--) {
			disc(context, cx, cy, radius * ring / GLOW_RINGS, shade);
		}
	}

	/** A filled circle, one fill per scanline. */
	private static void disc(DrawContext context, float cx, float cy, float radius, int colour) {
		int centreX = Math.round(cx);
		int top = Math.round(cy - radius);
		int bottom = Math.round(cy + radius);
		for (int y = top; y <= bottom; y++) {
			double dy = y + 0.5d - cy;
			double span = (double) radius * radius - dy * dy;
			if (span <= 0d) {
				continue;
			}
			int half = (int) Math.round(Math.sqrt(span));
			if (half > 0) {
				context.fill(centreX - half, y, centreX + half, y + 1, colour);
			}
		}
	}

	/**
	 * Darkens the chamber's edges as the field narrows. Drawn as four bands rather than a
	 * true vignette: at this size the difference is invisible and the cost is four fills.
	 */
	static void vignette(DrawContext context, int x, int y, int w, int h, float intensity) {
		int alpha = Math.round(VIGNETTE_MAX * intensity);
		if (alpha <= 0) {
			return;
		}
		for (int band = 0; band < VIGNETTE_BAND; band++) {
			int shade = tint(0x000000, alpha * (VIGNETTE_BAND - band) / VIGNETTE_BAND);
			context.fill(x, y + band, x + w, y + band + 1, shade);
			context.fill(x, y + h - band - 1, x + w, y + h - band, shade);
			context.fill(x + band, y, x + band + 1, y + h, shade);
			context.fill(x + w - band - 1, y, x + w - band, y + h, shade);
		}
	}

	/** What a candidate with no rarity to its name is framed in — a voter crate has none. */
	static final int NEUTRAL = 0xFF9AA3AD;

	/** A rarity's colour, or the neutral frame when there is no rarity to show. */
	static int colourOf(CrateRarity rarity) {
		return rarity == null ? NEUTRAL : rarity.color();
	}

	/**
	 * One candidate: the server's own item icon, framed in a colour. {@code alpha} fades a
	 * vented one out and {@code scale} grows the winner.
	 *
	 * <p>Takes a colour rather than a rarity because the voter crate has no rarities at all and
	 * still wants to mark one of its three draws.
	 *
	 * @param stack the server's own item, drawn as-is
	 */
	static void mote(DrawContext context, ItemStack stack, int colour, float cx,
			float cy, float scale, float alpha) {
		if (alpha <= 0.01f) {
			return;
		}
		int box = Math.round(MOTE * scale);
		int left = Math.round(cx - box / 2f);
		int top = Math.round(cy - box / 2f);
		int weight = Math.round(alpha * 255);

		HudObject.drawRoundedRect(context, left, top, box, box,
				tint(colour, Math.round(alpha * 40)));
		outline(context, left, top, box, box, tint(colour, Math.min(255, weight)));

		if (stack == null || stack.isEmpty()) {
			return;
		}
		// drawItem has no scale of its own, so the icon rides a matrix. Translate to where the
		// icon's top-left should land *after* scaling, or it drifts as the winner grows.
		float iconScale = scale;
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(cx - ICON * iconScale / 2f, cy - ICON * iconScale / 2f);
		context.getMatrices().scale(iconScale, iconScale);
		context.drawItem(stack, 0, 0);
		context.getMatrices().popMatrix();

		// drawItem has no alpha either — it goes through the item renderer, which ignores
		// anything on the matrix. So a fading candidate's frame faded and its icon did not,
		// then popped out whole. Veiling it in the panel's own background is the same result
		// from the front, and is the only way to dim an item icon at all.
		if (alpha < 1f) {
			HudObject.drawRoundedRect(context, left, top, box, box,
					tint(EditorTheme.PANEL_BG, Math.round((1f - alpha) * 255)));
		}
	}

	/** A label centred on {@code cx}, which is how everything in the chamber is placed. */
	static void centred(DrawContext context, TextRenderer font, String text, int cx, int y,
			int colour) {
		if (text == null || text.isEmpty()) {
			return;
		}
		context.drawText(font, text, cx - font.getWidth(text) / 2, y, colour, false);
	}

	private static void outline(DrawContext context, int x, int y, int w, int h, int colour) {
		context.fill(x, y, x + w, y + 1, colour);
		context.fill(x, y + h - 1, x + w, y + h, colour);
		context.fill(x, y + 1, x + 1, y + h - 1, colour);
		context.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
	}

	/** An ARGB colour at a given alpha, keeping its rgb. */
	static int tint(int argb, int alpha) {
		return (Math.clamp(alpha, 0, 255) << 24) | (argb & 0x00FFFFFF);
	}
}
