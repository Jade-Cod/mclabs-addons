package dev.jade.labsaddons.prestige;

/**
 * One chem's prestige track: how much has been sold toward its goal, and the goal.
 *
 * <p>Unlike a Mastery challenge this carries no icon — every prestige chem is a base
 * chem that {@code ChemIcons} already knows by name, so the icon is resolved at render
 * time and nothing item-shaped needs storing or persisting.
 *
 * <p>The name is kept exactly as the server spells it ({@code "Wheatium"}); matching is
 * case-insensitive so the sell message's spelling need not agree with the list's.
 */
public record PrestigeChem(String chem, double current, double target, boolean unlocked) {
	/**
	 * A track whose figures are known.
	 *
	 * <p>{@code unlocked} stays false: reaching the goal by the numbers already reads as
	 * complete, and a track that has genuinely finished is told to us outright.
	 */
	public PrestigeChem(String chem, double current, double target) {
		this(chem, current, target, false);
	}

	/**
	 * A finished track, as {@code /prestige progress} reports one: the row turns green
	 * with a tick and its hover reads {@code "Pumpkonium: Complete"} — no figures at all.
	 *
	 * <p>Hence the flag. Completion cannot always be inferred from the numbers, because
	 * once a chem is done the server stops stating any.
	 */
	public static PrestigeChem unlocked(String chem) {
		return new PrestigeChem(chem, 0, 0, true);
	}

	/** Whether the goal is met — either stated outright, or reached by the figures. */
	public boolean isComplete() {
		return unlocked || (target > 0 && current >= target);
	}

	/** Progress as a 0..1 fraction, clamped — a finished track can report current &gt; target. */
	public double fraction() {
		if (isComplete()) {
			return 1;
		}
		return target <= 0 ? 0 : Math.clamp(current / target, 0.0, 1.0);
	}

	/** Floored, matching how the server renders its own "(0%)" alongside each bar. */
	public int percent() {
		if (isComplete()) {
			return 100;
		}
		return target <= 0 ? 0 : (int) Math.clamp(Math.floor(current / target * 100), 0, 100);
	}

	/** Whether there are real figures to render; a bare "Complete" has none. */
	public boolean hasFigures() {
		return target > 0;
	}

	/**
	 * A copy advanced by {@code delta}, for the optimistic bump a sale's hover reports.
	 *
	 * <p>Stops at the goal: past it the client cannot tell whether the server banks the
	 * remainder, and the next {@code /prestige progress} is the authority either way.
	 */
	public PrestigeChem advancedBy(double delta) {
		double next = Math.max(0, current + delta);
		if (target > 0) {
			next = Math.min(next, target);
		}
		return new PrestigeChem(chem, next, target);
	}
}
