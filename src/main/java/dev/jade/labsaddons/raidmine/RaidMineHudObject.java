package dev.jade.labsaddons.raidmine;

import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.TimeFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Raid Mine widget: the double-drops countdown, and what this mining session has
 * gathered. Resource rows come from the holograms the mine spawns in place of
 * item drops — see {@link RaidMineHologramReader}.
 */
public class RaidMineHudObject extends HudObject {
	public static final String ID = "raid_mine";
	private static final int DEFAULT_TEXT_COLOR = 0xFFFFCC55;
	private static final int LINE_GAP = 2;
	/** Gap between a row's name and its total, so the columns don't touch. */
	private static final int COLUMN_GAP = 6;
	private static final String PREVIEW_TIME = "15.0";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.012f;
		defaults.y = 0.12f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		// Gated on the raid: outside it the session totals are history, and the
		// double-drops buff cannot be running anyway.
		return RaidMineScoreboard.isInRaid()
				&& (RaidMineTracker.isActive() || RaidMineSession.hasActivity());
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Component.translatable("labsaddons.hud.raid_mine.reset"),
				RaidMineSession::resetSession);
	}

	@Override
	public Component toggleGroupsLabel() {
		return RaidMineSession.knownCodes().isEmpty()
				? null
				: Component.translatable("labsaddons.hud.raid_mine.resources");
	}

	/** One toggle per resource seen this session, so the widget shows only what you care about. */
	@Override
	public List<ToggleGroup> toggleGroups() {
		List<String> codes = RaidMineSession.knownCodes();
		if (codes.isEmpty()) {
			return List.of();
		}
		List<ToggleOption> options = new ArrayList<>();
		for (String code : codes) {
			options.add(new ToggleOption(
					Component.literal(RaidMineResources.name(code)),
					() -> !RaidMineSession.isHidden(code),
					shown -> RaidMineSession.setHidden(code, !shown)));
		}
		return List.of(new ToggleGroup(
				Component.translatable("labsaddons.hud.raid_mine.resources"), options));
	}

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	private String timeText(boolean preview) {
		return RaidMineTracker.isActive()
				? TimeFormat.precise(RaidMineTracker.remainingMs())
				: PREVIEW_TIME;
	}

	/** The buff line, read as "Double Drops: 12.4" rather than an icon and a bare number. */
	private Component timerLine(boolean preview) {
		return Component.translatable("labsaddons.hud.raid_mine.double_drops_time", timeText(preview));
	}

	private boolean showsTimer(boolean preview) {
		return preview || RaidMineTracker.isActive();
	}

	private List<RaidMineSession.Row> rows(boolean preview) {
		List<RaidMineSession.Row> rows = RaidMineSession.rows().stream()
				.filter(row -> !RaidMineSession.isHidden(row.code()))
				.toList();
		if (rows.isEmpty() && preview) {
			// Editor preview so the widget can be sized and placed before a session starts.
			return List.of(new RaidMineSession.Row("ℯ", 128, 0xFFFC2E, 1536),
					new RaidMineSession.Row("𝕊", 24, 0x00B1C7, 288));
		}
		return rows;
	}

	/** Above these, a figure is compacted rather than written out in full. */
	private static final double THOUSAND = 1_000d;
	private static final double MILLION = 1_000_000d;
	private static final double BILLION = 1_000_000_000d;

	/**
	 * A figure short enough to read at a glance: "63.9k", "1.32m". The widget is
	 * looked at mid-mine, where "1,315,894.6" is more digits than anyone reads and
	 * pushes every column wider for no gain.
	 *
	 * <p>Only kicks in at a thousand. Below that the exact figure is short already,
	 * and it matters: held items multiply drops by up to 1.25, so a total can
	 * legitimately be 6.25 and rounding it away would drift as the session adds up.
	 */
	static String compact(double value) {
		double magnitude = Math.abs(value);
		if (magnitude >= BILLION) {
			return trimZeros(String.format(Locale.ROOT, "%.2f", value / BILLION)) + "b";
		}
		if (magnitude >= MILLION) {
			return trimZeros(String.format(Locale.ROOT, "%.2f", value / MILLION)) + "m";
		}
		if (magnitude >= THOUSAND) {
			return trimZeros(String.format(Locale.ROOT, "%.1f", value / THOUSAND)) + "k";
		}
		return exact(value);
	}

	/** Whole amounts plainly; otherwise up to two decimals, as multipliers produce. */
	private static String exact(double value) {
		return value == Math.rint(value)
				? String.format(Locale.ROOT, "%d", (long) value)
				: trimZeros(String.format(Locale.ROOT, "%.2f", value));
	}

	/** "64.0" -> "64", "1.30" -> "1.3"; a compacted figure shouldn't carry dead zeros. */
	private static String trimZeros(String text) {
		if (text.indexOf('.') < 0) {
			return text;
		}
		String trimmed = text.replaceAll("0+$", "");
		return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
	}

	static String amount(double value) {
		return compact(value);
	}

	/**
	 * Rates are rounded before compacting: they are extrapolated from a short
	 * sample, so decimals on them read as precision that isn't there.
	 */
	static String rate(double perHour) {
		return perHour <= 0 ? "" : compact(Math.round(perHour)) + "/h";
	}

	/**
	 * Width of the symbol column. On screen a resource is drawn as the symbol the
	 * server uses, not its name: the rows are glanced at mid-mine, and eight full
	 * names would take more width than the numbers beside them. The names live in
	 * the HUD editor's toggles instead, where there is room and time to read them.
	 */
	private int symbolWidth(List<RaidMineSession.Row> rows) {
		int width = 0;
		for (RaidMineSession.Row row : rows) {
			width = Math.max(width, font().width(row.code()));
		}
		return width;
	}

	/** Widest rendered value in the totals column. */
	private int valueWidth(List<RaidMineSession.Row> rows) {
		int width = 0;
		for (RaidMineSession.Row row : rows) {
			width = Math.max(width, font().width(amount(row.total())));
		}
		return width;
	}

	/** Widest rendered rate; 0 when no row has one yet. */
	private int rateWidth(List<RaidMineSession.Row> rows) {
		int width = 0;
		for (RaidMineSession.Row row : rows) {
			width = Math.max(width, font().width(rate(row.perHour())));
		}
		return width;
	}

	/** Combined width of the three columns: symbol, total, rate. */
	private int rowsWidth(List<RaidMineSession.Row> rows) {
		if (rows.isEmpty()) {
			return 0;
		}
		int width = symbolWidth(rows) + COLUMN_GAP + valueWidth(rows);
		int rates = rateWidth(rows);
		return rates == 0 ? width : width + COLUMN_GAP + rates;
	}

	@Override
	public int contentWidth(boolean preview) {
		int width = showsTimer(preview) ? font().width(timerLine(preview)) : 0;
		return Math.max(width, rowsWidth(rows(preview)));
	}

	@Override
	public int contentHeight(boolean preview) {
		int height = showsTimer(preview) ? font().lineHeight : 0;
		List<RaidMineSession.Row> rows = rows(preview);
		if (!rows.isEmpty()) {
			height += rows.size() * (font().lineHeight + LINE_GAP);
		}
		return height;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor context, boolean preview) {
		Font font = font();
		int color = settings().textColor | 0xFF000000;
		int y = 0;

		if (showsTimer(preview)) {
			context.text(font, timerLine(preview), 0, y, color, true);
			y += font.lineHeight;
		}

		List<RaidMineSession.Row> rows = rows(preview);
		if (rows.isEmpty()) {
			return;
		}
		// Both number columns are right-aligned so digits line up by place value
		// however wide the figures get. The widths are measured across the whole
		// column rather than per row, so the alignment holds as totals grow.
		int width = contentWidth(preview);
		int rates = rateWidth(rows);
		int valueRight = rates == 0 ? width : width - rates - COLUMN_GAP;

		for (RaidMineSession.Row row : rows) {
			y += LINE_GAP;
			// The symbol keeps the colour the server draws it in, which is what tells
			// the Flux and Essence tiers of a resource apart — they share a letter.
			context.text(font, Component.literal(row.code()),
					0, y, row.color() | 0xFF000000, true);
			String value = amount(row.total());
			context.text(font, Component.literal(value),
					valueRight - font.width(value), y, color, true);
			String perHour = rate(row.perHour());
			if (!perHour.isEmpty()) {
				context.text(font, Component.literal(perHour),
						width - font.width(perHour), y, 0xFFAAAAAA, true);
			}
			y += font.lineHeight;
		}
	}
}
