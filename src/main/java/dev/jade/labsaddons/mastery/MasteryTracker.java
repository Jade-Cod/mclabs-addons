package dev.jade.labsaddons.mastery;

import java.util.List;

/**
 * Holds the player's active Mastery challenges for the HUD, in the same static
 * no-injection style as {@code RunnerTracker} / {@code ChumTimer}.
 *
 * <p>{@link #setQuests(List)} is deliberately the only way in. Today the sole
 * caller is {@link MasteryReader} (a passive {@code /mastery} GUI scrape), but
 * MCLabs has been asked to append progress to the chem sell-confirmation message;
 * if that ships, a chat parser calls this same method and nothing else in the
 * feature changes.
 *
 * <p>Progress cannot be derived client-side from sell messages: each dealer
 * applies a prestige multiplier that re-rolls roughly every 20 seconds, so the
 * GUI (or an explicit server-sent figure) is the only trustworthy source.
 */
public final class MasteryTracker {
	private static volatile List<MasteryQuest> quests = List.of();

	private MasteryTracker() {
	}

	/** Replaces the tracked set wholesale (defensive immutable copy; never mutated in place). */
	public static void setQuests(List<MasteryQuest> newQuests) {
		quests = newQuests == null ? List.of() : List.copyOf(newQuests);
	}

	public static List<MasteryQuest> quests() {
		return quests;
	}

	public static boolean hasData() {
		return !quests.isEmpty();
	}

	public static void clear() {
		quests = List.of();
	}
}
