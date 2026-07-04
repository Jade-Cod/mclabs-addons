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

import java.util.ArrayList;
import java.util.List;

/**
 * Generic multi-row cooldown widget (sibling of LabeledTimerHudObject, which is
 * single-timer): one row per tracked cooldown across every registered
 * {@link CooldownSource} — icon, ability name, and a right-aligned countdown
 * column so ticking digits never shift the names. A finished cooldown lingers
 * briefly as "Ready!" with a gentle alpha pulse (motion only on state change),
 * then its row disappears.
 */
public class CooldownHudObject extends HudObject {
	public static final String ID = "ability_cooldowns";
	/** Echoes the HUD editor's aqua accent; overridable per-widget like all colors. */
	private static final int DEFAULT_TEXT_COLOR = 0xFF4FE3E3;
	private static final int ICON_SIZE = 16;
	private static final int ICON_GAP = 4;
	private static final int LINE_GAP = 2;
	/** Minimum gap between the name and the countdown column. */
	private static final int TIME_GAP = 8;
	/** Ready-pulse period; ~3 breaths across the linger window. */
	private static final long PULSE_PERIOD_MS = 800L;

	private static final List<CooldownSource> SOURCES = new ArrayList<>();

	private static ItemStack previewPickaxe;
	private static ItemStack previewAxe;

	/** Registers a cooldown provider; call once per feature at client init. */
	public static void addSource(CooldownSource source) {
		SOURCES.add(source);
	}

	private record Row(@Nullable ItemStack icon, String label, String time, boolean ready) {
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
		defaults.textColor = DEFAULT_TEXT_COLOR;
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

	private List<Row> rows(boolean preview) {
		long now = System.currentTimeMillis();
		List<Row> rows = new ArrayList<>();
		for (CooldownSource source : SOURCES) {
			for (CooldownEntry entry : source.entries(now)) {
				boolean ready = entry.isReady(now);
				String time = entry.active() ? "Active"
						: ready ? "Ready!"
						: (entry.approximate() ? "~" : "") + TimeFormat.hms(entry.remainingMs(now));
				rows.add(new Row(source.iconFor(entry.key()), entry.label(), time, ready));
			}
		}
		if (rows.isEmpty() && preview) {
			if (previewPickaxe == null) {
				previewPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
				previewAxe = new ItemStack(Items.DIAMOND_AXE);
			}
			rows.add(new Row(previewPickaxe, "Super Breaker", "3:12", false));
			rows.add(new Row(previewAxe, "Tree Feller", "Ready!", true));
		}
		return rows;
	}

	private static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}

	private int rowHeight() {
		return Math.max(ICON_SIZE, font().fontHeight);
	}

	private static int maxLabelWidth(List<Row> rows) {
		return rows.stream().mapToInt(row -> font().getWidth(row.label())).max().orElse(0);
	}

	private static int maxTimeWidth(List<Row> rows) {
		return rows.stream().mapToInt(row -> font().getWidth(row.time())).max().orElse(0);
	}

	@Override
	public int contentWidth(boolean preview) {
		List<Row> rows = rows(preview);
		if (rows.isEmpty()) {
			return 0;
		}
		return ICON_SIZE + ICON_GAP + maxLabelWidth(rows) + TIME_GAP + maxTimeWidth(rows);
	}

	@Override
	public int contentHeight(boolean preview) {
		int rowCount = Math.max(1, rows(preview).size());
		return rowCount * rowHeight() + (rowCount - 1) * LINE_GAP;
	}

	@Override
	protected void renderContent(DrawContext context, boolean preview) {
		List<Row> rows = rows(preview);
		TextRenderer font = font();
		int baseColor = settings().textColor | 0xFF000000;
		int width = contentWidth(preview);
		int y = 0;
		for (Row row : rows) {
			int textY = y + (rowHeight() - font.fontHeight) / 2 + 1;
			if (row.icon() != null) {
				context.drawItem(row.icon(), 0, y + (rowHeight() - ICON_SIZE) / 2);
			}
			context.drawText(font, Text.literal(row.label()),
					ICON_SIZE + ICON_GAP, textY, baseColor, true);
			int timeColor = row.ready() ? pulse(baseColor) : baseColor;
			context.drawText(font, Text.literal(row.time()),
					width - font.getWidth(row.time()), textY, timeColor, true);
			y += rowHeight() + LINE_GAP;
		}
	}

	/** The base color breathing between 0xB4 and 0xFF alpha (state-change pulse). */
	private static int pulse(int color) {
		double phase = (System.currentTimeMillis() % PULSE_PERIOD_MS) / (double) PULSE_PERIOD_MS
				* Math.PI * 2.0;
		int alpha = 0xB4 + (int) Math.round((Math.sin(phase) * 0.5 + 0.5) * (0xFF - 0xB4));
		return (alpha << 24) | (color & 0x00FFFFFF);
	}
}
