package dev.jade.labsaddons.carnage;

import dev.jade.labsaddons.carnage.CarnageTracker.Daily;
import dev.jade.labsaddons.carnage.CarnageTracker.Hunt;
import dev.jade.labsaddons.carnage.CarnageTracker.Mission;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.FrameValue;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.mastery.MasteryGains;
import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.HudObjects;
import dev.jade.labsaddons.hud.TimeFormat;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

	/** Pin keys in the progress widget's own pin set, so pins follow the HUD profile the same way. */
	private static final String PIN_SCORE = "carnage:score";
	private static final String PIN_MISSIONS = "carnage:missions";
	private static final String PIN_HUNT = "carnage:hunt";
	/** Text this faint is skipped: Minecraft draws near-zero alpha as fully opaque. */
	private static final float MIN_TEXT_ALPHA = 0.05f;

	/**
	 * {@code color} 0 means the widget's own text colour. The text is resolved and measured once,
	 * as the row is built, rather than on every measure and draw of the frame.
	 */
	private record Row(String text, int width, int color, double fraction, float alpha) {
		Row(Text text, int color, double fraction, float alpha) {
			this(text.getString(), font().getWidth(text.getString()), color, fraction, alpha);
		}

		Row(Text text, int color, double fraction) {
			this(text, color, fraction, 1f);
		}

		Row faded(float to) {
			return new Row(text, width, color, fraction, to);
		}
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

	/**
	 * Like the progress widget: a notification by default, not a dashboard. A row shows while
	 * it is gaining and fades {@link MasteryGains#LIFE_MS} after its last gain. Pinning a group
	 * in the editor keeps it up for good.
	 */
	@Override
	public boolean shouldRender() {
		return !rows(false).isEmpty();
	}

	@Override
	public Text toggleGroupsLabel() {
		return Text.translatable("labsaddons.hud.progress.pinned");
	}

	@Override
	public List<ToggleGroup> toggleGroups() {
		return List.of(new ToggleGroup(Text.translatable("labsaddons.hud.carnage.name"), List.of(
				pinOption("labsaddons.hud.carnage.pin.score", PIN_SCORE),
				pinOption("labsaddons.hud.carnage.pin.missions", PIN_MISSIONS),
				pinOption("labsaddons.hud.carnage.pin.hunt", PIN_HUNT))));
	}

	private static ToggleOption pinOption(String labelKey, String pin) {
		return new ToggleOption(Text.translatable(labelKey), () -> isPinned(pin), pinned -> {
			Set<String> pins = LabsAddonsConfig.get().pinnedProgressRows;
			if (pinned) {
				pins.add(pin);
			} else {
				pins.remove(pin);
			}
			LabsAddonsConfig.get().save();
		});
	}

	private static boolean isPinned(String pin) {
		return LabsAddonsConfig.get().pinnedProgressRows.contains(pin);
	}

	/** 1 while pinned, else the gain's own fade (0 once it is gone). */
	private static float visibility(String pin, String gain) {
		return isPinned(pin) ? 1f : MasteryGains.alpha(gain);
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("labsaddons.hud.carnage.clear"), CarnageTracker::clear);
	}

	/**
	 * The rows to draw. In the editor, with nothing gaining or pinned, every row is shown so
	 * there is something to place, and a sample stands in before the first sync.
	 */
	/**
	 * Built once a frame: the HUD asks for the rows four times (shouldRender, width, height,
	 * draw), and rebuilding them each time made this the costliest widget after the cooldown
	 * rings, about 136 us and 100 KB of garbage a frame with every group pinned.
	 */
	private static final FrameValue<List<Row>> ROWS = new FrameValue<>();

	private List<Row> rows(boolean preview) {
		return ROWS.get(Util.getMeasuringTimeMs(), preview, () -> buildRows(preview));
	}

	private static List<Row> buildRows(boolean preview) {
		Daily daily = CarnageTracker.daily();
		Hunt hunt = CarnageTracker.hunt();
		long now = System.currentTimeMillis();
		if (daily != null && now >= daily.stageEndMs() + CarnageTracker.DAY_MS) {
			// The stage it last saw ended over a day ago: the event, or that stage, is over.
			daily = null;
		}
		List<Row> body = new ArrayList<>();
		if (daily != null) {
			addDaily(body, daily, now, false);
		}
		if (hunt != null) {
			float alpha = visibility(PIN_HUNT, CarnageTracker.HUNT_GAIN);
			if (alpha > 0) {
				body.add(huntRow(hunt).faded(alpha));
			}
		}
		if (body.isEmpty() && preview) {
			Daily shown = daily == null && hunt == null ? sampleDaily() : daily;
			if (shown != null) {
				addDaily(body, shown, now, true);
			}
			body.add(huntRow(hunt == null ? new Hunt(12, 60, List.of()) : hunt));
			daily = shown;
		}
		if (body.isEmpty()) {
			return List.of();
		}
		float captionAlpha = (float) body.stream().mapToDouble(Row::alpha).max().orElse(1);
		List<Row> rows = new ArrayList<>();
		rows.add(caption(daily, now).faded(captionAlpha));
		rows.addAll(body);
		return rows;
	}

	private static Row caption(Daily daily, long now) {
		return daily != null && now < daily.stageEndMs()
				? new Row(Text.translatable("labsaddons.hud.carnage.caption", daily.stage(),
						TimeFormat.hms(daily.stageEndMs() - now)), 0, NO_BAR)
				: new Row(Text.translatable("labsaddons.hud.carnage.caption_stale"), 0, NO_BAR);
	}

	private static Row huntRow(Hunt hunt) {
		return new Row(Text.translatable("labsaddons.hud.carnage.hunt", hunt.found(), hunt.total()),
				hunt.found() >= hunt.total() ? DONE_COLOR : 0, fraction(hunt.found(), hunt.total()));
	}

	/**
	 * The score group (score, raffle, day reset) shows together off a score gain. Each mission
	 * shows on its own kill, or all of them while Missions is pinned.
	 */
	private static void addDaily(List<Row> rows, Daily daily, long now, boolean showAll) {
		float scoreAlpha = showAll ? 1f : visibility(PIN_SCORE, CarnageTracker.SCORE_GAIN);
		if (scoreAlpha > 0 && daily.scoreGoal() > 0) {
			rows.add(new Row(Text.translatable("labsaddons.hud.carnage.score",
					number(daily.score()), number(daily.scoreGoal())),
					daily.score() >= daily.scoreGoal() ? DONE_COLOR : 0,
					fraction(daily.score(), daily.scoreGoal()), scoreAlpha));
		}
		if (scoreAlpha > 0 && daily.maxTickets() > 0) {
			boolean full = daily.tickets() >= daily.maxTickets() || daily.ticketCost() <= 0;
			Text raffle = full
					? Text.translatable("labsaddons.hud.carnage.raffle_full", daily.tickets(), daily.maxTickets())
					: Text.translatable("labsaddons.hud.carnage.raffle", daily.tickets(), daily.maxTickets(),
							number(daily.score() % daily.ticketCost()), number(daily.ticketCost()));
			rows.add(new Row(raffle, full ? DONE_COLOR : 0, NO_BAR, scoreAlpha));
		}
		boolean allMissions = showAll || isPinned(PIN_MISSIONS);
		if (allMissions && daily.missions().isEmpty()) {
			rows.add(new Row(Text.translatable("labsaddons.hud.carnage.no_missions"), MUTED_COLOR, NO_BAR));
		}
		for (Mission mission : daily.missions()) {
			float alpha = allMissions ? 1f : MasteryGains.alpha(CarnageTracker.missionGain(mission.name()));
			if (alpha > 0) {
				rows.add(new Row(Text.literal(mission.name() + "  " + mission.current() + "/" + mission.target()),
						mission.done() ? DONE_COLOR : 0, fraction(mission.current(), mission.target()), alpha));
			}
		}
		if (scoreAlpha > 0 && daily.dayEndMs() > now) {
			rows.add(new Row(Text.translatable("labsaddons.hud.carnage.day",
					TimeFormat.hms(daily.dayEndMs() - now), daily.goalsDone(), daily.goalsTotal()),
					MUTED_COLOR, NO_BAR, scoreAlpha));
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

	private static int faded(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Math.clamp(alpha, 0f, 1f));
		return (a << 24) | (argb & 0x00FFFFFF);
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
		int text = rows.stream().mapToInt(row -> row.width()).max().orElse(0);
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
			int color = faded(row.color() == 0 ? baseColor : row.color(), row.alpha());
			if (row.alpha() >= MIN_TEXT_ALPHA) {
				context.drawText(font, row.text(), 0, y, color, true);
			}
			if (row.fraction() >= 0) {
				int barY = y + font.fontHeight + BAR_GAP;
				EditorPainter.pill(context, 0, barY, width, BAR_H, faded(TRACK_COLOR, row.alpha()));
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
