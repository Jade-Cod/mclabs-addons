package dev.jade.labsaddons.cooldown;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.TimeFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic multi-cooldown widget: one circular icon per tracked cooldown across
 * every registered {@link CooldownSource}, ringed by a recharge arc, with the
 * remaining time (M:SS) printed below. The ring starts solid orange and green
 * sweeps clockwise from the top as the cooldown recharges, like a clock hand,
 * until the whole ring is green and ready. An active ability's ring is solid
 * accent teal instead; a freshly finished one pulses solid green until it
 * drops off. The ring has a transparent center — only the ring itself is
 * drawn, so the item icon sits directly on the surrounding HUD background.
 *
 * <p>The ring is anti-aliased directly (per-pixel coverage, see
 * {@link #fillRingAA}): every pixel in the ring's bounding box gets its own
 * fractional coverage from supersampling, and its color is chosen by its
 * angle around the center. That naturally smooths both the inner and outer
 * edge in one pass, with no separate outline layer needed.
 */
public class CooldownHudObject extends HudObject {
	public static final String ID = "ability_cooldowns";

	private static final float RING_OUTER_RADIUS = 18f;
	private static final float RING_INNER_RADIUS = 15f;
	private static final float DIAMETER = RING_OUTER_RADIUS * 2f;
	private static final float GAP = 10f;
	private static final int ICON_SIZE = 16;
	private static final float TEXT_GAP = 3f;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	/** Supersample grid per pixel for {@link #fillRingAA}; 4x4 is smooth enough at this size. */
	private static final int AA_SAMPLES = 4;

	/** Green fill, sweeping clockwise as the cooldown recharges (also the ready pulse). Sampled from reference. */
	private static final int COLOR_PROGRESS = 0xFF00A500;
	/** Orange base ring, shrinking as it's consumed by the green sweep. Sampled from reference. */
	private static final int COLOR_BASE = 0xFFA24800;
	private static final int COLOR_ACTIVE = 0xFF4FE3E3;
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

	private static boolean vertical() {
		return LabsAddonsConfig.get().cooldownsStackVertical;
	}

	@Override
	public SwitchOption switchOption() {
		return new SwitchOption(
				Text.translatable("fishbite.hud.ability_cooldowns.horizontal_label"),
				Text.translatable("fishbite.hud.ability_cooldowns.vertical_label"),
				CooldownHudObject::vertical,
				isVertical -> {
					LabsAddonsConfig.get().cooldownsStackVertical = isVertical;
					LabsAddonsConfig.get().save();
				});
	}

	@Override
	public Text toggleGroupsLabel() {
		return Text.translatable("fishbite.hud.ability_cooldowns.visibility");
	}

	@Override
	public List<ToggleGroup> toggleGroups() {
		List<ToggleGroup> groups = new ArrayList<>();
		for (CooldownSource source : SOURCES) {
			String category = source.categoryLabel();
			List<CooldownSource.Toggleable> keys = source.allKeys();
			if (category == null || keys.isEmpty()) {
				continue;
			}
			List<ToggleOption> options = keys.stream()
					.map(k -> new ToggleOption(Text.literal(k.label()),
							() -> !LabsAddonsConfig.get().hiddenCooldownKeys.contains(k.key()),
							visible -> {
								var hidden = LabsAddonsConfig.get().hiddenCooldownKeys;
								if (visible) {
									hidden.remove(k.key());
								} else {
									hidden.add(k.key());
								}
								LabsAddonsConfig.get().save();
							}))
					.toList();
			groups.add(new ToggleGroup(Text.literal(category), options));
		}
		return groups;
	}

	private List<Ring> rings(boolean preview) {
		long now = System.currentTimeMillis();
		List<Ring> rings = new ArrayList<>();
		var hidden = LabsAddonsConfig.get().hiddenCooldownKeys;
		for (CooldownSource source : SOURCES) {
			for (CooldownEntry entry : source.entries(now)) {
				if (hidden.contains(entry.key())) {
					continue;
				}
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

	private static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}

	private static float itemLength() {
		return DIAMETER + TEXT_GAP + font().fontHeight;
	}

	@Override
	public int contentWidth(boolean preview) {
		int count = rings(preview).size();
		if (count == 0) {
			return 0;
		}
		return vertical() ? Math.round(DIAMETER) : Math.round(count * DIAMETER + (count - 1) * GAP);
	}

	@Override
	public int contentHeight(boolean preview) {
		int count = rings(preview).size();
		if (count == 0) {
			return 0;
		}
		return vertical()
				? Math.round(count * itemLength() + (count - 1) * GAP)
				: Math.round(itemLength());
	}

	@Override
	protected void renderContent(DrawContext context, boolean preview) {
		TextRenderer font = font();
		boolean vertical = vertical();
		float cx = RING_OUTER_RADIUS;
		float cy = RING_OUTER_RADIUS;
		for (Ring ring : rings(preview)) {
			drawRing(context, cx, cy, ring);
			if (ring.icon() != null) {
				context.drawItem(ring.icon(), Math.round(cx - ICON_SIZE / 2f), Math.round(cy - ICON_SIZE / 2f));
			}
			String text = timeText(ring);
			if (text != null) {
				int textY = Math.round(cy - RING_OUTER_RADIUS + DIAMETER + TEXT_GAP);
				context.drawCenteredTextWithShadow(font, text, Math.round(cx), textY, TEXT_COLOR);
			}
			if (vertical) {
				cy += itemLength() + GAP;
			} else {
				cx += DIAMETER + GAP;
			}
		}
	}

	/** M:SS remaining; no label while actively channeling (there's no countdown to show). */
	@Nullable
	private static String timeText(Ring ring) {
		return ring.active() ? null : TimeFormat.precise(ring.remainingMs());
	}

	private static void drawRing(DrawContext context, float cx, float cy, Ring ring) {
		if (ring.active()) {
			fillRingAA(context, cx, cy, RING_INNER_RADIUS, RING_OUTER_RADIUS, angleDeg -> COLOR_ACTIVE);
		} else if (ring.ready()) {
			int color = pulse(COLOR_PROGRESS);
			fillRingAA(context, cx, cy, RING_INNER_RADIUS, RING_OUTER_RADIUS, angleDeg -> color);
		} else {
			float progressDeg = ring.elapsedFraction() * 360f;
			fillRingAA(context, cx, cy, RING_INNER_RADIUS, RING_OUTER_RADIUS,
					angleDeg -> angleDeg < progressDeg ? COLOR_PROGRESS : COLOR_BASE);
		}
	}

	@FunctionalInterface
	private interface AngleColorFn {
		/** @param angleDeg clockwise from the top (12 o'clock), in [0, 360). */
		int colorAt(float angleDeg);
	}

	/**
	 * Fills an annulus with real anti-aliasing: every candidate pixel gets its own
	 * fractional coverage (outer-circle coverage minus inner-circle coverage, each via
	 * {@link #pixelDiscCoverage} supersampling) instead of a hard round, and its color
	 * comes from {@code colorFn} evaluated at that pixel's angle around the center. One
	 * pass smooths both the inner and outer edge with no separate outline needed, and
	 * each pixel is touched exactly once, so there's no overlap for translucency to
	 * compound into a moiré.
	 */
	private static void fillRingAA(DrawContext context, float cx, float cy, float innerR, float outerR,
			AngleColorFn colorFn) {
		int top = (int) Math.floor(cy - outerR - 1);
		int bottom = (int) Math.ceil(cy + outerR + 1);
		int left = (int) Math.floor(cx - outerR - 1);
		int right = (int) Math.ceil(cx + outerR + 1);
		for (int y = top; y < bottom; y++) {
			for (int x = left; x < right; x++) {
				float outerCoverage = pixelDiscCoverage(x, y, cx, cy, outerR);
				if (outerCoverage <= 0f) {
					continue;
				}
				float innerCoverage = innerR > 0f ? pixelDiscCoverage(x, y, cx, cy, innerR) : 0f;
				float coverage = Math.min(1f, outerCoverage - innerCoverage);
				if (coverage <= 0.02f) {
					continue;
				}
				float dx = (x + 0.5f) - cx;
				float dy = (y + 0.5f) - cy;
				float angleDeg = (float) Math.toDegrees(Math.atan2(dx, -dy));
				if (angleDeg < 0f) {
					angleDeg += 360f;
				}
				int color = colorFn.colorAt(angleDeg);
				int alpha = Math.round((color >>> 24) * coverage);
				if (alpha <= 0) {
					continue;
				}
				context.fill(x, y, x + 1, y + 1, (alpha << 24) | (color & 0x00FFFFFF));
			}
		}
	}

	/** Fraction of unit pixel cell (px, py) covered by a disc of the given radius, via an {@link #AA_SAMPLES}² grid. */
	private static float pixelDiscCoverage(int px, int py, float cx, float cy, float radius) {
		float radiusSq = radius * radius;
		int hits = 0;
		for (int sy = 0; sy < AA_SAMPLES; sy++) {
			float sampleY = (py + (sy + 0.5f) / AA_SAMPLES) - cy;
			for (int sx = 0; sx < AA_SAMPLES; sx++) {
				float sampleX = (px + (sx + 0.5f) / AA_SAMPLES) - cx;
				if (sampleX * sampleX + sampleY * sampleY <= radiusSq) {
					hits++;
				}
			}
		}
		return hits / (float) (AA_SAMPLES * AA_SAMPLES);
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
