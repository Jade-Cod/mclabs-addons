package dev.jade.labsaddons.mastery;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advances the chat-driven Mastery challenges live, so the HUD moves the instant an
 * event resolves instead of waiting for the next {@code /mastery} scrape. Three
 * families arrive as chat and are parsed here:
 *
 * <ul>
 * <li><b>Chat reactions.</b> A win — {@code » Jade typed the message in 1 minute and
 * 38.005 seconds and won $7,500!} — advances {@value #WIN_QUEST} <em>and</em>
 * {@value #COMPLETE_QUEST}, since winning also completes. A runner-up —
 * {@code » Runner-up, 18.336 seconds too late! Earned $2,000!} — advances
 * {@value #COMPLETE_QUEST} only.</li>
 * <li><b>Bounties.</b> {@code Bounty » Ophiliah has found a bounty chest near the
 * Airport Terminal in 00:51!} (and the {@code the last bounty chest} variant when it
 * closes out the hunt) advances {@value #BOUNTY_QUEST}.</li>
 * <li><b>Mini-event placement.</b> When an event concludes the server posts a
 * standalone standings message listing exactly nine places — which is why the
 * challenges are Top 3 and Top 9 — and the player's rank in it advances
 * {@value #EVENT_WINNER}, {@value #EVENT_TOP_3}, and {@value #EVENT_TOP_9}.</li>
 * </ul>
 *
 * <p>The win, bounty, and standings lines are broadcast to everyone and name whoever
 * earned them, so each is matched against the local player. The runner-up line is
 * addressed to you personally and carries no name, so it needs no ownership check.
 *
 * <p>Bumps are optimistic and in-memory. {@link MasteryTracker#advance} ignores
 * quests the player has not selected, so an inactive challenge never accrues
 * phantom progress, and the next GUI scrape reconciles against the server.
 */
public final class MasteryChatTracker {
	/** Exact quest names as shown in the /mastery GUI. */
	public static final String WIN_QUEST = "Win Chat Reactions";
	public static final String COMPLETE_QUEST = "Complete Chat Reactions";
	public static final String BOUNTY_QUEST = "Secure Bounties";
	public static final String EVENT_WINNER = "Mini-Event Winner";
	public static final String EVENT_TOP_3 = "Mini-Event Top 3";
	public static final String EVENT_TOP_9 = "Mini-Event Top 9";

	/**
	 * Captures the winner's name immediately before " typed the message". Anchoring on
	 * that phrase rather than the line start sidesteps the server's "»" prefix, and the
	 * lazy middle tolerates either time format ("38.005 seconds" or "1 minute and ...").
	 */
	private static final Pattern WIN = Pattern.compile(
			"(\\w{3,16}) typed the message in .+? and won \\$[\\d,]+", Pattern.CASE_INSENSITIVE);

	private static final Pattern RUNNER_UP = Pattern.compile(
			"Runner-up,.+?too late!", Pattern.CASE_INSENSITIVE);

	/** "Bounty » NAME has found a bounty chest near X in 00:51!", or "the last bounty chest". */
	private static final Pattern BOUNTY_FOUND = Pattern.compile(
			"Bounty\\s+»\\s+(\\w{3,16}) has found (?:a|the last) bounty chest", Pattern.CASE_INSENSITIVE);

	/**
	 * The concluding standings arrive as their own message beginning " Top players:",
	 * distinct from the periodic in-progress board that the server prefixes with
	 * "Mini-Event »" and heads "Current top players:". Only the final one is a result,
	 * so the running board is rejected outright before rank is read.
	 */
	private static final String FINAL_STANDINGS = "Top players:";
	private static final String LIVE_STANDINGS = "Current top players";

	/** One "#4. Ophiliah - 78 score" entry; the rank and the player it belongs to. */
	private static final Pattern STANDING = Pattern.compile("#(\\d+)\\.\\s*(\\w{3,16})");

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
		if (win.find()) {
			return isSelf(selfName, win.group(1)) && advanceAll(WIN_QUEST, COMPLETE_QUEST);
		}
		Matcher bounty = BOUNTY_FOUND.matcher(text);
		if (bounty.find()) {
			return isSelf(selfName, bounty.group(1)) && advanceAll(BOUNTY_QUEST);
		}
		return onStandings(text, selfName);
	}

	/** Credits the placement challenges the player's finishing rank satisfies. */
	private static boolean onStandings(String text, String selfName) {
		if (selfName == null || !text.contains(FINAL_STANDINGS) || text.contains(LIVE_STANDINGS)) {
			return false;
		}
		int rank = rankOf(text, selfName);
		if (rank < 1) {
			return false;
		}
		// Placing first is also placing in the top 3, and in the top 9 — the tiers nest,
		// so one finish credits every tier it satisfies.
		if (rank == 1) {
			return advanceAll(EVENT_WINNER, EVENT_TOP_3, EVENT_TOP_9);
		}
		if (rank <= 3) {
			return advanceAll(EVENT_TOP_3, EVENT_TOP_9);
		}
		return rank <= 9 && advanceAll(EVENT_TOP_9);
	}

	/** The player's place in a standings message, or -1 if they are not listed. */
	private static int rankOf(String text, String selfName) {
		Matcher standing = STANDING.matcher(text);
		while (standing.find()) {
			if (isSelf(selfName, standing.group(2))) {
				return Integer.parseInt(standing.group(1));
			}
		}
		return -1;
	}

	private static boolean isSelf(String selfName, String named) {
		return selfName != null && selfName.equalsIgnoreCase(named);
	}

	/**
	 * Advances every named quest. Each call must run, so the results are combined
	 * after the fact — short-circuiting would skip later quests whenever an earlier
	 * one is the active pick.
	 */
	private static boolean advanceAll(String... questNames) {
		boolean advanced = false;
		for (String questName : questNames) {
			advanced |= MasteryTracker.advance(questName, 1);
		}
		return advanced;
	}
}
