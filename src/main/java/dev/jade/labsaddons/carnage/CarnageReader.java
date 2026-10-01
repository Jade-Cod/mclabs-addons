package dev.jade.labsaddons.carnage;

import dev.jade.labsaddons.carnage.CarnageTracker.Daily;
import dev.jade.labsaddons.carnage.CarnageTracker.Hunt;
import dev.jade.labsaddons.carnage.CarnageTracker.HuntSet;
import dev.jade.labsaddons.carnage.CarnageTracker.Mission;
import dev.jade.labsaddons.hud.Durations;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the {@code /carnage} dashboard and {@code /carnage hunt} menus, once per open.
 *
 * <pre>
 * Daily Missions          [0/3] Complete all daily missions for:   (summary, skipped)
 *                         [0/4] Kill 4x Poltergeist
 *                         [|||||||||]
 *                         • 1x Halloween Crate Key
 * Daily Score Goal        Progress: 0/5,000
 * Daily Raffle            You have 0/10 tickets.  /  Next Ticket: 0/500
 * Daily Score Goal Bonus  Goals Completed: 0/16
 * Carnage Countdown       Current day ends in: 1d:06h:06m  /  Stage I ends in: 8d:06h:06m
 * </pre>
 *
 * The hunt menu states everything in its item names: "Giant Pumpkins [0/12]", and
 * "Master Hunter [0/60]" for the total.
 */
public final class CarnageReader {
	static final String DASHBOARD_TITLE = "Carnage Dashboard";
	static final String HUNT_TITLE = "Carnage Hunt";
	private static final String MASTER_HUNTER = "master hunter";

	private static final Pattern FRACTION = Pattern.compile("([\\d.,]+)\\s*/\\s*([\\d.,]+)");
	private static final Pattern MISSION = Pattern.compile("^\\[([\\d,]+)/([\\d,]+)]\\s+(.+)$");
	private static final Pattern NAMED_COUNT = Pattern.compile("^(.+?)\\s*\\[(\\d+)/(\\d+)]$");
	private static final Pattern DAY_ENDS = Pattern.compile("Current day ends in:\\s*(.+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern STAGE_ENDS = Pattern.compile("(Stage\\s+\\S+)\\s+ends in:\\s*(.+)", Pattern.CASE_INSENSITIVE);

	/** One slot's name and lore as plain text. */
	record Item(String name, List<String> lore) {
	}

	private CarnageReader() {
	}

	/** @return true if this was a Carnage menu we read. */
	public static boolean tryRead(HandledScreen<?> screen) {
		String title = screen.getTitle().getString().trim();
		if (title.equals(DASHBOARD_TITLE)) {
			Daily daily = dashboard(items(screen), System.currentTimeMillis());
			if (daily != null) {
				CarnageTracker.onDashboard(daily);
				return true;
			}
		} else if (title.equals(HUNT_TITLE)) {
			Hunt hunt = hunt(items(screen));
			if (hunt != null) {
				CarnageTracker.onHunt(hunt);
				return true;
			}
		}
		return false;
	}

	private static List<Item> items(HandledScreen<?> screen) {
		List<Item> items = new ArrayList<>();
		for (Slot slot : screen.getScreenHandler().slots) {
			ItemStack stack = slot.getStack();
			if (stack.isEmpty()) {
				continue;
			}
			LoreComponent lore = stack.get(DataComponentTypes.LORE);
			List<String> lines = lore == null ? List.of() : lore.lines().stream().map(Text::getString).toList();
			items.add(new Item(stack.getName().getString().trim(), lines));
		}
		return items;
	}

	// --- Minecraft-free seams ---

	/** The dashboard, or null if the countdown or missions are missing (not loaded, or not this menu). */
	static Daily dashboard(List<Item> items, long nowMs) {
		Item countdown = find(items, "Carnage Countdown");
		Item missions = find(items, "Daily Missions");
		if (countdown == null || missions == null) {
			return null;
		}
		long dayEnd = 0;
		long stageEnd = 0;
		String stage = "";
		for (String line : countdown.lore()) {
			Matcher day = DAY_ENDS.matcher(line);
			Matcher stageLine = STAGE_ENDS.matcher(line);
			if (day.find()) {
				dayEnd = nowMs + Durations.parseMs(day.group(1));
			} else if (stageLine.find()) {
				stage = stageLine.group(1);
				stageEnd = nowMs + Durations.parseMs(stageLine.group(2));
			}
		}
		double[] score = fraction(find(items, "Daily Score Goal"), "Progress");
		double[] tickets = fraction(find(items, "Daily Raffle"), "You have");
		double[] nextTicket = fraction(find(items, "Daily Raffle"), "Next Ticket");
		double[] goals = fraction(find(items, "Daily Score Goal Bonus"), "Goals Completed");
		return new Daily(dayEnd, stage, stageEnd, score[0], (int) score[1],
				(int) tickets[0], (int) tickets[1], (int) nextTicket[1],
				(int) goals[0], (int) goals[1], missions(missions.lore()));
	}

	/** Each "[a/b] text" line after the summary, with the "• reward" line under its bar. */
	static List<Mission> missions(List<String> lore) {
		List<Mission> missions = new ArrayList<>();
		for (String raw : lore) {
			String line = raw.trim();
			Matcher matcher = MISSION.matcher(line);
			if (matcher.matches()) {
				String name = matcher.group(3).trim();
				if (name.toLowerCase(Locale.ROOT).startsWith("complete all")) {
					continue;
				}
				missions.add(new Mission(name, CarnageTracker.killMob(name),
						toInt(matcher.group(1)), toInt(matcher.group(2)), ""));
			} else if (line.startsWith("•") && !missions.isEmpty()) {
				int last = missions.size() - 1;
				Mission mission = missions.get(last);
				if (mission.reward().isEmpty()) {
					missions.set(last, new Mission(mission.name(), mission.mob(), mission.current(),
							mission.target(), line.substring(1).trim()));
				}
			}
		}
		return missions;
	}

	static Hunt hunt(List<Item> items) {
		int found = -1;
		int total = 0;
		List<HuntSet> sets = new ArrayList<>();
		for (Item item : items) {
			Matcher matcher = NAMED_COUNT.matcher(item.name());
			if (!matcher.matches()) {
				continue;
			}
			String name = matcher.group(1).trim();
			int have = toInt(matcher.group(2));
			int of = toInt(matcher.group(3));
			if (name.toLowerCase(Locale.ROOT).equals(MASTER_HUNTER)) {
				found = have;
				total = of;
			} else {
				sets.add(new HuntSet(name, have, of));
			}
		}
		return found < 0 ? null : new Hunt(found, total, sets);
	}

	private static Item find(List<Item> items, String name) {
		for (Item item : items) {
			if (item.name().equalsIgnoreCase(name)) {
				return item;
			}
		}
		return null;
	}

	/** The "a/b" on the lore line starting with {@code label}, or {0, 0}. */
	private static double[] fraction(Item item, String label) {
		if (item != null) {
			for (String line : item.lore()) {
				if (!line.trim().startsWith(label)) {
					continue;
				}
				Matcher matcher = FRACTION.matcher(line);
				if (matcher.find()) {
					Double a = CarnageTracker.parseNumber(matcher.group(1));
					Double b = CarnageTracker.parseNumber(matcher.group(2));
					if (a != null && b != null) {
						return new double[]{a, b};
					}
				}
			}
		}
		return new double[]{0, 0};
	}

	private static int toInt(String text) {
		Double value = CarnageTracker.parseNumber(text);
		return value == null ? 0 : value.intValue();
	}
}
