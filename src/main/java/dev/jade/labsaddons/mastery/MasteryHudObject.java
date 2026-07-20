package dev.jade.labsaddons.mastery;

import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/**
 * Mastery challenges widget: a "Mastery" header then one row per active
 * challenge — the quest's own icon, an exp-style filled bar, and
 * {@code 631K/1.15M 54%}.
 *
 * <p>Data comes from {@link MasteryTracker}, populated by the passive
 * {@link MasteryReader} scrape when the player opens {@code /mastery}.
 */
public class MasteryHudObject extends HudObject {
	public static final String ID = "mastery";
	private static final int DEFAULT_TEXT_COLOR = 0xFFFFC24F;
	private static final int HEADER_COLOR = 0xFFFFFFFF;
	private static final int TRACK_COLOR = 0xFF3A4150;
	private static final int ICON_SIZE = 16;
	private static final int GAP = 4;
	private static final int LINE_GAP = 3;
	private static final int BAR_W = 62;
	private static final int BAR_H = 6;
	/** Cap on the name column; longer names are trimmed with an ellipsis. */
	private static final int NAME_MAX_W = 82;

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
		return MasteryTracker.hasData();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("labsaddons.hud.mastery.clear"), MasteryTracker::clear);
	}

	private List<MasteryQuest> quests(boolean preview) {
		if (preview && !MasteryTracker.hasData()) {
			return sampleQuests();
		}
		return MasteryTracker.quests();
	}

	/** Editor preview stand-in: a realistic mix across the Event, Chem, and Pit categories. */
	private static List<MasteryQuest> sampleQuests() {
		return List.of(
				new MasteryQuest(new ItemStack(Items.IRON_INGOT), "Mini-Event Top 3", 6, 15, 40),
				new MasteryQuest(new ItemStack(Items.CLOCK), "Win Chat Reactions", 37, 100, 37),
				new MasteryQuest(new ItemStack(Items.RED_WOOL), "Sell to Red Dealer", 631076.685, 1152000, 54),
				new MasteryQuest(new ItemStack(Items.IRON_SWORD), "Kill Petrified Archer", 412, 750, 55),
				new MasteryQuest(new ItemStack(Items.LIGHT_GRAY_DYE), "Sell Papcactinide", 319514.42, 806400, 39));
	}

	private static String progressText(MasteryQuest quest) {
		return abbreviate(quest.current()) + "/" + abbreviate(quest.target()) + "  " + quest.percent() + "%";
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

	/** Name column width: widest name, capped so one long quest can't stretch the widget. */
	private int nameColumnWidth(boolean preview) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int widest = quests(preview).stream()
				.mapToInt(quest -> font.getWidth(shortName(quest.name())))
				.max().orElse(0);
		return Math.min(widest, NAME_MAX_W);
	}

	/** Compact magnitude so five rows stay narrow: 631076.685 -> "631K", 1152000 -> "1.15M". */
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

	private static int rowHeight() {
		return Math.max(ICON_SIZE, MinecraftClient.getInstance().textRenderer.fontHeight);
	}

	@Override
	public int contentWidth(boolean preview) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int nameW = nameColumnWidth(preview);
		int rows = quests(preview).stream()
				.mapToInt(quest -> ICON_SIZE + GAP + nameW + GAP + BAR_W + GAP + font.getWidth(progressText(quest)))
				.max().orElse(0);
		return Math.max(rows, font.getWidth(header()));
	}

	@Override
	public int contentHeight(boolean preview) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int rows = quests(preview).size();
		return font.fontHeight + LINE_GAP + rows * rowHeight() + Math.max(0, rows - 1) * LINE_GAP;
	}

	private static String header() {
		return "Mastery";
	}

	@Override
	protected void renderContent(DrawContext context, boolean preview) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int textColor = settings().textColor | 0xFF000000;

		context.drawText(font, Text.literal(header()), 0, 0, HEADER_COLOR, true);
		int y = font.fontHeight + LINE_GAP;

		int height = rowHeight();
		int nameW = nameColumnWidth(preview);
		for (MasteryQuest quest : quests(preview)) {
			context.drawItem(quest.icon(), 0, y + (height - ICON_SIZE) / 2);

			int textY = y + (height - font.fontHeight) / 2 + 1;
			context.drawText(font, Text.literal(font.trimToWidth(shortName(quest.name()), nameW)),
					ICON_SIZE + GAP, textY, textColor, true);

			int barX = ICON_SIZE + GAP + nameW + GAP;
			int barY = y + (height - BAR_H) / 2;
			EditorPainter.pill(context, barX, barY, BAR_W, BAR_H, TRACK_COLOR);
			int filled = (int) Math.round(BAR_W * quest.fraction());
			if (filled >= BAR_H) {
				EditorPainter.pill(context, barX, barY, filled, BAR_H, textColor);
			} else if (filled > 0) {
				// Narrower than one pill cap: a plain fill avoids the rounded ends overlapping.
				context.fill(barX, barY, barX + filled, barY + BAR_H, textColor);
			}

			context.drawText(font, Text.literal(progressText(quest)), barX + BAR_W + GAP,
					textY, textColor, true);
			y += height + LINE_GAP;
		}
	}
}
