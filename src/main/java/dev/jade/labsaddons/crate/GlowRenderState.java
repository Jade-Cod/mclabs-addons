package dev.jade.labsaddons.crate;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * The soft round light behind a crate candidate, as one GUI element.
 *
 * <p>It used to be twelve concentric discs drawn with {@code fill()}, one rectangle per
 * scanline of each — which is where the cost was. A glow of the size the chamber uses came
 * to 258 fills, and nine candidates to <b>2,322 per frame</b>, each one its own render-state
 * object. Here the same light is an annular fan: the falloff rides on the vertex colours, so
 * the GPU interpolates what the old version paid for a rectangle at a time.
 *
 * <p>{@link #alphaAt} reproduces the old curve rather than inventing a nicer one, so the
 * chamber looks the way it did. Twelve layers of the same low alpha composite to
 * {@code 1-(1-a)^12} at the centre and fall to nothing at the rim; sampling that exact
 * expression at each band boundary keeps the shape, and five bands is enough because the
 * curve is gentle and the GPU smooths between them.
 */
final class GlowRenderState implements SimpleGuiElementRenderState {
	/** Radial steps. The curve is gentle, so the GPU's own blend covers the gaps. */
	private static final int BANDS = 5;
	/**
	 * Wedges around the circle. At the radius the chamber draws, twenty puts about three
	 * pixels of arc between vertices, which is under what the old scanlines quantised to.
	 */
	private static final int SLICES = 20;
	/** Layers the old version composited. Kept so {@link #alphaAt} matches it. */
	private static final int LAYERS = 12;

	private final Matrix3x2fc pose;
	private final float cx;
	private final float cy;
	private final float radius;
	private final int rgb;
	/** One layer's alpha as a fraction, which is what the old version drew twelve of. */
	private final float layerAlpha;
	private final ScreenRect bounds;

	GlowRenderState(Matrix3x2fc pose, float cx, float cy, float radius, int colour,
			float layerAlpha) {
		this.pose = new Matrix3x2f(pose);
		this.cx = cx;
		this.cy = cy;
		this.radius = radius;
		this.rgb = colour & 0x00FFFFFF;
		this.layerAlpha = layerAlpha;

		int minX = (int) Math.floor(cx - radius) - 1;
		int minY = (int) Math.floor(cy - radius) - 1;
		int size = (int) Math.ceil(radius * 2) + 3;
		this.bounds = new ScreenRect(minX, minY, size, size).transformEachVertex(this.pose);
	}

	/**
	 * The old composite at a fraction {@code t} of the way out, {@code 1-(1-a)^(12(1-t))}.
	 * Full weight at the centre, nothing at the rim.
	 *
	 * <p>Static and package-private so the curve can be checked without a GUI; see
	 * {@code GlowCurveTest}, which is the only thing standing between this and the light
	 * quietly changing shape.
	 */
	static int alphaAt(float layerAlpha, float t) {
		double covered = LAYERS * (1d - t);
		double alpha = 1d - Math.pow(1d - layerAlpha, covered);
		return Math.clamp((int) Math.round(alpha * 255d), 0, 255);
	}

	@Override
	public void setupVertices(VertexConsumer consumer) {
		double step = Math.PI * 2d / SLICES;
		for (int band = 0; band < BANDS; band++) {
			float inner = radius * band / BANDS;
			float outer = radius * (band + 1) / BANDS;
			int innerColour = rgb | (alphaAt(layerAlpha, (float) band / BANDS) << 24);
			int outerColour = rgb | (alphaAt(layerAlpha, (float) (band + 1) / BANDS) << 24);
			for (int slice = 0; slice < SLICES; slice++) {
				double a0 = slice * step;
				double a1 = (slice + 1) * step;
				float cos0 = (float) Math.cos(a0);
				float sin0 = (float) Math.sin(a0);
				float cos1 = (float) Math.cos(a1);
				float sin1 = (float) Math.sin(a1);

				// Outer, inner, inner, outer — the winding the wheel's segments use.
				consumer.vertex(pose, cx + outer * cos0, cy + outer * sin0).color(outerColour);
				consumer.vertex(pose, cx + inner * cos0, cy + inner * sin0).color(innerColour);
				consumer.vertex(pose, cx + inner * cos1, cy + inner * sin1).color(innerColour);
				consumer.vertex(pose, cx + outer * cos1, cy + outer * sin1).color(outerColour);
			}
		}
	}

	@Override
	public RenderPipeline pipeline() {
		return RenderPipelines.GUI;
	}

	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.empty();
	}

	@Override
	public ScreenRect scissorArea() {
		return null;
	}

	@Override
	public ScreenRect bounds() {
		return this.bounds;
	}
}
