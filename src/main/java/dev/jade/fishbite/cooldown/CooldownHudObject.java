package dev.jade.fishbite.cooldown;

import dev.jade.fishbite.hud.HudObject;
import dev.jade.fishbite.hud.HudObjectSettings;
import dev.jade.fishbite.hud.TimeFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic multi-cooldown widget: one circular icon per tracked cooldown across
 * every registered {@link CooldownSource}, ringed by a recharge arc and
 * labelled with the remaining time (M:SS). The ring starts solid orange and
 * green sweeps clockwise from the top as the cooldown recharges, like a clock
 * hand, until the whole ring is green and ready. An active ability's ring is
 * solid accent teal instead; a freshly finished one pulses solid green until
 * it drops off. A thin gray rim frames both edges of the ring.
 */
public class CooldownHudObject extends HudObject {
	public static final String ID = "ability_cooldowns";

	private static final float RING_OUTER_RADIUS = 18f;
	private static final float RING_INNER_RADIUS = 15f;
	private static final float BORDER_THICKNESS = 1.5f;
	private static final float BORDER_OUTER_RADIUS = RING_OUTER_RADIUS + BORDER_THICKNESS;
	private static final float BORDER_INNER_RADIUS = RING_INNER_RADIUS - BORDER_THICKNESS;
	private static final float DIAMETER = BORDER_OUTER_RADIUS * 2f;
	private static final float GAP = 8f;
	private static final int ICON_SIZE = 16;
	/** Icon top-left and text top, both relative to the ring's vertical center. */
	private static final float ICON_Y_OFFSET = -11f;
	private static final float TEXT_Y_OFFSET = 6f;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	/** Wedge granularity for the progress split — the one shape that genuinely needs an angular cut. */
	private static final float RING_SEGMENT_DEG = 2.5f;

	/** Green fill, sweeping clockwise as the cooldown recharges (also the ready pulse). Sampled from reference. */
	private static final int COLOR_PROGRESS = 0xFF00A500;
	/** Orange base ring, shrinking as it's consumed by the green sweep. Sampled from reference. */
	private static final int COLOR_BASE = 0xFFA24800;
	private static final int COLOR_ACTIVE = 0xFF4FE3E3;
	/** Thin rim framing both edges of the ring. Sampled from reference. */
	private static final int COLOR_BORDER = 0xFF4C4C4C;
	private static final int COLOR_DISC_BG = 0x99202020;
	/** Ready-pulse period; ~3 breaths across the linger window. */
	private static final long PULSE_PERIOD_MS = 800L;

	private static final List<CooldownSource> SOURCES = new ArrayList<>();

	private static ItemStack previewPickaxe;
	private static ItemStack previewAxe;

	/** Registers a cooldown provider; call once per feature at client init. */
	public static void addSource(CooldownSource source) {
		SOURCES.add(source);
	}

