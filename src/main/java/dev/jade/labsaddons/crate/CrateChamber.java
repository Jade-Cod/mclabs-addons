package dev.jade.labsaddons.crate;

import dev.jade.labsaddons.hud.HudObject;
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
	private static final int GLOW_RINGS = 6;
	private static final int GLOW_STEP = 9;
	/** How opaque the innermost glow ring gets with the field at its narrowest. */
	private static final int GLOW_ALPHA = 46;
	private static final int VIGNETTE_MAX = 150;
	private static final int VIGNETTE_BAND = 5;
	private static final int DOT = 7;

	private CrateChamber() {
	}

	/**
	 * The chamber light. Brightens and tightens as the field narrows, so the same colour
	 * says both "this is still the best thing in play" and "there is not much left".
	 *
	 * @param intensity 0 with the field wide open, 1 with one candidate left
	 */
	static void glow(DrawContext context, int cx, int cy, CrateRarity best, float intensity) {
		if (best == null) {
			return;
		}
		for (int ring = GLOW_RINGS; ring >= 1; ring--) {
			int spread = ring * GLOW_STEP;
			// Outer rings stay faint however tight it gets, or the whole panel washes out.
			int alpha = Math.round(GLOW_ALPHA * intensity / (float) ring);
			if (alpha <= 0) {
				continue;
			}
			HudObject.drawRoundedRect(context, cx - spread, cy - spread / 2,
					spread * 2, spread, tint(best.color(), alpha));
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
			int step = alpha * (VIGNETTE_BAND - band) / VIGNETTE_BAND;
			int shade = tint(0x000000, step / VIGNETTE_BAND);
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
	}

	/**
	 * Six dots, one per rarity, lit while any candidate still holds it. This is the reading
	 * the vanilla screen makes you take off pane colours by eye.
	 */
	static void ladder(DrawContext context, int x, int y, CrateSpin spin) {
		CrateRarity[] all = CrateRarity.values();
		for (int i = 0; i < all.length; i++) {
			CrateRarity rarity = all[all.length - 1 - i];
			boolean lit = spin.aliveAt(rarity);
			int left = x + i * (DOT + 3);
			context.fill(left, y, left + DOT, y + DOT,
					lit ? rarity.color() : tint(rarity.color(), 38));
		}
	}

	/** How wide the ladder is, so the caller can place what sits beside it. */
	static int ladderWidth() {
		return CrateRarity.values().length * (DOT + 3) - 3;
	}

	/**
	 * The wait for the next cull, drawn as it builds. The server's gaps lengthen by about
	 * 100ms each, so this is a real countdown rather than a spinner.
	 */
	static void tension(DrawContext context, int x, int y, int w, int h, float progress,
			int colour) {
		context.fill(x, y, x + w, y + h, 0xFF23262E);
		int filled = Math.round(w * Math.clamp(progress, 0f, 1f));
		if (filled > 0) {
			context.fill(x, y, x + filled, y + h, colour);
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
