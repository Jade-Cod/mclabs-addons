package dev.jade.fishbite.daily;

import dev.jade.fishbite.config.FishBiteConfig;

import java.util.regex.Pattern;

/**
 * Counts the daily votes (MCLabs has 7 vote sites). Each "Vote registered!" line
 * increments toward {@link #VOTE_GOAL}; the count resets at the 9 PM Pacific
 * {@link DailyReset} boundary.
 */
public final class VoteTracker {
	public static final int VOTE_GOAL = 7;

	private static final Pattern VOTE = Pattern.compile(
			"Vote registered! Claim your vote rewards with /claimvotes", Pattern.CASE_INSENSITIVE);

	private VoteTracker() {
	}

	public static void onMessage(String text) {
		if (!VOTE.matcher(text).find()) {
			return;
		}
		FishBiteConfig config = FishBiteConfig.get();
		long boundary = DailyReset.currentBoundaryMs();
		if (config.voteBoundaryMs < boundary) {
			config.voteCount = 0;
			config.voteBoundaryMs = boundary;
		}
		config.voteCount = Math.min(VOTE_GOAL, config.voteCount + 1);
		config.save();
	}

	/** Votes registered since the current reset (0..VOTE_GOAL). */
	public static int votesDone() {
		FishBiteConfig config = FishBiteConfig.get();
		if (config.voteBoundaryMs < DailyReset.currentBoundaryMs()) {
			return 0;
		}
		return Math.min(VOTE_GOAL, Math.max(0, config.voteCount));
	}

	public static boolean pending() {
		return votesDone() < VOTE_GOAL;
	}

	/** Marks all votes done for the current reset window (local-only override). */
	public static void markAllDone() {
		FishBiteConfig config = FishBiteConfig.get();
		config.voteBoundaryMs = DailyReset.currentBoundaryMs();
		config.voteCount = VOTE_GOAL;
		config.save();
	}
}