	private record Ring(@Nullable ItemStack icon, long remainingMs, float elapsedFraction, boolean active,
			boolean ready) {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.985f;
		defaults.y = 0.30f;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		long now = System.currentTimeMillis();
		return SOURCES.stream().anyMatch(source -> !source.entries(now).isEmpty());
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("fishbite.hud.ability_cooldowns.clear"),
				() -> SOURCES.forEach(CooldownSource::clear));
	}

	private List<Ring> rings(boolean preview) {
		long now = System.currentTimeMillis();
		List<Ring> rings = new ArrayList<>();
		for (CooldownSource source : SOURCES) {
			for (CooldownEntry entry : source.entries(now)) {
				rings.add(new Ring(source.iconFor(entry.key()), entry.remainingMs(now), entry.elapsedFraction(now),
						entry.active(), entry.isReady(now)));
			}
		}
		if (rings.isEmpty() && preview) {
			if (previewPickaxe == null) {
				previewPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
				previewAxe = new ItemStack(Items.DIAMOND_AXE);
			}
			rings.add(new Ring(previewPickaxe, 48_000L, 0.8f, false, false));
			rings.add(new Ring(previewAxe, 0L, 1f, false, true));
		}
		return rings;
	}

	@Override
	public int contentWidth(boolean preview) {
		int count = rings(preview).size();
		if (count == 0) {
			return 0;
		}
		return Math.round(count * DIAMETER + (count - 1) * GAP);
	}

	@Override
	public int contentHeight(boolean preview) {
		return rings(preview).isEmpty() ? 0 : Math.round(DIAMETER);
	}

	@Override
	protected void renderContent(DrawContext context, boolean preview) {
		TextRenderer font = font();
		float cx = BORDER_OUTER_RADIUS;
		float cy = BORDER_OUTER_RADIUS;
		for (Ring ring : rings(preview)) {
			drawRing(context, cx, cy, ring);
			if (ring.icon() != null) {
				context.drawItem(ring.icon(), Math.round(cx - ICON_SIZE / 2f), Math.round(cy + ICON_Y_OFFSET));
			}
			String text = timeText(ring);
			if (text != null) {
				drawCenteredText(context, font, text, cx, cy + TEXT_Y_OFFSET, TEXT_COLOR);
			}
			cx += DIAMETER + GAP;
		}
	}

	private static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}

	/** M:SS remaining; no label while actively channeling (there's no countdown to show). */
	@Nullable
	private static String timeText(Ring ring) {
		return ring.active() ? null : TimeFormat.hms(ring.remainingMs());
	}

	private static void drawRing(DrawContext context, float cx, float cy, Ring ring) {
		fillDisc(context, cx, cy, BORDER_INNER_RADIUS, COLOR_DISC_BG);
		fillAnnulus(context, cx, cy, BORDER_INNER_RADIUS, RING_INNER_RADIUS, COLOR_BORDER);
		if (ring.active()) {
			fillAnnulus(context, cx, cy, RING_INNER_RADIUS, RING_OUTER_RADIUS, COLOR_ACTIVE);
		} else if (ring.ready()) {
			fillAnnulus(context, cx, cy, RING_INNER_RADIUS, RING_OUTER_RADIUS, pulse(COLOR_PROGRESS));
		} else {
			float progressDeg = ring.elapsedFraction() * 360f;
			fillArcSector(context, cx, cy, RING_INNER_RADIUS, RING_OUTER_RADIUS, 0f, progressDeg, COLOR_PROGRESS);
			fillArcSector(context, cx, cy, RING_INNER_RADIUS, RING_OUTER_RADIUS, progressDeg, 360f - progressDeg,
					COLOR_BASE);
		}
		fillAnnulus(context, cx, cy, RING_OUTER_RADIUS, BORDER_OUTER_RADIUS, COLOR_BORDER);
	}

	/**
	 * Fills a disc per horizontal scanline (one non-overlapping row per pixel, its
	 * half-width from the circle equation) rather than approximating it from rotated
	 * quads. This is pixel-accurate — no polygon faceting — and safe for translucent
	 * colors: unlike overlapping rotated wedges, adjacent rows never double-paint the
	 * same pixel, so alpha can't compound into visible seams.
	 */
	private static void fillDisc(DrawContext context, float cx, float cy, float radius, int color) {
		int top = (int) Math.floor(cy - radius);
		int bottom = (int) Math.ceil(cy + radius);
		for (int y = top; y < bottom; y++) {
			float dy = (y + 0.5f) - cy;
			float discriminant = radius * radius - dy * dy;
			if (discriminant <= 0f) {
				continue;
			}
			float halfWidth = (float) Math.sqrt(discriminant);
			context.fill(Math.round(cx - halfWidth), y, Math.round(cx + halfWidth), y + 1, color);
		}
	}

	/** Same scanline technique as {@link #fillDisc}, hollowed out below {@code innerR}. */
	private static void fillAnnulus(DrawContext context, float cx, float cy, float innerR, float outerR, int color) {
		int top = (int) Math.floor(cy - outerR);
		int bottom = (int) Math.ceil(cy + outerR);
		for (int y = top; y < bottom; y++) {
			float dy = (y + 0.5f) - cy;
			float outerDisc = outerR * outerR - dy * dy;
			if (outerDisc <= 0f) {
				continue;
			}
			float outerHalf = (float) Math.sqrt(outerDisc);
			float innerDisc = innerR * innerR - dy * dy;
			if (innerDisc <= 0f) {
				// This row is entirely above/below the inner circle — one continuous span.
				context.fill(Math.round(cx - outerHalf), y, Math.round(cx + outerHalf), y + 1, color);
			} else {
				float innerHalf = (float) Math.sqrt(innerDisc);
				context.fill(Math.round(cx - outerHalf), y, Math.round(cx - innerHalf), y + 1, color);
				context.fill(Math.round(cx + innerHalf), y, Math.round(cx + outerHalf), y + 1, color);
			}
		}
	}

	/**
	 * Fills an annulus sector by approximating it as a fan of thin rotated
	 * rectangles — {@link DrawContext} has no native arc primitive, but {@code fill()}
	 * snapshots the current matrix per-quad, so a small rotation between calls
	 * produces a rotated quad. Only the progress/base split needs this: it's the one
	 * ring shape whose color boundary sits at an arbitrary angle rather than following
	 * a full circle, so {@link #fillAnnulus}'s per-row math doesn't apply. Every wedge
	 * must stay fully opaque — translucency here compounds unevenly where wedges
	 * overlap, visible as a starburst moiré. {@code startDeg}/{@code sweepDeg} are
	 * measured clockwise from the top (12 o'clock).
	 */
	private static void fillArcSector(DrawContext context, float cx, float cy, float innerR, float outerR,
			float startDeg, float sweepDeg, int color) {
		if (sweepDeg <= 0f) {
			return;
		}
		sweepDeg = Math.min(360f, sweepDeg);
		int segments = Math.max(1, (int) Math.ceil(sweepDeg / RING_SEGMENT_DEG));
		float segDeg = sweepDeg / segments;
		// Outer-edge arc length for one wedge, so neighboring wedges tile without gaps.
		float halfThickness = (outerR * (float) Math.toRadians(segDeg)) / 2f + 0.5f;
		int x1 = Math.round(cx + innerR);
		int x2 = Math.round(cx + outerR);
		int y1 = Math.round(cy - halfThickness);
		int y2 = Math.round(cy + halfThickness);
		Matrix3x2fStack matrices = context.getMatrices();
		for (int i = 0; i < segments; i++) {
			float mid = startDeg + segDeg * (i + 0.5f);
			matrices.pushMatrix();
			// The un-rotated wedge points due east of center (logical angle 90 from top).
			matrices.rotateAbout((float) Math.toRadians(mid - 90f), cx, cy);
			context.fill(x1, y1, x2, y2, color);
			matrices.popMatrix();
		}
	}

	/**
	 * Draws text centered at ({@code cx}, {@code topY}) at native scale. Minecraft's
	 * font is a fixed-resolution bitmap font; scaling it by a fractional matrix factor
	 * (as an earlier version of this widget did) forces sub-pixel interpolation and
	 * reads as blurry, uneven strokes. Sizing the ring to fit the text instead keeps
	 * every glyph on the pixel grid.
	 */
	private static void drawCenteredText(DrawContext context, TextRenderer font, String text, float cx, float topY,
			int color) {
		context.drawCenteredTextWithShadow(font, text, Math.round(cx), Math.round(topY), color);
	}

	/**
	 * The base color breathing in brightness (never alpha) between 70% and 100%, so
	 * the always-opaque-paint rule above still holds during the pulse.
	 */
	private static int pulse(int color) {
		double phase = (System.currentTimeMillis() % PULSE_PERIOD_MS) / (double) PULSE_PERIOD_MS
				* Math.PI * 2.0;
		double brightness = 0.7 + (Math.sin(phase) * 0.5 + 0.5) * 0.3;
		int r = clampChannel(((color >> 16) & 0xFF) * brightness);
		int g = clampChannel(((color >> 8) & 0xFF) * brightness);
		int b = clampChannel((color & 0xFF) * brightness);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static int clampChannel(double value) {
		return Math.max(0, Math.min(255, (int) Math.round(value)));
	}
}
