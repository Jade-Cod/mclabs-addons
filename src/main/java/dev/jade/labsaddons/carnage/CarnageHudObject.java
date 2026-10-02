package dev.jade.labsaddons.carnage;

import dev.jade.labsaddons.carnage.CarnageTracker.Daily;
import dev.jade.labsaddons.carnage.CarnageTracker.Mission;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.FrameValue;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.HudObjects;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.mastery.MasteryGains;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Halloween Carnage while you grind, laid out like the Mastery and prestige progress widget:
 * an icon, a name, the figures, what you just gained, and one bar.
 *
 * <pre>
 * [skull] Daily score  920/5,000  18%  +4.3
 *         ###|#---|----|----|----|----|----
 * [sword] Poltergeist  1/4  25%  +1
 *         #######----------------------
 * </pre>
 *
 * <p>Two questions only: am I close to today's goal, and is the mission I'm on moving. The
 * score bar is notched at each raffle ticket, since tickets are earned from the same score. A
 * mission row comes up when you kill its mob. Rows show while they gain and fade like the
 * progress widget's; the editor can pin either kind to keep it up. Everything else (stage
 * and day timers, the goal streak, the hunt) is in the {@code /carnage} screen.
 */
public class CarnageHudObject extends HudObject {
	public static final String ID = "carnage";
	private static final int DEFAULT_TEXT_COLOR = 0xFFFF8C1A;
	private static final int GAIN_COLOR = 0xFF7BE06B;
	private static final int TRACK_COLOR = 0xFF3A4150;
	private static final int NOTCH_COLOR = 0xFF1A1D24;
	private static final int ICON_SIZE = 16;
	private static final int GAP = 4;
	private static final int BAR_GAP = 2;
	private static final int ROW_GAP = 5;
	private static final int BAR_H = 6;
	/** Icons cannot be drawn translucent, so hide them once a fading row is mostly gone. */
	private static final float ICON_FADE_CUTOFF = 0.45f;
	/** Component this faint is skipped: Minecraft draws near-zero alpha as fully opaque. */
	private static final float MIN_TEXT_ALPHA = 0.05f;

	/** Pin keys in the progress widget's own pin set, so pins follow the HUD profile the same way. */
	private static final String PIN_SCORE = "carnage:score";
	private static final String PIN_MISSIONS = "carnage:missions";
	private static final Pattern KILL_PREFIX = Pattern.compile("^Kill\\s+[\\d,]+x\\s+", Pattern.CASE_INSENSITIVE);

	/**
	 * Made on first draw, not at class load: the widget is registered while the game is still
	 * starting, and 26.3 refuses to build an item stack before its components are bound.
	 */
	private static ItemStack scoreIcon;
	private static ItemStack missionIcon;

	private static ItemStack scoreIcon() {
		if (scoreIcon == null) {
			scoreIcon = new ItemStack(Items.SKULL_POTTERY_SHERD);
		}
		return scoreIcon;
	}

	private static ItemStack missionIcon() {
		if (missionIcon == null) {
			missionIcon = new ItemStack(Items.IRON_SWORD);
		}
		return missionIcon;
	}

	/**
	 * One row, its text resolved and measured as it is built. {@code notches} are where on the
	 * bar the raffle tickets fall, as fractions of it; empty for none.
	 */
	private record Row(ItemStack icon, String label, String gain, int width, double fraction, double[] notches,
			float alpha) {
	}

	/**
	 * Built once a frame: the HUD asks for the rows four times (shouldRender, width, height,
	 * draw), and rebuilding them each time is what made the old version the costliest widget.
	 */
	private static final FrameValue<List<Row>> ROWS = new FrameValue<>();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component group() {
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

	@Override
	public boolean shouldRender() {
		return !rows(false).isEmpty();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Component.translatable("labsaddons.hud.carnage.clear"), CarnageTracker::clear);
	}

	@Override
	public Component toggleGroupsLabel() {
		return Component.translatable("labsaddons.hud.progress.pinned");
	}

	@Override
	public List<ToggleGroup> toggleGroups() {
		return List.of(new ToggleGroup(Component.translatable("labsaddons.hud.carnage.name"), List.of(
				pinOption("labsaddons.hud.carnage.pin.score", PIN_SCORE),
				pinOption("labsaddons.hud.carnage.pin.missions", PIN_MISSIONS))));
	}

