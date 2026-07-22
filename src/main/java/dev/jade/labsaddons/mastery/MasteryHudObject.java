package dev.jade.labsaddons.mastery;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mastery challenges widget. Each quest is two lines — icon, name, progress and
 * the amount just gained on top, with an exp-style bar underneath:
 *
 * <pre>
 * [icon] Petrified Archer  412/750  55%  +3
 *        ###############--------------
 * </pre>
 *
 * <p>By default this is a notification, not a dashboard: a row appears only when
 * that quest gains progress and fades once {@link MasteryGains#LIFE_MS} passes
 * without another gain, most recently gained first. The editor toggle switches it
 * to showing every active quest permanently.
 *
 * <p>Size is recomputed from the visible rows every frame, so the base class's
 * auto-anchoring keeps a widget parked near a screen edge growing inward rather
 * than off-screen as rows come and go.
 */
public class MasteryHudObject extends HudObject {
	public static final String ID = "mastery";
	private static final int DEFAULT_TEXT_COLOR = 0xFFFFC24F;
	private static final int GAIN_COLOR = 0xFF7BE06B;
	private static final int TRACK_COLOR = 0xFF3A4150;
	private static final int ICON_SIZE = 16;
	private static final int GAP = 4;
	private static final int BAR_GAP = 2;
	private static final int ROW_GAP = 5;
	private static final int BAR_H = 6;
	/** Icons cannot be drawn translucent, so hide them once a fading row is mostly gone. */
	private static final float ICON_FADE_CUTOFF = 0.45f;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.012f;
		defaults.y = 0.30f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	private static boolean alwaysShow() {
		return LabsAddonsConfig.get().masteryAlwaysShow;
	}

	@Override
	public boolean shouldRender() {
		return alwaysShow() ? MasteryTracker.hasData() : MasteryGains.hasRecent();
	}

	@Override
	public List<ToggleOption> toggleOptions() {
		return List.of(new ToggleOption(
				Text.translatable("labsaddons.hud.mastery.always_show"),
				MasteryHudObject::alwaysShow,
				value -> {
					LabsAddonsConfig.get().masteryAlwaysShow = value;
					LabsAddonsConfig.get().save();
				}));
	}

	@Override
	public EditorAction editorAction() {
		// Clears the saved board too, or it would come straight back on next launch.
		return new EditorAction(Text.translatable("labsaddons.hud.mastery.clear"), MasteryStore::clear);
	}

	/** A quest, the opacity its row renders at, and the amount it just gained. */
	private record Row(MasteryQuest quest, float alpha, double delta) {
	}

	private List<Row> rows(boolean preview) {
		List<Row> rows = visibleRows();
		// The editor must always have something to grab, even when nothing has gained
		// recently and the widget would be invisible in play.
		return preview && rows.isEmpty() ? sampleRows() : rows;
	}

	private List<Row> visibleRows() {
		if (alwaysShow()) {
			// Still surface a live gain's "+" while showing the whole board.
			return MasteryTracker.quests().stream()
					.map(quest -> new Row(quest, 1f, MasteryGains.delta(quest.name())))
					.toList();
		}
		// Most recently gained first.
		List<Row> rows = new ArrayList<>();
		for (String name : MasteryGains.recentNames()) {
			MasteryTracker.quests().stream()
					.filter(quest -> quest.name().equals(name))
					.findFirst()
					.ifPresent(quest -> rows.add(
							new Row(quest, MasteryGains.alpha(name), MasteryGains.delta(name))));
		}
		return rows;
	}

	/** Editor preview stand-in: a realistic mix across the Event, Chem, and Pit categories. */
	private static List<Row> sampleRows() {
		return List.of(
				new Row(new MasteryQuest(new ItemStack(Items.CLOCK), "Win Chat Reactions", 38, 100, 38), 1f, 1),
				new Row(new MasteryQuest(new ItemStack(Items.IRON_SWORD), "Kill Petrified Archer", 412, 750, 55), 1f, 3),
				new Row(new MasteryQuest(new ItemStack(Items.RED_WOOL), "Sell to Red Dealer", 631076.685, 1152000, 54),
						1f, 19200));
	}

	private static String progressText(MasteryQuest quest) {
		return abbreviate(quest.current()) + "/" + abbreviate(quest.target()) + "  " + quest.percent() + "%";
	}

	/** The "+N just gained" suffix, or empty when this row has no live gain. */
	private static String gainText(double delta) {
		return delta <= 0 ? "" : "  +" + abbreviate(delta);
	}

	/**
	 * Verb prefixes dropped from quest names. The icon is not enough to tell quests
	 * apart — 14 Pit challenges share the iron sword, and red wool is both "Red
	 * Patrol" and "Sell to Red Dealer" — so the name is shown, and dropping the
	 * shared verb keeps the distinguishing word visible when the column is tight.
	 * Longest prefixes first so "Sell to " wins over "Sell ".
	 */
	private static final String[] NAME_PREFIXES = {
			"Place in ", "Complete ", "Collect ", "Sell to ", "Secure ", "Catch ", "Sell ", "Kill ", "Win ",
	};

	static String shortName(String name) {
		for (String prefix : NAME_PREFIXES) {
			if (name.length() > prefix.length() && name.startsWith(prefix)) {
				return name.substring(prefix.length());
			}
		}
		return name;
	}

	/** Compact magnitude so rows stay narrow: 631076.685 -> "631K", 1152000 -> "1.15M". */
	private static String abbreviate(double value) {
		double abs = Math.abs(value);
		if (abs >= 1_000_000) {
			double scaled = value / 1_000_000;
			return trim(scaled, Math.abs(scaled) < 10 ? 2 : 1) + "M";
		}
		if (abs >= 1_000) {
			double scaled = value / 1_000;
			return trim(scaled, Math.abs(scaled) < 10 ? 1 : 0) + "K";
		}
		return trim(value, value == Math.rint(value) ? 0 : 1);
	}

	private static String trim(double value, int decimals) {
		return String.format(Locale.US, "%,." + decimals + "f", value);
	}

	/** Full top line for one row, used for both measuring and drawing. */
	private static String topLine(Row row) {
		return shortName(row.quest().name()) + "  " + progressText(row.quest()) + gainText(row.delta());
	}

	private static int rowHeight() {
		int fontHeight = MinecraftClient.getInstance().textRenderer.fontHeight;
		return Math.max(ICON_SIZE, fontHeight) + BAR_GAP + BAR_H;
	}

	@Override
	public int contentWidth(boolean preview) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		return rows(preview).stream()
				.mapToInt(row -> ICON_SIZE + GAP + font.getWidth(topLine(row)))
				.max().orElse(0);
	}

	@Override
	public int contentHeight(boolean preview) {
		int rows = rows(preview).size();
		return rows == 0 ? 0 : rows * rowHeight() + (rows - 1) * ROW_GAP;
	}

	/** Applies a row's fade to a colour's alpha channel. */
	private static int faded(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Math.clamp(alpha, 0f, 1f));
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	@Override
	protected void renderContent(DrawContext context, boolean preview) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int baseColor = settings().textColor | 0xFF000000;
		int textTop = Math.max(0, (ICON_SIZE - font.fontHeight) / 2);
		int barX = ICON_SIZE + GAP;
		int barW = Math.max(BAR_H, contentWidth(preview) - barX);

		int y = 0;
		for (Row row : rows(preview)) {
			MasteryQuest quest = row.quest();
			float alpha = row.alpha();

			if (alpha > ICON_FADE_CUTOFF) {
				context.drawItem(quest.icon(), 0, y);
			}

			int textX = ICON_SIZE + GAP;
			String label = shortName(quest.name()) + "  " + progressText(quest);
			context.drawText(font, Text.literal(label), textX, y + textTop, faded(baseColor, alpha), true);

			String gain = gainText(row.delta());
			if (!gain.isEmpty()) {
				context.drawText(font, Text.literal(gain), textX + font.getWidth(label), y + textTop,
						faded(GAIN_COLOR, alpha), true);
			}

			int barY = y + Math.max(ICON_SIZE, font.fontHeight) + BAR_GAP;
			EditorPainter.pill(context, barX, barY, barW, BAR_H, faded(TRACK_COLOR, alpha));
			int filled = (int) Math.round(barW * quest.fraction());
			if (filled >= BAR_H) {
				EditorPainter.pill(context, barX, barY, filled, BAR_H, faded(baseColor, alpha));
			} else if (filled > 0) {
				// Narrower than one pill cap: a plain fill avoids the rounded ends overlapping.
				context.fill(barX, barY, barX + filled, barY + BAR_H, faded(baseColor, alpha));
			}

			y += rowHeight() + ROW_GAP;
		}
	}
}
