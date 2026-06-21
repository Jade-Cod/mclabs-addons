package dev.jade.fishbite.daily;

import dev.jade.fishbite.config.FishBiteConfig;

import java.util.regex.Pattern;

/**
 * Tracks completion of the two daily claims that reset at 9 PM Pacific:
 * the daily spin ({@code /daily}) and the Daily Investor Rewards ({@code /sm claim}).
 * A claim is recorded from its confirmation chat line; a task is "pending" until
 * it has been claimed since the most recent {@link DailyReset} boundary.
 */
public final class DailyTracker {
	/** "Daily bonus streak increased to N days!" or "You've already used your daily spin today!". */
	private static final Pattern SPIN_DONE = Pattern.compile(
			"Daily bonus streak increased to|already used your daily spin today", Pattern.CASE_INSENSITIVE);
	/** "You will receive your Daily Investor Rewards in 5 seconds..." or "Your daily rewards will be available in ...". */
	private static final Pattern SM_DONE = Pattern.compile(
			"receive your Daily Investor Rewards|Your daily rewards will be available in", Pattern.CASE_INSENSITIVE);

	private DailyTracker() {
	}

	public static void onMessage(String text) {
		FishBiteConfig config = FishBiteConfig.get();
		boolean changed = false;
		if (SPIN_DONE.matcher(text).find()) {
			config.dailySpinClaimedMs = System.currentTimeMillis();
			changed = true;
		}
		if (SM_DONE.matcher(text).find()) {
			config.smClaimedMs = System.currentTimeMillis();
			changed = true;
		}
		if (changed) {
			config.save();
		}
	}

	/** Called immediately when the player types "/sm claim", before server confirmation. */
	public static void markSmClaimed() {
		FishBiteConfig config = FishBiteConfig.get();
		config.smClaimedMs = System.currentTimeMillis();
		config.save();
	}

	public static boolean dailyPending() {
		return DailyReset.isPending(FishBiteConfig.get().dailySpinClaimedMs);
	}

	public static boolean smPending() {
		return DailyReset.isPending(FishBiteConfig.get().smClaimedMs);
	}
}
