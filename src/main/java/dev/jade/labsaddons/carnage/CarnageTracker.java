package dev.jade.labsaddons.carnage;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.mastery.MasteryGains;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Halloween Carnage: today's score, raffle tickets and missions, the stage countdown, and
 * Halloween Hunt progress.
 *
 * <p>The {@code /carnage} dashboard and {@code /carnage hunt} menus are the only places
 * that state any of it, so {@link CarnageReader} copies them in whenever they're opened.
 * Between visits the score moves live off the per-kill actionbar
 * ({@code "+4.3 Carnage Points (67 - 67)"}) and Kill missions move live off
 * {@link dev.jade.labsaddons.mastery.MasteryKillTracker}, which already knows how to tell
 * our kills from everyone else's. Both are optimistic; the next {@code /carnage} corrects
 * them.
 *
 * <p>Everything here is a record and the transitions are pure functions of the old state,
 * so the rules (day rollover, goal crossing, mission credit) are testable without a game.
 */
public final class CarnageTracker {
	static final long DAY_MS = 24L * 60 * 60 * 1000;

	/**
	 * Each raffle ticket costs this much more score than the one before. Read off the dashboard's
	 * "Next Ticket" figure through one day: 500, then 625, then 781.25.
	 */
	// ponytail: inferred from three tickets, not stated anywhere; if a later ticket's
	// "Next Ticket" disagrees, this is the number to change.
	static final double RAFFLE_RATIO = 1.25;
	/** "MCLabs Ophiliah just claimed their 5 Free Souls of Fright. Click this message to get yours." */
	private static final Pattern FREE_SOULS = Pattern.compile(
			"^MCLabs\\s*»?\\s*(\\w+) just claimed their \\d+ Free Souls of Fright");
	/**
	 * The free souls are once per event, and the event runs a month: a claim older than this is
	 * a past year's, and the button comes back.
	 */
	// ponytail: a fixed window; key it off the event's end date if MCLabs states one.
	static final long FREE_SOULS_MS = 45L * DAY_MS;
	/** "Carnage » Daily goal completed! Claim your reward with /claim" */
	private static final Pattern GOAL_DONE = Pattern.compile("^Carnage »\\s+Daily goal completed!");
	/** "Carnage » Daily mission complete! (Kill 125x Geist). Claim your reward with /claim" */
	private static final Pattern MISSION_DONE = Pattern.compile("^Carnage »\\s+Daily mission complete! \\((.+?)\\)");
	/**
	 * The repeatable goal that follows the daily one, until a dashboard sync states it: the
	 * menu's "Progress: 391.65/50,000" on day one of Stage I.
	 */
	static final int REPEAT_GOAL = 50_000;
	private static final long SAME_DAY_SLACK_MS = 10 * 60_000L;
	/** "Carnage » Carnage Daily Raffle Ticket 1/10 earned! Next ticket in 512.32 Carnage score." */
	private static final Pattern TICKET_EARNED = Pattern.compile(
			"^Carnage »\\s+Carnage Daily Raffle Ticket (\\d+)/(\\d+) earned!");

	/**
	 * Keys in {@link MasteryGains}, so the HUD rows pop up on a gain and fade exactly like the
	 * progress widget's. Prefixed, so they can never be taken for a quest or chem name.
	 */
	public static final String SCORE_GAIN = "carnage:score";

	public static String missionGain(String missionName) {
		return "carnage:mission:" + missionName;
	}

