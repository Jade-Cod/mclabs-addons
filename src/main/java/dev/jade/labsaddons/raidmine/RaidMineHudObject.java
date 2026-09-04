package dev.jade.labsaddons.raidmine;

import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.TimeFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
	private static final int ICON_SIZE = 16;
	private static final int ICON_GAP = 4;
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

	/** "1,234" for whole amounts, "14.2" when the server sends a fractional one. */
	static String amount(double value) {
		return value == Math.rint(value)
				? String.format(Locale.ROOT, "%,d", (long) value)
				: String.format(Locale.ROOT, "%,.1f", value);
	}

	static String rate(double perHour) {
		return perHour <= 0 ? "" : amount(perHour) + "/h";
	}

	private int nameWidth(List<RaidMineSession.Row> rows) {
		int width = 0;
		for (RaidMineSession.Row row : rows) {
			width = Math.max(width, font().width(RaidMineResources.name(row.code())));
		}
		return width;
	}

	@Override
	public int contentWidth(boolean preview) {
		Font font = font();
		List<RaidMineSession.Row> rows = rows(preview);
		int width = showsTimer(preview) ? ICON_SIZE + ICON_GAP + font.width(timeText(preview)) : 0;
		int names = nameWidth(rows);
		for (RaidMineSession.Row row : rows) {
			int value = font.width(amount(row.total()) + "  " + rate(row.perHour()));
			width = Math.max(width, names + COLUMN_GAP + value);
		}
		return width;
	}

	@Override
	public int contentHeight(boolean preview) {
		int height = showsTimer(preview) ? Math.max(ICON_SIZE, font().lineHeight) : 0;
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
			int rowHeight = Math.max(ICON_SIZE, font.lineHeight);
			context.item(new ItemStack(Items.DIAMOND_PICKAXE), 0, y + (rowHeight - ICON_SIZE) / 2);
			context.text(font, Component.literal(timeText(preview)), ICON_SIZE + ICON_GAP,
					y + (rowHeight - font.lineHeight) / 2 + 1, color, true);
			y += rowHeight;
		}

		List<RaidMineSession.Row> rows = rows(preview);
		int names = nameWidth(rows);
		for (RaidMineSession.Row row : rows) {
			y += LINE_GAP;
			// The resource keeps the colour the server draws it in, which is what tells
			// the Flux and Essence tiers of a resource apart at a glance.
			context.text(font, Component.literal(RaidMineResources.name(row.code())),
					0, y, row.color() | 0xFF000000, true);
			String value = amount(row.total());
			String perHour = rate(row.perHour());
			context.text(font, Component.literal(value), names + COLUMN_GAP, y, color, true);
			if (!perHour.isEmpty()) {
				context.text(font, Component.literal(perHour),
						names + COLUMN_GAP + font.width(value + "  "), y, 0xFFAAAAAA, true);
			}
			y += font.lineHeight;
		}
	}
}