	private static ToggleOption pinOption(String labelKey, String pin) {
		return new ToggleOption(Component.translatable(labelKey), () -> isPinned(pin), pinned -> {
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

	// --- rows ---

	private List<Row> rows(boolean preview) {
		return ROWS.get(Util.getMillis(), preview, () -> buildRows(preview));
	}

	/** In the editor, with nothing gaining or pinned, every row shows so there is something to place. */
	private static List<Row> buildRows(boolean preview) {
		Daily daily = CarnageTracker.daily();
		List<Row> rows = daily == null ? new ArrayList<>() : rows(daily, false);
		if (rows.isEmpty() && preview) {
			rows = rows(daily != null ? daily : sampleDaily(), true);
		}
		return rows;
	}

	private static List<Row> rows(Daily daily, boolean showAll) {
		List<Row> rows = new ArrayList<>();
		float scoreAlpha = showAll ? 1f : visibility(PIN_SCORE, CarnageTracker.SCORE_GAIN);
		if (scoreAlpha > 0 && daily.goalDone()) {
			// The daily goal is done: the row follows the repeatable goal it turned into.
			rows.add(row(scoreIcon(), "Repeat goal", daily.repeatProgress(), daily.repeatGoal(),
					MasteryGains.delta(CarnageTracker.SCORE_GAIN), NO_NOTCHES, scoreAlpha));
		} else if (scoreAlpha > 0 && daily.scoreGoal() > 0) {
			rows.add(row(scoreIcon(), "Daily score", daily.score(), daily.scoreGoal(),
					MasteryGains.delta(CarnageTracker.SCORE_GAIN), ticketNotches(daily), scoreAlpha));
		}
		boolean allMissions = showAll || isPinned(PIN_MISSIONS);
		for (Mission mission : daily.missions()) {
			String key = CarnageTracker.missionGain(mission.name());
			float alpha = allMissions ? 1f : MasteryGains.alpha(key);
			if (alpha > 0) {
				rows.add(row(missionIcon(), shortName(mission.name()), mission.current(), mission.target(),
						MasteryGains.delta(key), NO_NOTCHES, alpha));
			}
		}
		return rows;
	}

	private static final double[] NO_NOTCHES = {};

	/** Each ticket threshold short of the goal, as a fraction of it. Tickets cost more as they go. */
	private static double[] ticketNotches(Daily daily) {
		List<Double> at = new ArrayList<>();
		for (int n = 1; n <= daily.maxTickets() && daily.ticketBase() > 0; n++) {
			double threshold = CarnageTracker.ticketThreshold(daily, n);
			if (threshold >= daily.scoreGoal()) {
				break;
			}
			at.add(threshold / daily.scoreGoal());
		}
		return at.stream().mapToDouble(Double::doubleValue).toArray();
	}

	private static Row row(ItemStack icon, String name, double current, double target, double gain,
			double[] notches, float alpha) {
		String label = name + "  " + number(current) + "/" + number(target) + "  " + percent(current, target) + "%";
		String gainText = gain > 0 ? "  +" + trim(gain) : "";
		int width = ICON_SIZE + GAP + font().width(label + gainText);
		double fraction = target <= 0 ? 0 : Math.clamp(current / target, 0.0, 1.0);
		return new Row(icon, label, gainText, width, fraction, notches, alpha);
	}

	/** "Kill 4x Poltergeist" reads as "Poltergeist": the icon and the figures say the rest. */
	static String shortName(String missionName) {
		return KILL_PREFIX.matcher(missionName).replaceFirst("");
	}

	private static Daily sampleDaily() {
		long now = System.currentTimeMillis();
		return new Daily(now + 30 * 3_600_000L, "Stage I", now + 8 * CarnageTracker.DAY_MS, 920, 5_000,
				1, 10, 625, 0, 16, List.of(new Mission("Kill 4x Poltergeist", "poltergeist", 1, 4, "")));
	}

	private static String number(double value) {
		return String.format(Locale.ROOT, "%,d", (long) Math.floor(value));
	}

	private static int percent(double current, double target) {
		return target <= 0 ? 0 : (int) Math.min(100, Math.floor(current * 100 / target));
	}

	private static String trim(double value) {
		return value == Math.rint(value)
				? String.format(Locale.ROOT, "%,d", (long) value)
				: String.format(Locale.ROOT, "%,.1f", value);
	}

	// --- layout ---

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	private static int rowHeight() {
		return Math.max(ICON_SIZE, font().lineHeight) + BAR_GAP + BAR_H;
	}

	@Override
	public int contentWidth(boolean preview) {
		return rows(preview).stream().mapToInt(Row::width).max().orElse(0);
	}

	@Override
	public int contentHeight(boolean preview) {
		int rows = rows(preview).size();
		return rows == 0 ? 0 : rows * rowHeight() + (rows - 1) * ROW_GAP;
	}

	private static int faded(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Math.clamp(alpha, 0f, 1f));
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor context, boolean preview) {
		Font font = font();
		int baseColor = settings().textColor | 0xFF000000;
		int textTop = Math.max(0, (ICON_SIZE - font.lineHeight) / 2);
		int barX = ICON_SIZE + GAP;
		int barW = Math.max(BAR_H, contentWidth(preview) - barX);
		int y = 0;
		for (Row row : rows(preview)) {
			float alpha = row.alpha();
			if (alpha > ICON_FADE_CUTOFF) {
				context.item(row.icon(), 0, y);
			}
			if (alpha >= MIN_TEXT_ALPHA) {
				context.text(font, row.label(), barX, y + textTop, faded(baseColor, alpha), true);
				if (!row.gain().isEmpty()) {
					context.text(font, row.gain(), barX + font.width(row.label()), y + textTop,
							faded(GAIN_COLOR, alpha), true);
				}
			}
			int barY = y + Math.max(ICON_SIZE, font.lineHeight) + BAR_GAP;
			EditorPainter.pill(context, barX, barY, barW, BAR_H, faded(TRACK_COLOR, alpha));
			int filled = (int) Math.round(barW * row.fraction());
			if (filled >= BAR_H) {
				EditorPainter.pill(context, barX, barY, filled, BAR_H, faded(baseColor, alpha));
			} else if (filled > 0) {
				context.fill(barX, barY, barX + filled, barY + BAR_H, faded(baseColor, alpha));
			}
			for (double at : row.notches()) {
				int nx = barX + (int) Math.round(barW * at);
				context.fill(nx, barY, nx + 1, barY + BAR_H, faded(NOTCH_COLOR, alpha));
			}
			y += rowHeight() + ROW_GAP;
		}
	}
}
