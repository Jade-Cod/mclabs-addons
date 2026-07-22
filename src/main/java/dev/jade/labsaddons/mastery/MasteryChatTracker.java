package dev.jade.labsaddons.mastery;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advances the two chat-reaction Mastery challenges live from chat, so the HUD
 * moves the instant a reaction resolves instead of waiting for the next
 * {@code /mastery} scrape.
 *
 * <p>Two outcomes count:
 * <ul>
 * <li><b>Win</b> — {@code » Jade typed the message in 1 minute and 38.005 seconds
 * and won $7,500!} — advances {@value #WIN_QUEST} <em>and</em>
 * {@value #COMPLETE_QUEST}, since winning also completes.</li>
 * <li><b>Runner-up</b> — {@code » Runner-up, 18.336 seconds too late! Earned
 * $2,000!} — advances {@value #COMPLETE_QUEST} only.</li>
 * </ul>
 *
 * <p>The win line names whoever won, so it is matched against the local player;
 * everyone sees it. The runner-up line is addressed to you personally and carries
 * no name, so it needs no ownership check.
 *
 * <p>Bumps are optimistic and in-memory. {@link MasteryTracker#advance} ignores
 * quests the player has not selected, so an inactive challenge never accrues
 * phantom progress, and the next GUI scrape reconciles against the server.
 */
public final class MasteryChatTracker {
	/** Exact quest names as shown in the /mastery GUI. */
	public static final String WIN_QUEST = "Win Chat Reactions";
	public static final String COMPLETE_QUEST = "Complete Chat Reactions";

	/**
	 * Captures the winner's name immediately before " typed the message". Anchoring on
	 * that phrase rather than the line start sidesteps the server's "»" prefix, and the
	 * lazy middle tolerates either time format ("38.005 seconds" or "1 minute and ...").
	 */
	private static final Pattern WIN = Pattern.compile(
			"(\\w{3,16}) typed the message in .+? and won \\$[\\d,]+", Pattern.CASE_INSENSITIVE);

	private static final Pattern RUNNER_UP = Pattern.compile(
			"Runner-up,.+?too late!", Pattern.CASE_INSENSITIVE);

	// ponytail: session username, not the server nickname. If MCLabs ever announces
	// wins under a nick, swap this supplier for one that resolves the display name.
	private static Supplier<String> selfNameSupplier = () -> null;

	private MasteryChatTracker() {
	}

	public static void setSelfNameSupplier(Supplier<String> supplier) {
		selfNameSupplier = supplier == null ? () -> null : supplier;
	}

	/** @return true if an active quest advanced, so the caller can persist the board. */
	public static boolean onMessage(String text) {
		return onMessage(text, selfNameSupplier.get());
	}

	/** Package-private seam so the parsing can be tested without a Minecraft session. */
	static boolean onMessage(String text, String selfName) {
		if (text == null || text.isEmpty()) {
			return false;
		}
		if (RUNNER_UP.matcher(text).find()) {
			return MasteryTracker.advance(COMPLETE_QUEST, 1);
		}
		Matcher win = WIN.matcher(text);
		if (win.find() && selfName != null && selfName.equalsIgnoreCase(win.group(1))) {
			// Both advances must run, so evaluate before combining — a short-circuit
			// here would skip the "Complete" bump whenever "Win" is the active quest.
			boolean won = MasteryTracker.advance(WIN_QUEST, 1);
			boolean completed = MasteryTracker.advance(COMPLETE_QUEST, 1);
			return won || completed;
		}
		return false;
	}
}
