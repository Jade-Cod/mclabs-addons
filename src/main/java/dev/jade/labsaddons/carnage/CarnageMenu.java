package dev.jade.labsaddons.carnage;

import dev.jade.labsaddons.casino.SlotView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code /carnage} menus as data: which page is open, and what each page lists.
 *
 * <p>Every page shares one frame. The top row is the nav (leaderboard 0, dashboard 2, hunt 4,
 * shop 6, bestiary 8), the bottom row holds the free-souls claim (47), the countdown clock
 * (49) and the tags link (51), and paged lists put their nine entries in row three (18-26)
 * with previous / next at 30 / 32. Minecraft-free, so it is tested against the menu's own
 * strings.
 */
public final class CarnageMenu {
	public static final int NAV_LEADERBOARD = 0;
	public static final int NAV_DASHBOARD = 2;
	public static final int NAV_HUNT = 4;
	public static final int NAV_SHOP = 6;
	public static final int NAV_BESTIARY = 8;
	public static final int CLAIM_SOULS = 47;
	public static final int COUNTDOWN = 49;
	public static final int NAV_TAGS = 51;
	public static final int LEADERBOARD_SCOPE = 29;
	public static final int PREVIOUS_PAGE = 30;
	public static final int NEXT_PAGE = 32;
	/** The shop's own "claim 5 free souls" head. */
	public static final int SHOP_CLAIM = 40;
	private static final int LIST_FIRST = 18;
	private static final int LIST_LAST = 26;
	private static final int GRID_FIRST = 9;
	private static final int GRID_LAST = 44;

	public static final String COUNTDOWN_NAME = "Carnage Countdown";

	public enum Page {
		DASHBOARD(NAV_DASHBOARD), HUNT(NAV_HUNT), SHOP(NAV_SHOP), LEADERBOARD(NAV_LEADERBOARD),
		BESTIARY(NAV_BESTIARY), TAGS(NAV_TAGS);

		/** The nav slot that opens this page. */
		public final int navSlot;

		Page(int navSlot) {
			this.navSlot = navSlot;
		}

		/** The page a menu title names, or null for anything that isn't a Carnage page. */
		public static Page of(String title) {
			String t = title == null ? "" : title.trim();
			if (t.equals(CarnageReader.DASHBOARD_TITLE)) {
				return DASHBOARD;
			}
			if (t.equals(CarnageReader.HUNT_TITLE)) {
				return HUNT;
			}
			if (t.startsWith("Carnage Shop")) {
				return SHOP;
			}
			if (t.startsWith("Carnage - ")) {
				return LEADERBOARD;
			}
			if (t.equals("Carnage Bestiary")) {
				return BESTIARY;
			}
			if (t.startsWith("Halloween Tags")) {
				return TAGS;
			}
			return null;
		}
	}

	public record Leader(int slot, int rank, String player, String points) {
	}

	public record ShopItem(int slot, String name, int cost) {
	}

	/** {@code stage} is "II" for a monster locked behind a later stage, or "" once it can be hunted. */
	public record Monster(int slot, String name, boolean discovered, String stage) {
	}

	/** {@code next} is the one tag the menu marks "Next tag!": the next to unlock. */
	public record Tag(int slot, String name, boolean unlocked, boolean next, String status) {
	}

	private static final Pattern LEADER = Pattern.compile("^#(\\d+)\\.\\s*(\\S+)$");
	private static final Pattern POINTS = Pattern.compile("^([\\d,.]+)\\s+Carnage Points");
	private static final Pattern COST = Pattern.compile("Cost:\\s*([\\d,]+)\\s+Souls? of Fright", Pattern.CASE_INSENSITIVE);
	private static final Pattern STAGE_MONSTER = Pattern.compile("^Stage\\s+(\\S+)\\s+Monster$");
	private static final Pattern TAGS_AWAY = Pattern.compile("(\\d+)\\s+tags? away", Pattern.CASE_INSENSITIVE);
	/** "Click to switch to overall leaderboard." with the line break the lore puts in it. */
	private static final Pattern SWITCH_TO = Pattern.compile(
			"switch to\\s+(?:the\\s+)?(?:this\\s+)?(\\w+)(?:'s)?\\s+leaderboard", Pattern.CASE_INSENSITIVE);
	/** "(1/2)" or "[1/7]" at the end of a title. */
	private static final Pattern PAGE_OF = Pattern.compile("[(\\[](\\d+)/(\\d+)[)\\]]\\s*$");

	private CarnageMenu() {
	}

	/** The cheap probe: two slot names every Carnage page carries. */
	public static boolean looksLike(String dashboardSlotName, String countdownSlotName) {
		return dashboardSlotName.contains("Carnage Dashboard") && countdownSlotName.contains(COUNTDOWN_NAME);
	}

