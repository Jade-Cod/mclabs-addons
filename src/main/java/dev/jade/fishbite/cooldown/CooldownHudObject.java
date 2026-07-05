package dev.jade.fishbite.cooldown;

import dev.jade.fishbite.hud.HudObject;
import dev.jade.fishbite.hud.HudObjectSettings;
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
 * every registered {@link CooldownSource}, ringed by a two-tone recharge arc
 * (no text — the ring alone reads as a MOBA-style ability cooldown). Orange
 * is the elapsed portion, sweeping clockwise from the top; green is what's
 * left. An active ability's ring is solid accent teal; a freshly finished one
 * pulses solid green until it drops off.
 */
public class CooldownHudObject extends HudObject {
	public static final String ID = "ability_cooldowns";

	private static final float OUTER_RADIUS = 14f;
	private static final float INNER_RADIUS = 11f;
	private static final float DISC_RADIUS = 10f;
	private static final float DIAMETER = OUTER_RADIUS * 2f;
	private static final float GAP = 6f;
	private static final int ICON_SIZE = 16;

	/** Wedge granularity for the recharge ring; coarser for the backdrop disc. */
	private static final float RING_SEGMENT_DEG = 9f;
	private static final float DISC_SEGMENT_DEG = 18f;

	private static final int COLOR_ELAPSED = 0xFFFF8C1A;
	private static final int COLOR_REMAINING = 0xFF4CD137;
	private static final int COLOR_ACTIVE = 0xFF4FE3E3;
	private static final int COLOR_READY = 0xFF4CD137;
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

	private record Ring(@Nullable ItemStack icon, float elapsedFraction, boolean active, boolean ready,
			boolean approximate) {
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
				rings.add(new Ring(source.iconFor(entry.key()), entry.elapsedFraction(now),
						entry.active(), entry.isReady(now), entry.approximate()));
			}
		}
		if (rings.isEmpty() && preview) {
			if (previewPickaxe == null) {
				previewPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
				previewAxe = new ItemStack(Items.DIAMOND_AXE);
			}
			rings.add(new Ring(previewPickaxe, 0.8f, false, false, false));
			rings.add(new Ring(previewAxe, 1f, false, true, false));
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
		float cx = OUTER_RADIUS;
		float cy = OUTER_RADIUS;
		for (Ring ring : rings(preview)) {
			drawRing(context, cx, cy, ring);
			if (ring.icon() != null) {
				context.drawItem(ring.icon(), Math.round(cx - ICON_SIZE / 2f), Math.round(cy - ICON_SIZE / 2f));
			}
			cx += DIAMETER + GAP;
		}
	}

	private static void drawRing(DrawContext context, float cx, float cy, Ring ring) {
		fillArcSector(context, cx, cy, 0f, DISC_RADIUS, 0f, 360f, COLOR_DISC_BG, DISC_SEGMENT_DEG);
		if (ring.active()) {
			fillArcSector(context, cx, cy, INNER_RADIUS, OUTER_RADIUS, 0f, 360f, COLOR_ACTIVE, RING_SEGMENT_DEG);
			return;
		}
		if (ring.ready()) {
			fillArcSector(context, cx, cy, INNER_RADIUS, OUTER_RADIUS, 0f, 360f, pulse(COLOR_READY), RING_SEGMENT_DEG);
			return;
		}
		float elapsedDeg = ring.elapsedFraction() * 360f;
		boolean dim = ring.approximate();
		fillArcSector(context, cx, cy, INNER_RADIUS, OUTER_RADIUS, 0f, elapsedDeg,
				dim ? withAlpha(COLOR_ELAPSED, 0xE6) : COLOR_ELAPSED, RING_SEGMENT_DEG);
		fillArcSector(context, cx, cy, INNER_RADIUS, OUTER_RADIUS, elapsedDeg, 360f - elapsedDeg,
				dim ? withAlpha(COLOR_REMAINING, 0xE6) : COLOR_REMAINING, RING_SEGMENT_DEG);
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

	private static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	/** The base color breathing between 0xB4 and 0xFF alpha (state-change pulse). */
	private static int pulse(int color) {
		double phase = (System.currentTimeMillis() % PULSE_PERIOD_MS) / (double) PULSE_PERIOD_MS
				* Math.PI * 2.0;
		int alpha = 0xB4 + (int) Math.round((Math.sin(phase) * 0.5 + 0.5) * (0xFF - 0xB4));
		return (alpha << 24) | (color & 0x00FFFFFF);
	}
}
