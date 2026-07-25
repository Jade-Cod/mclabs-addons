package dev.jade.labsaddons.mastery;

import java.util.ArrayList;
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

	/**
	 * Replaces the tracked set wholesale (defensive immutable copy; never mutated in place),
	 * recording a gain for every quest whose progress rose since the last set.
	 *
	 * <p>The first set establishes a baseline only — with nothing to diff against, an
	 * opening {@code /mastery} must not report the whole board as freshly gained. A quest
	 * absent from the previous set (newly re-rolled) is likewise baseline-only.
	 *
	 * <p>Deltas are computed against the local values, which already include any optimistic
	 * chat bumps, so a chat win followed by a scrape that confirms it yields a zero delta
	 * rather than counting the same event twice.
	 */
	public static void setQuests(List<MasteryQuest> newQuests) {
		List<MasteryQuest> previous = quests;
		List<MasteryQuest> next = newQuests == null ? List.of() : List.copyOf(newQuests);
		if (!previous.isEmpty()) {
			for (MasteryQuest quest : next) {
				previous.stream()
						.filter(old -> old.name().equalsIgnoreCase(quest.name()))
						.findFirst()
						.ifPresent(old -> MasteryGains.record(quest.name(), quest.current() - old.current()));
			}
		}
		quests = next;
	}

	public static List<MasteryQuest> quests() {
		return quests;
	}

	public static boolean hasData() {
		return !quests.isEmpty();
	}

	/**
	 * Optimistically advances one quest by {@code delta}, but <em>only</em> if it is
	 * currently active — an unselected challenge earns nothing, so a chat event for
	 * a quest the player has not picked must be ignored rather than invented.
	 *
	 * <p>The bump is local and in-memory; the next {@code /mastery} scrape replaces
	 * it with the server's authoritative figures.
	 *
	 * <p>A challenge that has already reached its target is left alone: it is finished
	 * and sits there until the player re-rolls it, so counting past the goal would
	 * invent progress the client cannot verify. Whether the server caps at the target
	 * or banks the overflow, the scrape stays the authority — so refusing to guess is
	 * never worse than guessing wrong.
	 *
	 * @return true if an active, unfinished quest matched and was advanced.
	 */
	public static boolean advance(String questName, double delta) {
		List<MasteryQuest> current = quests;
		List<MasteryQuest> updated = new ArrayList<>(current.size());
		String matchedName = null;
		double applied = 0;
		for (MasteryQuest quest : current) {
			if (matchedName == null && quest.name().equalsIgnoreCase(questName) && !quest.isComplete()) {
				// Record under the quest's own spelling: matching is case-insensitive, but
				// the HUD looks gains up by the exact name it renders.
				matchedName = quest.name();
				MasteryQuest advanced = quest.advancedBy(delta);
				// The pop-up must show what actually landed: a bump clamped at the target
				// moved less than the caller asked for, and "+5" on a +1 move would lie.
				applied = advanced.current() - quest.current();
				updated.add(advanced);
			} else {
				updated.add(quest);
			}
		}
		if (matchedName != null) {
			quests = List.copyOf(updated);
			MasteryGains.record(matchedName, applied);
		}
		return matchedName != null;
	}

	public static void clear() {
		quests = List.of();
	}
}
