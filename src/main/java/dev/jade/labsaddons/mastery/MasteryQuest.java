package dev.jade.labsaddons.mastery;

import net.minecraft.item.ItemStack;

/**
 * One active Mastery challenge as shown in the {@code /mastery} GUI: the quest's
 * own icon, its display name, and its progress.
 *
 * <p>The icon is the {@link ItemStack} copied straight out of the GUI slot, so no
 * name-to-item mapping is needed — the server already picked the right item (red
 * wool for the Red Dealer, a clock for chat reactions, the chem's own dye for a
 * chem quest, including any custom model data).
 *
 * <p>{@code current} may be fractional (e.g. {@code 631076.685}); {@code percent}
 * is the server's own rounded figure, kept verbatim so the HUD never disagrees
 * with the number the GUI shows.
 */
public record MasteryQuest(ItemStack icon, String name, double current, double target, int percent) {
	/**
	 * Whether the challenge has hit its goal and is waiting to be re-rolled. A quest
	 * with no known target is never complete — there is nothing to have reached.
	 */
	public boolean isComplete() {
		return target > 0 && current >= target;
	}

	/** Progress as a 0..1 fraction, clamped — a finished quest can report current &gt; target. */
	public double fraction() {
		if (target <= 0) {
			return 0;
		}
		return Math.clamp(current / target, 0.0, 1.0);
	}

	/**
	 * A copy advanced by {@code delta}, with the percent recomputed locally.
	 *
	 * <p>Used for optimistic chat-driven bumps between GUI scrapes; the next
	 * {@link MasteryReader} read overwrites it with the server's own figures.
	 *
	 * <p>Stops at the target: a bump that crosses the goal is only trustworthy up to
	 * the goal itself, since whether the server banks the remainder is not visible
	 * from the client. The scrape restores any overflow the server did keep.
	 */
	public MasteryQuest advancedBy(double delta) {
		double next = Math.max(0, current + delta);
		if (target > 0) {
			next = Math.min(next, target);
		}
		return new MasteryQuest(icon, name, next, target, percentOf(next, target));
	}

	/**
	 * Floor, matching the server: it renders 631076.685/1,152,000 as 54% (54.78 floored)
	 * and 319514.42/806,400 as 39% (39.62 floored).
	 */
	static int percentOf(double current, double target) {
		if (target <= 0) {
			return 0;
		}
		return (int) Math.clamp(Math.floor(current / target * 100), 0, 100);
	}
}
