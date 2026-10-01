package dev.jade.labsaddons.carnage;

import dev.jade.labsaddons.carnage.CarnageTracker.Daily;
import dev.jade.labsaddons.carnage.CarnageTracker.Hunt;
import dev.jade.labsaddons.carnage.CarnageTracker.Mission;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.HudObjects;
import dev.jade.labsaddons.hud.TimeFormat;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Halloween Carnage at a glance: today's score toward the goal, raffle tickets, each daily
 * mission with a bar, when the day and stage end, and Halloween Hunt progress.
 *
 * <pre>
 * Carnage · Stage I · 8d 6h left
 * Daily score  67/5,000
 * ====-------------------
 * Raffle  0/10 tickets · next 67/500
 * Kill 4x Poltergeist  1/4
 * =====-----------------
 * Day resets in 1d 6h · Goals 0/16
 * Hunt  12/60
 * </pre>
 */
public class CarnageHudObject extends HudObject {
	public static final String ID = "carnage";
	private static final int DEFAULT_TEXT_COLOR = 0xFFFF8C1A;
	private static final int DONE_COLOR = 0xFF7BE06B;
	private static final int MUTED_COLOR = 0xFFAAAAAA;
	private static final int TRACK_COLOR = 0xFF3A4150;
	private static final int BAR_H = 4;
	private static final int BAR_GAP = 2;
	private static final int ROW_GAP = 3;
	private static final int MIN_BAR_W = 110;
	/** No bar under this row. */
	private static final double NO_BAR = -1;

	/** {@code color} 0 means the widget's own text colour. */
	private record Row(Text text, int color, double fraction) {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Text group() {
		return HudObjects.EVENTS;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.012f;
		defaults.y = 0.02f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	/** Gone a day after the stage it last saw ends: the event, or at least that stage, is over. */
	@Override
	public boolean shouldRender() {
		Daily daily = CarnageTracker.daily();
		if (daily != null) {
			return System.currentTimeMillis() < daily.stageEndMs() + CarnageTracker.DAY_MS;
		}
		return CarnageTracker.hunt() != null;
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("labsaddons.hud.carnage.clear"), CarnageTracker::clear);
	}

	private List<Row> rows(boolean preview) {
		Daily daily = CarnageTracker.daily();
		Hunt hunt = CarnageTracker.hunt();
		if (daily == null && hunt == null && preview) {
			daily = sampleDaily();
			hunt = new Hunt(12, 60, List.of());
		}
		long now = System.currentTimeMillis();
		List<Row> rows = new ArrayList<>();
		if (daily != null && now < daily.stageEndMs()) {
			rows.add(new Row(Text.translatable("labsaddons.hud.carnage.caption", daily.stage(),
					TimeFormat.hms(daily.stageEndMs() - now)), 0, NO_BAR));
		} else {
			rows.add(new Row(Text.translatable("labsaddons.hud.carnage.caption_stale"), 0, NO_BAR));
		}
		if (daily != null) {
			addDaily(rows, daily, now);
		}
		if (hunt != null) {
			rows.add(new Row(Text.translatable("labsaddons.hud.carnage.hunt", hunt.found(), hunt.total()),
					hunt.found() >= hunt.total() ? DONE_COLOR : 0, fraction(hunt.found(), hunt.total())));
		}
		return rows;
	}

	private static void addDaily(List<Row> rows, Daily daily, long now) {
		if (daily.scoreGoal() > 0) {
			rows.add(new Row(Text.translatable("labsaddons.hud.carnage.score",
					number(daily.score()), number(daily.scoreGoal())),
					daily.score() >= daily.scoreGoal() ? DONE_COLOR : 0, fraction(daily.score(), daily.scoreGoal())));
		}
		if (daily.maxTickets() > 0) {
			boolean full = daily.tickets() >= daily.maxTickets() || daily.ticketCost() <= 0;
			Text raffle = full
					? Text.translatable("labsaddons.hud.carnage.raffle_full", daily.tickets(), daily.maxTickets())
					: Text.translatable("labsaddons.hud.carnage.raffle", daily.tickets(), daily.maxTickets(),
							number(daily.score() % daily.ticketCost()), number(daily.ticketCost()));
			rows.add(new Row(raffle, full ? DONE_COLOR : 0, NO_BAR));
		}
		if (daily.missions().isEmpty()) {
			rows.add(new Row(Text.translatable("labsaddons.hud.carnage.no_missions"), MUTED_COLOR, NO_BAR));
		}
		for (Mission mission : daily.missions()) {
			rows.add(new Row(Text.literal(mission.name() + "  " + mission.current() + "/" + mission.target()),
					mission.done() ? DONE_COLOR : 0, fraction(mission.current(), mission.target())));
		}
		if (daily.dayEndMs() > now) {
			rows.add(new Row(Text.translatable("labsaddons.hud.carnage.day",
					TimeFormat.hms(daily.dayEndMs() - now), daily.goalsDone(), daily.goalsTotal()), MUTED_COLOR, NO_BAR));
		}
	}

	private static Daily sampleDaily() {
		long now = System.currentTimeMillis();
		return new Daily(now + 30 * 3_600_000L, "Stage I", now + 8 * CarnageTracker.DAY_MS, 1_840, 5_000,
				3, 10, 500, 2, 16, List.of(
						new Mission("Kill 4x Poltergeist", "poltergeist", 4, 4, "1x Halloween Crate Key"),
						new Mission("Kill 125x Geist", "geist", 61, 125, ""),
						new Mission("Kill 3x Scarecrow", "scarecrow", 1, 3, "")));
	}

	private static double fraction(double have, double of) {
		return of <= 0 ? 0 : Math.clamp(have / of, 0.0, 1.0);
	}

	private static String number(double value) {
		return String.format(Locale.ROOT, "%,d", (long) Math.floor(value));
	}

	// --- layout ---

	private static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}

	private static int heightOf(Row row) {
		return font().fontHeight + (row.fraction() < 0 ? 0 : BAR_GAP + BAR_H);
	}

	@Override
	public int contentWidth(boolean preview) {
		List<Row> rows = rows(preview);
		int text = rows.stream().mapToInt(row -> font().getWidth(row.text())).max().orElse(0);
		boolean hasBar = rows.stream().anyMatch(row -> row.fraction() >= 0);
		return hasBar ? Math.max(MIN_BAR_W, text) : text;
	}

	@Override
	public int contentHeight(boolean preview) {
		List<Row> rows = rows(preview);
		return rows.stream().mapToInt(CarnageHudObject::heightOf).sum() + Math.max(0, rows.size() - 1) * ROW_GAP;
	}

	@Override
	protected void renderContent(DrawContext context, boolean preview) {
		TextRenderer font = font();
		int baseColor = settings().textColor | 0xFF000000;
		int width = contentWidth(preview);
		int y = 0;
		for (Row row : rows(preview)) {
			int color = row.color() == 0 ? baseColor : row.color();
			context.drawText(font, row.text(), 0, y, color, true);
			if (row.fraction() >= 0) {
				int barY = y + font.fontHeight + BAR_GAP;
				EditorPainter.pill(context, 0, barY, width, BAR_H, TRACK_COLOR);
				int filled = (int) Math.round(width * row.fraction());
				if (filled >= BAR_H) {
					EditorPainter.pill(context, 0, barY, filled, BAR_H, color);
				} else if (filled > 0) {
					context.fill(0, barY, filled, barY + BAR_H, color);
				}
			}
			y += heightOf(row) + ROW_GAP;
		}
	}
}
