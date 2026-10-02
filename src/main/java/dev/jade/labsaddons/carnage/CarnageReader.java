package dev.jade.labsaddons.carnage;

import dev.jade.labsaddons.carnage.CarnageTracker.Daily;
import dev.jade.labsaddons.carnage.CarnageTracker.Hunt;
import dev.jade.labsaddons.carnage.CarnageTracker.HuntSet;
import dev.jade.labsaddons.carnage.CarnageTracker.Mission;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.hud.Durations;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;

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
	private static final Pattern KILL_COUNT = Pattern.compile("^Kill\\s+([\\d,]+)x\\s", Pattern.CASE_INSENSITIVE);
	private static final Pattern MISSION = Pattern.compile("^\\[([\\d,]+)/([\\d,]+)]\\s+(.+)$");
	private static final Pattern NAMED_COUNT = Pattern.compile("^(.+?)\\s*\\[(\\d+)/(\\d+)]$");
	private static final Pattern DAY_ENDS = Pattern.compile("Current day ends in:\\s*(.+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern STAGE_ENDS = Pattern.compile("(Stage\\s+\\S+)\\s+ends in:\\s*(.+)", Pattern.CASE_INSENSITIVE);

	private CarnageReader() {
	}

	/** @return true if this was a Carnage menu we read. */
	public static boolean tryRead(AbstractContainerScreen<?> screen) {
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

	private static List<SlotView> items(AbstractContainerScreen<?> screen) {
		List<SlotView> items = new ArrayList<>();
		List<Slot> slots = screen.getMenu().slots;
		for (int i = 0; i < slots.size(); i++) {
			ItemStack stack = slots.get(i).getItem();
			if (stack.isEmpty()) {
				items.add(SlotView.empty(i));
				continue;
			}
			ItemLore lore = stack.get(DataComponents.LORE);
			List<String> lines = lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
			items.add(new SlotView(i, stack.getHoverName().getString().trim(), lines, stack.getCount()));
		}
		return items;
	}

	// --- Minecraft-free seams ---

	/** The dashboard, or null if the countdown or missions are missing (not loaded, or not this menu). */
	static Daily dashboard(List<SlotView> items, long nowMs) {
		SlotView countdown = find(items, "Carnage Countdown");
		SlotView missions = find(items, "Daily Missions");
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
		SlotView goal = find(items, "Daily Score Goal");
		double[] score = fraction(goal, "Progress");
		// After the goal: "Daily Goal Complete!", then "Repeatable Goal:" with its own Progress line.
		boolean goalDone = goal != null && goal.lore().stream().anyMatch(l -> l.trim().equals("Daily Goal Complete!"));
		double[] tickets = fraction(find(items, "Daily Raffle"), "You have");
		double[] nextTicket = fraction(find(items, "Daily Raffle"), "Next Ticket");
		double[] goals = fraction(find(items, "Daily Score Goal Bonus"), "Goals Completed");
		List<Mission> list = missions(missions.lore());
		int held = (int) tickets[0];
		if (goalDone) {
			return new Daily(dayEnd, stage, stageEnd, score[0], 0, held, (int) tickets[1],
					ticketBase(nextTicket[1], held), (int) goals[0], (int) goals[1], list, (int) score[1], -1);
		}
		return new Daily(dayEnd, stage, stageEnd, score[0], (int) score[1], held, (int) tickets[1],
				ticketBase(nextTicket[1], held), (int) goals[0], (int) goals[1], list);
	}

	/**
	 * "Next Ticket: 298.6/781.25" states the cost of the ticket after the ones you hold. Each
	 * costs {@link CarnageTracker#RAFFLE_RATIO} times the last, so the first one's cost is that
	 * figure scaled back down.
	 */
	static double ticketBase(double nextCost, int held) {
		return nextCost <= 0 ? 0 : nextCost / Math.pow(CarnageTracker.RAFFLE_RATIO, held);
	}

	/** Each "[a/b] text" line after the summary, with the "• reward" line under its bar. */
	static List<Mission> missions(List<String> lore) {
		List<Mission> missions = new ArrayList<>();
		for (String raw : lore) {
			String line = raw.trim();
			Matcher matcher = MISSION.matcher(line);
			if (line.startsWith("✔ ")) {
				// A finished mission: "✔ Kill 125x Geist", no figures; its target is in the name.
				String name = line.substring(2).trim();
				int target = Math.max(1, toInt(killCount(name)));
				missions.add(new Mission(name, CarnageTracker.killMob(name), target, target, ""));
			} else if (matcher.matches()) {
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

	static Hunt hunt(List<SlotView> items) {
		int found = -1;
		int total = 0;
		List<HuntSet> sets = new ArrayList<>();
		for (SlotView item : items) {
			Matcher matcher = NAMED_COUNT.matcher(item.name().trim());
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

	private static String killCount(String name) {
		Matcher matcher = KILL_COUNT.matcher(name);
		return matcher.find() ? matcher.group(1) : "1";
	}

	/** "Giant Pumpkins [2/12]" as a set, or null for any other name. */
	static HuntSet huntSet(String name) {
		Matcher matcher = NAMED_COUNT.matcher(name == null ? "" : name.trim());
		return matcher.matches()
				? new HuntSet(matcher.group(1).trim(), toInt(matcher.group(2)), toInt(matcher.group(3)))
				: null;
	}

	private static SlotView find(List<SlotView> items, String name) {
		for (SlotView item : items) {
			if (item.name().trim().equalsIgnoreCase(name)) {
				return item;
			}
		}
		return null;
	}

	/** The "a/b" on the lore line starting with {@code label}, or {0, 0}. */
	private static double[] fraction(SlotView item, String label) {
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
