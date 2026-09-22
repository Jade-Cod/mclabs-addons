package dev.jade.labsaddons.crate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crate glow used to be twelve concentric discs drawn with {@code fill()}, one rectangle
 * per scanline — 2,322 of them a frame with nine candidates up. It is now one GUI element
 * with the falloff on its vertices, which is only an improvement if it still looks the same.
 *
 * <p>So this pins the curve against the thing it replaced: the alpha twelve layers of the
 * same low opacity actually composited to, worked out here independently of the code under
 * test.
 */
class GlowCurveTest {
	/** GLOW_ALPHA (8) at full intensity, as {@link CrateChamber} hands it over. */
	private static final float FULL = 8f / 255f;

	/** What n layers of alpha {@code a} come to, painted over one another. */
	private static int composited(float a, int layers) {
		double clear = 1d;
		for (int i = 0; i < layers; i++) {
			clear *= (1d - a);
		}
		return (int) Math.round((1d - clear) * 255d);
	}

	@Test
	void theCentreIsAsBrightAsTwelveStackedDiscsWere() {
		// All twelve discs covered the centre, so it carried the full composite — about a
		// third opaque, which is the light the chamber was tuned to.
		assertEquals(composited(FULL, 12), GlowRenderState.alphaAt(FULL, 0f));
		// 81/255, a touch under a third opaque.
		assertEquals(81, GlowRenderState.alphaAt(FULL, 0f));
	}

	@Test
	void theRimIsNothingAtAll() {
		// Only the outermost disc reached the rim, and past it nothing did.
		assertEquals(0, GlowRenderState.alphaAt(FULL, 1f));
	}

	@Test
	void whereAWholeNumberOfDiscsReachedTheCurveIsExactlyWhatTheyCameTo() {
		// A point t of the way out was covered by the discs whose radius reached it, which
		// is 12(1-t) of them. Wherever that is a whole number the two must agree exactly.
		for (int discs = 0; discs <= 12; discs++) {
			float t = 1f - discs / 12f;
			assertEquals(composited(FULL, discs), GlowRenderState.alphaAt(FULL, t),
					discs + " discs reached " + t);
		}
	}

	@Test
	void theBandBoundariesSitBetweenTheDiscsTheyFallBetween() {
		// The five boundaries the fan actually emits land on fractional disc counts — t=0.2
		// is 9.6 discs deep — so the curve interpolates there rather than matching either
		// neighbour. That is the whole point of it being a curve, but it must stay penned in
		// by the discs on each side, or the light has changed shape somewhere between them.
		for (int band = 0; band <= 5; band++) {
			float t = band / 5f;
			double discs = 12 * (1d - t);
			int alpha = GlowRenderState.alphaAt(FULL, t);
			assertTrue(alpha >= composited(FULL, (int) Math.floor(discs)), "band " + band);
			assertTrue(alpha <= composited(FULL, (int) Math.ceil(discs)), "band " + band);
		}
	}

	@Test
	void theLightOnlyEverFadesOutwards() {
		int previous = 256;
		for (int step = 0; step <= 20; step++) {
			int alpha = GlowRenderState.alphaAt(FULL, step / 20f);
			assertTrue(alpha <= previous, "alpha rose at step " + step);
			previous = alpha;
		}
	}

	@Test
	void aFadingCandidateDimsTheWholeCurveWithIt() {
		// A vented candidate passes a fraction of GLOW_ALPHA, and nothing may come out
		// brighter than the same point at full strength.
		for (int step = 0; step <= 10; step++) {
			float t = step / 10f;
			assertTrue(GlowRenderState.alphaAt(FULL * 0.25f, t)
					<= GlowRenderState.alphaAt(FULL, t), "t=" + t);
		}
		assertEquals(0, GlowRenderState.alphaAt(0f, 0f));
	}
}