	public static List<Leader> leaders(List<SlotView> slots) {
		List<Leader> out = new ArrayList<>();
		for (int i = LIST_FIRST; i <= LIST_LAST; i++) {
			SlotView slot = SlotView.at(slots, i);
			Matcher name = slot == null ? null : LEADER.matcher(slot.name().trim());
			if (name == null || !name.matches()) {
				continue;
			}
			String points = "";
			for (String line : slot.lore()) {
				Matcher m = POINTS.matcher(line.trim());
				if (m.find()) {
					points = m.group(1);
					break;
				}
			}
			out.add(new Leader(i, Integer.parseInt(name.group(1)), name.group(2), points));
		}
		return out;
	}

	public static List<ShopItem> shop(List<SlotView> slots) {
		List<ShopItem> out = new ArrayList<>();
		for (int i = GRID_FIRST; i <= GRID_LAST; i++) {
			SlotView slot = SlotView.at(slots, i);
			if (slot == null || slot.isEmpty()) {
				continue;
			}
			for (String line : slot.lore()) {
				Matcher m = COST.matcher(line);
				if (m.find()) {
					out.add(new ShopItem(i, slot.name().trim(), Integer.parseInt(m.group(1).replace(",", ""))));
					break;
				}
			}
		}
		return out;
	}

	public static List<Monster> bestiary(List<SlotView> slots) {
		List<Monster> out = new ArrayList<>();
		for (int i = GRID_FIRST; i <= GRID_LAST; i++) {
			SlotView slot = SlotView.at(slots, i);
			if (slot == null || slot.isEmpty()) {
				continue;
			}
			String stage = "";
			Matcher m = STAGE_MONSTER.matcher(slot.loreLine(0).trim());
			if (m.matches()) {
				stage = m.group(1);
			}
			String name = slot.name().trim();
			out.add(new Monster(i, name, !name.equals("???"), stage));
		}
		return out;
	}

	public static List<Tag> tags(List<SlotView> slots) {
		List<Tag> out = new ArrayList<>();
		for (int i = LIST_FIRST; i <= LIST_LAST; i++) {
			SlotView slot = SlotView.at(slots, i);
			if (slot == null || !slot.name().trim().endsWith(" Tag")) {
				continue;
			}
			String name = slot.name().trim();
			name = name.substring(0, name.length() - " Tag".length());
			// Three states, each its own lore line: "Earned!", "Next tag!", or "3 tags away!".
			boolean unlocked = slot.loreContains("Earned!");
			boolean next = !unlocked && slot.loreContains("Next tag!");
			String status = unlocked ? "unlocked" : next ? "next up" : "";
			for (String line : slot.lore()) {
				Matcher m = TAGS_AWAY.matcher(line);
				if (!unlocked && !next && m.find()) {
					status = m.group(1) + " away";
					break;
				}
			}
			out.add(new Tag(i, name, unlocked, next, status));
		}
		return out;
	}

	/**
	 * The label for the scope button: where it goes next. The server cycles stage, overall,
	 * daily, and says so in the button's own lore ("Click to switch to / overall
	 * leaderboard."), which is read first; the cycle is the fallback.
	 */
	public static String nextScope(SlotView button) {
		if (button == null || button.isEmpty()) {
			return "";
		}
		Matcher m = SWITCH_TO.matcher(String.join(" ", button.lore()));
		if (m.find()) {
			String next = m.group(1).trim().toUpperCase(Locale.ROOT);
			return next.equals("STAGE") ? "THIS STAGE" : next;
		}
		String scope = button.name().trim().toLowerCase(Locale.ROOT);
		if (scope.startsWith("stage")) {
			return "OVERALL";
		}
		return scope.startsWith("overall") ? "DAILY" : "THIS STAGE";
	}

	/** {page, pages} from a title's "(1/2)" or "[1/7]", or null when it has none. */
	public static int[] pageOf(String title) {
		Matcher m = PAGE_OF.matcher(title == null ? "" : title.trim());
		return m.find() ? new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
	}

	/** Whether a paging head is really there: these menus leave the slot empty on the first and last page. */
	public static boolean has(List<SlotView> slots, int index, String namePrefix) {
		SlotView slot = SlotView.at(slots, index);
		return slot != null && slot.name().trim().toLowerCase(Locale.ROOT)
				.startsWith(namePrefix.toLowerCase(Locale.ROOT));
	}

	/** The countdown clock's "Stage I ends in" line, stage name and duration text, or null. */
	public static String[] stageLine(List<SlotView> slots) {
		SlotView clock = SlotView.at(slots, COUNTDOWN);
		if (clock == null) {
			return null;
		}
		for (String line : clock.lore()) {
			int at = line.indexOf(" ends in:");
			if (line.trim().startsWith("Stage") && at > 0) {
				return new String[]{line.substring(0, at).trim(), line.substring(at + " ends in:".length()).trim()};
			}
		}
		return null;
	}
}
