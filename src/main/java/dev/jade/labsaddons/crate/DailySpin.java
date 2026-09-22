package dev.jade.labsaddons.crate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether the roll on screen is the daily spin rather than a voter crate.
 *
 * <p>The two animations are the same animation. {@code /daily}'s spin and a voter crate's
 * both arrive as {@code "Rolling rewards..."}, then {@code "Choose a reward!"}, both 27
 * slots, both with the three reels in slots 11, 13 and 15 and the other twenty-four filled
 * with unnamed grey panes. Nothing on either screen says which it is, and the reward pools
 * overlap — {@code $25,000}, {@code $75,000} and {@code Hopper x3} are in both — so the
 * rewards cannot settle it either, and guessing from them put a voter crate's percentage
 * under a daily reward.
 *
 * <p>What does settle it is where the roll came from. The daily spin is opened from
 * {@code "Daily Bonus (98 day streak)"}, which closes the same instant the roll opens; a
 * voter crate is rolled by right-clicking a key with no menu open at all. So the last menu
 * seen before the roll is the whole of the answer, and it carries the streak with it.
 *
 * <p>Three things disarm it, because the spin is only a daily until it is over: the server's
 * own line saying what you chose, any other menu opening, and a timeout in case you walk
 * away from the choice without making one. Closing the screen deliberately does not — the
 * daily menu hands over to the roll within the same millisecond, and dropping the arm on a
 * frame with no screen would lose the handover.
 *
 * <p>Minecraft-free so both the title and the line can be tested against the real strings.
 */
public final class DailySpin {
	private static final Pattern STREAK = Pattern.compile(
			"daily bonus\\s*\\((\\d[\\d,]*)\\s*day streak\\)", Pattern.CASE_INSENSITIVE);
	/** The server's own line once a reward has been taken, which ends the spin. */
	private static final Pattern CHOSE = Pattern.compile(
			"^MCLabs » You rolled your Daily Spin and chose\\b", Pattern.CASE_INSENSITIVE);
	/** Longest a spin can stay armed with nothing else happening. */
	private static final long ARM_MS = 120_000L;

	private static int streak;
	private static long armedAtMs;

	private DailySpin() {
	}

	/** The streak out of "Daily Bonus (98 day streak)", or 0 when this is not that menu. */
	public static int streakOf(String title) {
		if (title == null) {
			return 0;
		}
		Matcher matcher = STREAK.matcher(title.trim());
		if (!matcher.find()) {
			return 0;
		}
		try {
			return Integer.parseInt(matcher.group(1).replace(",", ""));
		} catch (NumberFormatException absurd) {
			return 0;
		}
	}

	/**
	 * Whether this line is the server saying the spin is over.
	 *
	 * <p>Anchored to how the server opens it, like every other line the mod acts on: a player
	 * quoting it in chat arrives with their name and rank in front and moves nothing.
	 */
	public static boolean isChoiceLine(String text) {
		return text != null && CHOSE.matcher(text.trim()).find();
	}

	/** Call once per menu opened. */
	public static void onScreen(String title, long nowMs) {
		int found = streakOf(title);
		if (found > 0) {
			streak = found;
			armedAtMs = nowMs;
			return;
		}
		// The spin itself, which is what the arm is for.
		if (CrateTitles.isVoteRoll(title) || CrateTitles.isVoteChoice(title)) {
			return;
		}
		reset();
	}

	public static void onMessage(String text) {
		if (isChoiceLine(text)) {
			reset();
		}
	}

	/** Whether a roll on screen now is the daily one. */
	public static boolean isSpinning(long nowMs) {
		return armedAtMs > 0L && nowMs >= armedAtMs && nowMs - armedAtMs <= ARM_MS;
	}

	/** How many days running, as the daily menu's own title stated it. */
	public static int streak() {
		return streak;
	}

	/** Call on disconnect: the spin belongs to the server we just left. */
	public static void reset() {
		streak = 0;
		armedAtMs = 0L;
	}

	/** Visible for testing. */
	static void armed(int days, long atMs) {
		streak = days;
		armedAtMs = atMs;
	}
}
