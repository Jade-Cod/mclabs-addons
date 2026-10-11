package dev.jade.labsaddons.daily;

import dev.jade.labsaddons.config.LabsAddonsConfig;

import java.util.regex.Pattern;

/**
 * Tracks completion of the two daily claims that reset at 9 PM Pacific:
 * the daily spin ({@code /daily}) and the Daily Investor Rewards ({@code /sm claim}).
 * A claim is recorded from its confirmation chat line; a task is "pending" until
 * it has been claimed since the most recent {@link DailyReset} boundary.
 */
public final class DailyTracker {
	/**
	 * "Daily Spin » Daily bonus streak increased to N days!" or "You've already used your daily spin
	 * today!". Only the first has been captured, so only it is anchored on its header; the other
	 * relies on the dispatcher's player-chat filter.
	 */
	private static final Pattern SPIN_DONE = Pattern.compile(
			"^Daily Spin\\s*»\\s*Daily bonus streak increased to|already used your daily spin today",
			Pattern.CASE_INSENSITIVE);
	/** "Investor Rewards » Your daily rewards will be available in ..." (captured, anchored) or "You will receive your Daily Investor Rewards in 5 seconds...". */
	private static final Pattern SM_DONE = Pattern.compile(
			"receive your Daily Investor Rewards|^Investor Rewards\\s*»\\s*Your daily rewards will be available in",
			Pattern.CASE_INSENSITIVE);

	private DailyTracker() {
	}

	public static void onMessage(String text) {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		boolean changed = false;
		String line = text.strip();
		if (SPIN_DONE.matcher(line).find()) {
			config.dailySpinClaimedMs = System.currentTimeMillis();
			changed = true;
		}
		if (SM_DONE.matcher(line).find()) {
			config.smClaimedMs = System.currentTimeMillis();
			changed = true;
		}
		if (changed) {
			config.save();
		}
	}

	/** Called immediately when the player types "/sm claim", before server confirmation. */
	public static void markSmClaimed() {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.smClaimedMs = System.currentTimeMillis();
		config.save();
	}

	public static boolean dailyPending() {
		return DailyReset.isPending(LabsAddonsConfig.get().dailySpinClaimedMs);
	}

	public static boolean smPending() {
		return DailyReset.isPending(LabsAddonsConfig.get().smClaimedMs);
	}
}
