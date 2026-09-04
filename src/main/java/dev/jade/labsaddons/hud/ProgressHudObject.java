package dev.jade.labsaddons.hud;

import dev.jade.labsaddons.chem.ChemIcons;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.mastery.MasteryGains;
import dev.jade.labsaddons.mastery.MasteryQuest;
import dev.jade.labsaddons.mastery.MasteryStore;
import dev.jade.labsaddons.mastery.MasteryTracker;
import dev.jade.labsaddons.prestige.PrestigeChem;
import dev.jade.labsaddons.prestige.PrestigeStore;
import dev.jade.labsaddons.prestige.PrestigeTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Progress widget: Mastery challenges and chem prestige tracks in one stack, each
 * an icon, a name, its figures, the amount just gained, and an exp-style bar.
 *
 * <pre>
 * [icon] Petrified Archer  412/750  55%  +3
 *        ###############--------------
 * [icon] Cactium  413K/1.38M  29%  +8,273
 *        #######----------------------
 * </pre>
 *
 * <p>A notification by default, not a dashboard: a row appears when it gains and
 * fades once {@link MasteryGains#LIFE_MS} passes without another. Pinning a row in
 * the editor keeps it on screen permanently instead — so the widget shows exactly
 * what you chose to watch, plus whatever just moved.
 *
 * <p>Both halves are drawn identically on purpose. They differ only in where their
 * icon comes from: a Mastery quest carries the {@code ItemStack} copied out of the
 * {@code /mastery} GUI, while a prestige chem is always a base chem that
 * {@link ChemIcons} resolves by name.
 *
 * <p>Size is recomputed from the visible rows every frame, so the base class's
 * auto-anchoring keeps an edge-parked widget growing inward rather than off-screen
 * as rows come and go.
 */
public class ProgressHudObject extends HudObject {
	public static final String ID = "progress";
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

	@Override
	public boolean shouldRender() {
		return !visibleRows().isEmpty();
	}

	@Override
	public EditorAction editorAction() {
		// Clears the saved boards too, or they would come straight back on next launch.
		return new EditorAction(Component.translatable("labsaddons.hud.progress.clear"), () -> {
			MasteryStore.clear();
			PrestigeStore.clear();
		});
	}

	// --- pinning ---

	private static boolean isPinned(String name) {
		return LabsAddonsConfig.get().pinnedProgressRows.contains(pinKey(name));
	}

	private static String pinKey(String name) {
		return name.toLowerCase(Locale.ROOT).trim();
	}

	private static void setPinned(String name, boolean pinned) {
		Set<String> pins = LabsAddonsConfig.get().pinnedProgressRows;
		if (pinned) {
			pins.add(pinKey(name));
		} else {
			pins.remove(pinKey(name));
		}
		LabsAddonsConfig.get().save();
	}

	@Override
	public Component toggleGroupsLabel() {
		return Component.translatable("labsaddons.hud.progress.pinned");
	}

	/**
	 * One toggle per known row, grouped by source. Only rows we have actually seen are
	 * offered — listing challenges or chems before the first sync would be guesswork.
	 *
	 * <p>Pins are stored by name, so pinning a Mastery challenge lapses when that
	 * challenge is re-rolled away and takes effect again if it ever comes back. Chem
	 * prestige names are fixed, so those pins are permanent.
	 */
	@Override
	public List<ToggleGroup> toggleGroups() {
		List<ToggleGroup> groups = new ArrayList<>();
		addGroup(groups, "labsaddons.hud.progress.mastery",
				MasteryTracker.quests().stream().map(MasteryQuest::name).toList());
		addGroup(groups, "labsaddons.hud.progress.prestige",
				PrestigeTracker.chems().stream().map(PrestigeChem::chem).toList());
		return groups;
	}

	private static void addGroup(List<ToggleGroup> groups, String labelKey, List<String> names) {
		if (names.isEmpty()) {
			return;
		}
		List<ToggleOption> options = names.stream()
				.map(name -> new ToggleOption(Component.literal(shortName(name)),
						() -> isPinned(name),
						pinned -> setPinned(name, pinned)))
				.toList();
		groups.add(new ToggleGroup(Component.translatable(labelKey), options));
	}

	// --- rows ---

	/**
	 * A row from either source. Its figures are rendered to text and its bar to a
	 * fraction up front, because the two sources do not always agree on what exists: a
	 * finished prestige track is reported as "Complete" with no numbers behind it.
	 */
	private record Row(ItemStack icon, String name, String figures, double fraction,
			double delta, float alpha) {
	}

	private static Row of(MasteryQuest quest, float alpha) {
		return new Row(quest.icon(), quest.name(), figures(quest.current(), quest.target(), quest.percent()),
				quest.fraction(), MasteryGains.delta(quest.name()), alpha);
	}

	private static Row of(PrestigeChem chem, float alpha) {
		// A track finished before we ever saw its goal has nothing to count toward, so it
		// says so rather than rendering a meaningless "0/0".
		String figures = chem.hasFigures()
				? figures(chem.current(), chem.target(), chem.percent())
				: Component.translatable("labsaddons.hud.progress.complete").getString();
		return new Row(ChemIcons.iconFor(chem.chem()), chem.chem(), figures, chem.fraction(),
				MasteryGains.delta(chem.chem()), alpha);
	}

	private static String figures(double current, double target, int percent) {
		return abbreviate(current) + "/" + abbreviate(target) + "  " + percent + "%";
	}

	/**
	 * Pinned rows first, in their own stable order, then whatever else has gained
	 * recently, most recent first.
	 *
	 * <p>Pinned rows deliberately do not reorder as they gain: a row you asked to keep
	 * on screen shuffling position every time it moves would defeat the point of
	 * pinning it. Anything already shown as a pin is skipped in the second pass so a
	 * gaining pin is not drawn twice.
	 */
	private static List<Row> visibleRows() {
		// Snapshotted once: this runs several times a frame (measure, then draw), and both
		// accessors copy their backing collection on every call.
		List<MasteryQuest> quests = MasteryTracker.quests();
		List<PrestigeChem> chems = PrestigeTracker.chems();

		List<Row> rows = new ArrayList<>();
		Set<String> shown = new LinkedHashSet<>();
		for (MasteryQuest quest : quests) {
			if (isPinned(quest.name())) {
				rows.add(of(quest, 1f));
				shown.add(quest.name());
			}
		}
		for (PrestigeChem chem : chems) {
			if (isPinned(chem.chem())) {
				rows.add(of(chem, 1f));
				shown.add(chem.chem());
			}
		}
		for (String name : MasteryGains.recentNames()) {
			if (shown.contains(name)) {
				continue;
			}
			quests.stream().filter(q -> q.name().equals(name)).findFirst()
					.ifPresent(quest -> rows.add(of(quest, MasteryGains.alpha(name))));
			chems.stream().filter(c -> c.chem().equals(name)).findFirst()
					.ifPresent(chem -> rows.add(of(chem, MasteryGains.alpha(name))));
		}
		return rows;
	}

	private List<Row> rows(boolean preview) {
		List<Row> rows = visibleRows();
		// The editor must always have something to grab, even when nothing has gained
		// recently and the widget would be invisible in play.
		return preview && rows.isEmpty() ? sampleRows() : rows;
	}

	/** Editor preview stand-in: a realistic mix of Mastery challenges and prestige chems. */
	private static List<Row> sampleRows() {
		return List.of(
				new Row(new ItemStack(Items.CLOCK), "Win Chat Reactions", figures(38, 100, 38), 0.38, 1, 1f),
				new Row(new ItemStack(Items.IRON_SWORD), "Kill Petrified Archer",
						figures(412, 750, 55), 0.55, 3, 1f),
				new Row(new ItemStack(Items.DYE.pick(DyeColor.GREEN)), "Cactium",
						figures(412_880, 1_382_400, 29), 0.29, 8_273, 1f));
	}

	// --- text ---

	/**
	 * Verb prefixes dropped from quest names. The icon is not enough to tell rows
	 * apart — 14 Pit challenges share the iron sword, and red wool is both "Red
	 * Patrol" and "Sell to Red Dealer" — so the name is shown, and dropping the
	 * shared verb keeps the distinguishing word visible when the column is tight.
	 * Longest prefixes first so "Sell to " wins over "Sell ". Chem prestige names
	 * carry no prefix and pass through untouched.
	 */
	private static final String[] NAME_PREFIXES = {
			"Place in ", "Complete ", "Collect ", "Sell to ", "Secure ", "Catch ", "Sell ", "Kill ", "Win ",
	};

	public static String shortName(String name) {
		for (String prefix : NAME_PREFIXES) {
			if (name.length() > prefix.length() && name.startsWith(prefix)) {
				return name.substring(prefix.length());
			}
		}
		return name;
	}

	/** The "+N just gained" suffix, or empty when this row has no live gain. */
	private static String gainText(double delta) {
		return delta <= 0 ? "" : "  +" + abbreviate(delta);
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
		return shortName(row.name()) + "  " + row.figures() + gainText(row.delta());
	}

	// --- layout ---

	private static int rowHeight() {
		int fontHeight = Minecraft.getInstance().font.lineHeight;
		return Math.max(ICON_SIZE, fontHeight) + BAR_GAP + BAR_H;
	}

	@Override
	public int contentWidth(boolean preview) {
		Font font = Minecraft.getInstance().font;
		return rows(preview).stream()
				.mapToInt(row -> ICON_SIZE + GAP + font.width(topLine(row)))
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
	protected void renderContent(GuiGraphicsExtractor context, boolean preview) {
		Font font = Minecraft.getInstance().font;
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

			String label = shortName(row.name()) + "  " + row.figures();
			context.text(font, Component.literal(label), barX, y + textTop, faded(baseColor, alpha), true);

			String gain = gainText(row.delta());
			if (!gain.isEmpty()) {
				context.text(font, Component.literal(gain), barX + font.width(label), y + textTop,
						faded(GAIN_COLOR, alpha), true);
			}

			int barY = y + Math.max(ICON_SIZE, font.lineHeight) + BAR_GAP;
			EditorPainter.pill(context, barX, barY, barW, BAR_H, faded(TRACK_COLOR, alpha));
			int filled = (int) Math.round(barW * Math.clamp(row.fraction(), 0.0, 1.0));
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