	/**
	 * "+4.3 Carnage Points (67 - 67)", or with a booster running "+6.6 Carnage Points [1.5x]
	 * (1,740 - 1,740)": anything but a bracket may sit between the words and the figures. The
	 * first figure in the brackets is taken as today's score, the one the Daily Score Goal and
	 * the raffle count against.
	 */
	// ponytail: both figures were equal in the only sample (day one of stage one), so which
	// is daily and which is stage is a guess. Swap the group if the goal bar runs away.
	private static final Pattern POINTS = Pattern.compile(
			"\\+\\s*[\\d.,]+\\s+Carnage Points[^(]*\\(\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern KILL = Pattern.compile("^Kill\\s+[\\d,]+x\\s+(.+)$", Pattern.CASE_INSENSITIVE);

	/** {@code mob} is the lowercase mob a "Kill Nx Mob" mission counts, or null for any other kind. */
	public record Mission(String name, String mob, int current, int target, String reward) {
		public boolean done() {
			return current >= target;
		}

		Mission plusOne() {
			return new Mission(name, mob, Math.min(target, current + 1), target, reward);
		}
	}

	/**
	 * What the dashboard said, moved on by kills since. {@code score} is today's total.
	 *
	 * <p>Once the daily goal is done the dashboard swaps it for a repeatable one: {@code
	 * repeatGoal} is its size (0 until then), and {@code repeatStart} the total score it
	 * started counting from. It starts at the moment the goal completes, with whatever the
	 * finishing kill overshot by thrown away, which is what the menu's figures fit. A
	 * {@code repeatStart} below zero means the total isn't known yet: {@code score} then holds
	 * the menu's repeat progress, and the next kill anchors it.
	 */
	public record Daily(long dayEndMs, String stage, long stageEndMs, double score, int scoreGoal,
			int tickets, int maxTickets, double ticketBase, int goalsDone, int goalsTotal, List<Mission> missions,
			int repeatGoal, double repeatStart) {
		public Daily {
			missions = missions == null ? List.of() : List.copyOf(missions);
			stage = stage == null ? "" : stage;
		}

		public Daily(long dayEndMs, String stage, long stageEndMs, double score, int scoreGoal, int tickets,
				int maxTickets, double ticketBase, int goalsDone, int goalsTotal, List<Mission> missions) {
			this(dayEndMs, stage, stageEndMs, score, scoreGoal, tickets, maxTickets, ticketBase, goalsDone,
					goalsTotal, missions, 0, 0);
		}

		public boolean goalDone() {
			return repeatGoal > 0;
		}

		/** Progress toward the repeatable goal; 0 before the daily goal is done. */
		public double repeatProgress() {
			if (!goalDone()) {
				return 0;
			}
			return repeatStart < 0 ? score : Math.max(0, score - repeatStart);
		}

		Daily with(double score, int tickets, int goalsDone, List<Mission> missions, int repeatGoal,
				double repeatStart) {
			return new Daily(dayEndMs, stage, stageEndMs, score, scoreGoal, tickets, maxTickets, ticketBase,
					goalsDone, goalsTotal, missions, repeatGoal, repeatStart);
		}
	}

	public record HuntSet(String name, int found, int total) {
	}

	public record Hunt(int found, int total, List<HuntSet> sets) {
		public Hunt {
			sets = sets == null ? List.of() : List.copyOf(sets);
		}
	}

	private CarnageTracker() {
	}

	// --- live state (persisted in state.json) ---

	/** Today's board, rolled over to the current day if a reset has passed since. Null until synced. */
	public static Daily daily() {
		Daily stored = LabsAddonsConfig.get().carnageDaily;
		if (stored == null) {
			return null;
		}
		Daily current = rolled(stored, System.currentTimeMillis());
		if (current != stored) {
			store(current);
		}
		return current;
	}

	public static Hunt hunt() {
		return LabsAddonsConfig.get().carnageHunt;
	}

	/**
	 * A dashboard sync. Before the goal it states the total outright. After it, it states only
	 * the repeat progress, so the total we already know is kept and the repeat anchored to it;
	 * without one, the next kill anchors it.
	 */
	public static void onDashboard(Daily synced) {
		store(merge(daily(), synced));
	}

	static Daily merge(Daily known, Daily synced) {
		if (!synced.goalDone()) {
			return synced;
		}
		int scoreGoal = synced.scoreGoal() > 0 ? synced.scoreGoal() : known == null ? 0 : known.scoreGoal();
		double progress = synced.score();
		Daily base = new Daily(synced.dayEndMs(), synced.stage(), synced.stageEndMs(), progress, scoreGoal,
				synced.tickets(), synced.maxTickets(), synced.ticketBase(), synced.goalsDone(), synced.goalsTotal(),
				synced.missions(), synced.repeatGoal(), -1);
		// Same day: both day ends come from a minutes-only countdown read at different times.
		boolean sameDay = known != null && Math.abs(known.dayEndMs() - synced.dayEndMs()) < SAME_DAY_SLACK_MS;
		if (sameDay && known.score() >= progress) {
			return base.with(known.score(), synced.tickets(), synced.goalsDone(), synced.missions(),
					synced.repeatGoal(), known.score() - progress);
		}
		return base;
	}

	public static void onHunt(Hunt hunt) {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.carnageHunt = hunt;
		config.save();
	}

	/** Actionbar text. Moves the score, and the raffle and goal with it. */
	public static void onActionbar(String text) {
		Double score = parseScore(text);
		Daily daily = daily();
		if (score != null && daily != null) {
			if (score > daily.score()) {
				MasteryGains.record(SCORE_GAIN, score - daily.score());
			}
			store(withScore(daily, score));
		}
	}

	public static boolean freeSoulsClaimed() {
		long at = LabsAddonsConfig.get().carnageFreeSoulsClaimedMs;
		return at > 0 && System.currentTimeMillis() - at < FREE_SOULS_MS;
	}

	/** Whether this line is the server saying {@code self} claimed the free souls. */
	static boolean claimedFreeSouls(String text, String self) {
		Matcher matcher = FREE_SOULS.matcher(text.trim());
		return self != null && matcher.find() && matcher.group(1).equalsIgnoreCase(self);
	}

	/** Raffle tickets earned, and your own free-souls claim. */
	public static void onMessage(String text, String self) {
		if (claimedFreeSouls(text, self)) {
			LabsAddonsConfig config = LabsAddonsConfig.get();
			config.carnageFreeSoulsClaimedMs = System.currentTimeMillis();
			config.save();
			return;
		}
		Daily daily = daily();
		if (daily == null) {
			return;
		}
		if (GOAL_DONE.matcher(text.trim()).find()) {
			store(goalCompleted(daily));
			return;
		}
		Matcher mission = MISSION_DONE.matcher(text.trim());
		if (mission.find()) {
			store(missionCompleted(daily, mission.group(1)));
			return;
		}
		onTicket(text);
	}

	/** The server's own count when a ticket is earned, which beats our arithmetic. */
	private static void onTicket(String text) {
		int[] ticket = ticketEarned(text);
		Daily daily = ticket == null ? null : daily();
		if (daily == null) {
			return;
		}
		store(new Daily(daily.dayEndMs(), daily.stage(), daily.stageEndMs(), daily.score(), daily.scoreGoal(),
				ticket[0], ticket[1], daily.ticketBase(), daily.goalsDone(), daily.goalsTotal(), daily.missions(),
				daily.repeatGoal(), daily.repeatStart()));
	}

	/** {held, max} from a "Raffle Ticket 1/10 earned!" line, or null for any other line. */
	static int[] ticketEarned(String text) {
		Matcher matcher = TICKET_EARNED.matcher(text.trim());
		return matcher.find() ? new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))} : null;
	}

	/** @return true if a Kill mission for this mob moved. */
	public static boolean onKill(String mob) {
		Daily daily = daily();
		if (daily == null) {
			return false;
		}
		Daily next = withKill(daily, mob);
		if (next == daily) {
			return false;
		}
		for (int i = 0; i < next.missions().size(); i++) {
			if (next.missions().get(i) != daily.missions().get(i)) {
				MasteryGains.record(missionGain(next.missions().get(i).name()), 1);
			}
		}
		store(next);
		return true;
	}

	/** Lowercase mobs of the Kill missions still open today, for the kill tracker to watch for. */
	public static List<String> killTargets() {
		Daily daily = daily();
		if (daily == null) {
			return List.of();
		}
		return daily.missions().stream()
				.filter(mission -> mission.mob() != null && !mission.done())
				.map(Mission::mob)
				.toList();
	}

	public static void clear() {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.carnageDaily = null;
		config.carnageHunt = null;
		config.save();
	}

	private static void store(Daily daily) {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.carnageDaily = daily;
		config.save();
	}

	// --- pure transitions ---

	/** Total score at which the {@code n}th raffle ticket of the day is earned. */
	public static double ticketThreshold(Daily daily, int n) {
		return daily.ticketBase() * (Math.pow(RAFFLE_RATIO, n) - 1) / (RAFFLE_RATIO - 1);
	}

	/** Tickets a score has earned so far today, up to the cap. */
	static int ticketsAt(Daily daily, double score) {
		if (daily.ticketBase() <= 0) {
			return daily.tickets();
		}
		int n = 0;
		while (n < daily.maxTickets() && ticketThreshold(daily, n + 1) <= score) {
			n++;
		}
		return n;
	}

	static Double parseScore(String text) {
		Matcher matcher = POINTS.matcher(text);
		return matcher.find() ? parseNumber(matcher.group(1)) : null;
	}

	/** The lowercase mob of a "Kill 4x Poltergeist" mission, or null if it isn't one. */
	static String killMob(String missionName) {
		Matcher matcher = KILL.matcher(missionName.trim());
		return matcher.matches() ? matcher.group(1).trim().toLowerCase(Locale.ROOT) : null;
	}

	/**
	 * A new day wipes the score, tickets and missions. The new missions are unknown until the
	 * next {@code /carnage}, so the list goes empty rather than carrying yesterday's forward.
	 * Goals completed is untouched: it is bumped the moment the goal is crossed, not at reset.
	 */
	// ponytail: assumes every day after the first is 24h long (day one ran 30h). The
	// dashboard countdown re-anchors it on every open.
	static Daily rolled(Daily daily, long nowMs) {
		if (nowMs < daily.dayEndMs() || daily.dayEndMs() <= 0) {
			return daily;
		}
		long dayEnd = daily.dayEndMs();
		while (dayEnd <= nowMs) {
			dayEnd += DAY_MS;
		}
		return new Daily(dayEnd, daily.stage(), daily.stageEndMs(), 0, daily.scoreGoal(),
				0, daily.maxTickets(), daily.ticketBase(), daily.goalsDone(), daily.goalsTotal(), List.of(), 0, 0);
	}

	/**
	 * A new total from a kill. Raffle tickets follow it, and crossing the daily goal finishes
	 * it (once). After the goal, a total that drops below the last one means the actionbar is
	 * counting the repeat goal itself rather than the day, and is taken as such.
	 */
	static Daily withScore(Daily daily, double score) {
		int tickets = Math.max(daily.tickets(), ticketsAt(daily, score));
		if (daily.goalDone()) {
			if (daily.repeatStart() < 0) {
				// The dashboard gave repeat progress with no total: this kill anchors it.
				double start = score >= daily.score() ? score - daily.score() : 0;
				return daily.with(score, tickets, daily.goalsDone(), daily.missions(), daily.repeatGoal(), start);
			}
			if (score + 0.5 < daily.score()) {
				return daily.with(score, daily.tickets(), daily.goalsDone(), daily.missions(), daily.repeatGoal(), 0);
			}
			return daily.with(score, tickets, daily.goalsDone(), daily.missions(), daily.repeatGoal(), daily.repeatStart());
		}
		Daily moved = daily.with(score, tickets, daily.goalsDone(), daily.missions(), 0, 0);
		boolean crossed = daily.scoreGoal() > 0 && score >= daily.scoreGoal();
		return crossed ? goalCompleted(moved) : moved;
	}

	/**
	 * The daily goal is done: counted once toward the streak, and the repeatable goal starts
	 * from the current total. Called by whichever arrives first, the crossing kill or the
	 * server's "Daily goal completed!" line.
	 */
	static Daily goalCompleted(Daily daily) {
		if (daily.goalDone()) {
			return daily;
		}
		return daily.with(daily.score(), daily.tickets(), Math.min(daily.goalsTotal(), daily.goalsDone() + 1),
				daily.missions(), REPEAT_GOAL, daily.score());
	}

	/** "Daily mission complete! (Kill 125x Geist)": that mission is done, whatever we'd counted. */
	static Daily missionCompleted(Daily daily, String missionName) {
		List<Mission> missions = new ArrayList<>(daily.missions());
		for (int i = 0; i < missions.size(); i++) {
			Mission mission = missions.get(i);
			if (mission.name().equalsIgnoreCase(missionName.trim()) && !mission.done()) {
				missions.set(i, new Mission(mission.name(), mission.mob(), mission.target(), mission.target(),
						mission.reward()));
				return daily.with(daily.score(), daily.tickets(), daily.goalsDone(), missions, daily.repeatGoal(),
						daily.repeatStart());
			}
		}
		return daily;
	}

	/** Credits the first open Kill mission for this mob; returns the same instance if none. */
	static Daily withKill(Daily daily, String mob) {
		String key = mob.toLowerCase(Locale.ROOT);
		List<Mission> missions = new ArrayList<>(daily.missions());
		for (int i = 0; i < missions.size(); i++) {
			Mission mission = missions.get(i);
			if (key.equals(mission.mob()) && !mission.done()) {
				missions.set(i, mission.plusOne());
				return daily.with(daily.score(), daily.tickets(), daily.goalsDone(), missions, daily.repeatGoal(),
						daily.repeatStart());
			}
		}
		return daily;
	}

	static Double parseNumber(String text) {
		try {
			return Double.parseDouble(text.replace(",", ""));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
