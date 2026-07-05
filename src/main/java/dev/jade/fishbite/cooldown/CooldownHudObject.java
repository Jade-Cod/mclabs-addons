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
 * labelled with the remaining time (M:SS). The ring starts solid red and
 * green sweeps clockwise from the top as the cooldown recharges, like a clock
 * hand, until the whole ring is green and ready. An active ability's ring is
 * solid accent teal instead; a freshly finished one pulses solid green until
 * it drops off.
 */
public class CooldownHudObject extends HudObject {
	public static final String ID = "ability_cooldowns";

	private static final float OUTER_RADIUS = 18f;
	private static final float INNER_RADIUS = 15f;
	private static final float DISC_RADIUS = 14.5f;
	private static final float DIAMETER = OUTER_RADIUS * 2f;
	private static final float GAP = 8f;
	private static final int ICON_SIZE = 16;
	/** Icon top-left and text top, both relative to the ring's vertical center. */
	private static final float ICON_Y_OFFSET = -11f;
	private static final float TEXT_Y_OFFSET = 6f;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	/** Wedge granularity for the recharge ring; coarser for the backdrop disc. */
	private static final float RING_SEGMENT_DEG = 9f;
	private static final float DISC_SEGMENT_DEG = 18f;

	/** Green fill, sweeping clockwise as the cooldown recharges (also the ready pulse). */
	private static final int COLOR_PROGRESS = 0xFF4CD137;
	/** Red base ring, shrinking as it's consumed by the green sweep. */
	private static final int COLOR_BASE = 0xFFE74C3D;
	private static final int COLOR_ACTIVE = 0xFF4FE3E3;
	/**
	 * Backdrop disc behind the icon. Always fully opaque: the disc/ring are drawn as
	 * many overlapping rotated rectangles (see {@link #fillArcSector}), and any
	 * translucency there compounds unevenly where wedges cross — visible as a
	 * starburst moiré radiating from the center. Opaque paint is overlap-proof:
	 * painting the same solid color twice looks identical to painting it once.
	 */
	private static final int COLOR_DISC_BG = 0xFF202020;
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
		float cx = OUTER_RADIUS;
		float cy = OUTER_RADIUS;
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
		fillArcSector(context, cx, cy, 0f, DISC_RADIUS, 0f, 360f, COLOR_DISC_BG, DISC_SEGMENT_DEG);
		if (ring.active()) {
			fillArcSector(context, cx, cy, INNER_RADIUS, OUTER_RADIUS, 0f, 360f, COLOR_ACTIVE, RING_SEGMENT_DEG);
			return;
		}
		if (ring.ready()) {
			fillArcSector(context, cx, cy, INNER_RADIUS, OUTER_RADIUS, 0f, 360f, pulse(COLOR_PROGRESS), RING_SEGMENT_DEG);
			return;
		}
		float progressDeg = ring.elapsedFraction() * 360f;
		fillArcSector(context, cx, cy, INNER_RADIUS, OUTER_RADIUS, 0f, progressDeg, COLOR_PROGRESS, RING_SEGMENT_DEG);
		fillArcSector(context, cx, cy, INNER_RADIUS, OUTER_RADIUS, progressDeg, 360f - progressDeg, COLOR_BASE,
				RING_SEGMENT_DEG);
	}

	/**
	 * Fills an annulus sector (or, with {@code innerR = 0}, a disc) by approximating
	 * it as a fan of thin rotated rectangles — {@link DrawContext} has no native arc
	 * primitive, but {@code fill()} snapshots the current matrix per-quad, so a small
	 * rotation between calls produces a rotated quad. {@code startDeg}/{@code sweepDeg}
	 * are measured clockwise from the top (12 o'clock).
	 */
	private static void fillArcSector(DrawContext context, float cx, float cy, float innerR, float outerR,
			float startDeg, float sweepDeg, int color, float segmentDeg) {
		if (sweepDeg <= 0f) {
			return;
		}
		sweepDeg = Math.min(360f, sweepDeg);
		int segments = Math.max(1, (int) Math.ceil(sweepDeg / segmentDeg));
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
	 * The base color breathing in brightness (never alpha) between 70% and 100%.
	 * Every wedge {@link #fillArcSector} paints must stay fully opaque: the ring/disc
	 * are approximated as many overlapping rotated rectangles, and any translucency
	 * there compounds unevenly where wedges cross, visible as a starburst moiré. An
	 * opaque color painted twice looks identical to painted once, so pulsing via RGB
	 * intensity gets the "breathing" effect with no overlap risk.
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
