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
	 * Keys in {@link MasteryGains}, so the HUD rows pop up on a gain and fade exactly like the
	 * progress widget's. Prefixed, so they can never be taken for a quest or chem name.
	 */
	public static final String SCORE_GAIN = "carnage:score";

	public static String missionGain(String missionName) {
		return "carnage:mission:" + missionName;
	}

	/**
	 * "+4.3 Carnage Points (67 - 67)". The first figure in the brackets is taken as today's
	 * score, the one the Daily Score Goal and the raffle count against.
	 */
	// ponytail: both figures were equal in the only sample (day one of stage one), so which
	// is daily and which is stage is a guess. Swap the group if the goal bar runs away.
	private static final Pattern POINTS = Pattern.compile(
			"\\+\\s*[\\d.,]+\\s+Carnage Points\\s*\\(\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE);
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

	/** What the dashboard said, moved on by kills since. {@code score} is today's. */
	public record Daily(long dayEndMs, String stage, long stageEndMs, double score, int scoreGoal,
			int tickets, int maxTickets, int ticketCost, int goalsDone, int goalsTotal, List<Mission> missions) {
		public Daily {
			missions = missions == null ? List.of() : List.copyOf(missions);
			stage = stage == null ? "" : stage;
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

	public static void onDashboard(Daily daily) {
		store(daily);
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
				0, daily.maxTickets(), daily.ticketCost(), daily.goalsDone(), daily.goalsTotal(), List.of());
	}

	/** Raffle tickets are one per {@code ticketCost} of today's score, up to the cap. */
	static Daily withScore(Daily daily, double score) {
		boolean crossedGoal = daily.scoreGoal() > 0 && daily.score() < daily.scoreGoal() && score >= daily.scoreGoal();
		int tickets = daily.tickets();
		if (daily.ticketCost() > 0) {
			tickets = Math.max(tickets, Math.min(daily.maxTickets(), (int) (score / daily.ticketCost())));
		}
		int goalsDone = crossedGoal ? Math.min(daily.goalsTotal(), daily.goalsDone() + 1) : daily.goalsDone();
		return new Daily(daily.dayEndMs(), daily.stage(), daily.stageEndMs(), score, daily.scoreGoal(),
				tickets, daily.maxTickets(), daily.ticketCost(), goalsDone, daily.goalsTotal(), daily.missions());
	}

	/** Credits the first open Kill mission for this mob; returns the same instance if none. */
	static Daily withKill(Daily daily, String mob) {
		String key = mob.toLowerCase(Locale.ROOT);
		List<Mission> missions = new ArrayList<>(daily.missions());
		for (int i = 0; i < missions.size(); i++) {
			Mission mission = missions.get(i);
			if (key.equals(mission.mob()) && !mission.done()) {
				missions.set(i, mission.plusOne());
				return new Daily(daily.dayEndMs(), daily.stage(), daily.stageEndMs(), daily.score(),
						daily.scoreGoal(), daily.tickets(), daily.maxTickets(), daily.ticketCost(),
						daily.goalsDone(), daily.goalsTotal(), missions);
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
