package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.Money;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything coinflip says in chat: who posted what, who won it, and what it did to you.
 *
 * <p>Coinflip is the one casino game the whole server hears about, which is what makes the
 * open-flips widget possible without opening anything. One trap in the broadcast, and it
 * is the only real trap in the game: <b>"has just won a $3,000,000 coinflip" is the pot,
 * not the wager.</b> Your own line reports the wager. Read the wrong one and every figure
 * doubles.
 */
public final class CfChat {
	/** A posted flip nobody has taken yet. */
	public record OpenFlip(int id, String player, long wagerCents, long seenAtMs) {
	}

	/** What the last resolved flip did to your money. */
	public record Outcome(long wagerCents, long returnedCents, boolean won, String opponent,
			long atMs) {
	}

	/**
	 * The flip you have just paid for, before its screen has anything to say. The flip
	 * screen states neither the wager nor the id, so this is where they come from.
	 */
	public record Taken(int id, long wagerCents, String opponent, String creatorFace,
			long atMs) {
	}

	/**
	 * The id is bounded rather than {@code \d+}, and the lookahead stops it matching the
	 * front of a longer run of digits. Chat is the one input here that anybody on the
	 * server can shape, and an unbounded group fed to {@code Integer.parseInt} throws
	 * {@link NumberFormatException} straight out of the chat dispatcher. Nine digits is
	 * five orders of magnitude past any real flip id, so anything longer is not a flip.
	 */
	private static final Pattern CREATED = Pattern.compile(
			"Coinflip\\s*»\\s*(\\w{1,16}) has just created a \\$([\\d,]+(?:\\.\\d{1,2})?) "
					+ "Coinflip!.*?/cf take (\\d{1,9})(?!\\d)", Pattern.CASE_INSENSITIVE);
	private static final Pattern WON_SELF = Pattern.compile(
			"Coinflip\\s*»\\s*You have won the \\$([\\d,]+(?:\\.\\d{1,2})?) coinflip against "
					+ "(\\w{1,16}) and received \\$([\\d,]+(?:\\.\\d{1,2})?)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern LOST_SELF = Pattern.compile(
			"Coinflip\\s*»\\s*You have lost the \\$([\\d,]+(?:\\.\\d{1,2})?) coinflip against "
					+ "(\\w{1,16})", Pattern.CASE_INSENSITIVE);
	/** The server-wide announcement. Its figure is the pot. */
	private static final Pattern RESOLVED = Pattern.compile(
			"Coinflip\\s*»\\s*(\\w{1,16}) has just won a \\$([\\d,]+(?:\\.\\d{1,2})?) coinflip "
					+ "against (\\w{1,16})", Pattern.CASE_INSENSITIVE);
	private static final Pattern TOO_POOR = Pattern.compile(
			"Coinflip\\s*»\\s*You do not have enough money to take that coinflip",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern DEBITED = Pattern.compile(
			"MCLabs\\s*»\\s*\\$([\\d,]+(?:\\.\\d{1,2})?) has been taken from your account",
			Pattern.CASE_INSENSITIVE);
	/**
	 * A take, however the player sent it. The broadcast is clickable, so most takes are a
	 * command the mod never typed — bounded the same way {@link #CREATED} is, because the
	 * id in it came off a chat line somebody else wrote.
	 */
	private static final Pattern TAKE = Pattern.compile(
			"^/?cf\\s+take\\s+(\\d{1,9})(?!\\d)", Pattern.CASE_INSENSITIVE);

	/** A post lives this long, and the lobby holds no more than this many. */
	private static final long EXPIRY_MS = 24L * 60L * 60L * 1_000L;
	private static final int MAX_OPEN = 36;
	/**
	 * How long the wager from a plain "taken from your account" stays usable. That line is
	 * not coinflip's — every purchase on the server sends it — so it is only trusted when
	 * a flip screen opens right behind it.
	 */
	private static final long DEBIT_TRUST_MS = 3_000L;
	/**
	 * How long a flip named by a lobby click stays in flight. A result line clears it
	 * anyway; this only bounds what happens when one never arrives.
	 */
	private static final long TAKE_TRUST_MS = 60_000L;

	private static final Map<Integer, OpenFlip> OPEN = new LinkedHashMap<>();
	private static Outcome last;
	private static Taken taken;
	private static long sessionNetCents;
	private static int sessionPlayed;
	private static int sessionWon;

	private CfChat() {
	}

	/**
	 * @param selfName the local player's name, so our own posts are not offered back to us
	 *                 and the result lines can be told from the broadcast of them
	 * @return the flip that just resolved for us, for the caller to fold into the lifetime
	 *         record — kept out of here so this class stays testable against the server's
	 *         exact strings with no config in the way
	 */
	public static Outcome onMessage(String text, String selfName, long nowMs) {
		if (text == null) {
			return null;
		}
		Matcher created = CREATED.matcher(text);
		if (created.find()) {
			String player = created.group(1);
			if (!player.equalsIgnoreCase(selfName)) {
				add(new OpenFlip(Integer.parseInt(created.group(3)), player,
						Money.parseCents(created.group(2)), nowMs));
			}
			return null;
		}
		Matcher won = WON_SELF.matcher(text);
		if (won.find()) {
			long wager = Money.parseCents(won.group(1));
			long returned = Money.parseCents(won.group(3));
			return settle(new Outcome(wager, returned, true, won.group(2), nowMs));
		}
		Matcher lost = LOST_SELF.matcher(text);
		if (lost.find()) {
			long wager = Money.parseCents(lost.group(1));
			return settle(new Outcome(wager, 0L, false, lost.group(2), nowMs));
		}
		Matcher resolved = RESOLVED.matcher(text);
		if (resolved.find()) {
			// Only ever used to drop a row that is no longer takeable. The figure here is
			// the pot, and our own result has already been counted by the lines above.
			retire(resolved.group(1), resolved.group(3),
					Money.parseCents(resolved.group(2)) / 2L);
			return null;
		}
		if (TOO_POOR.matcher(text).find()) {
			taken = null;
			return null;
		}
		Matcher debited = DEBITED.matcher(text);
		// taken(nowMs), not the field: a lobby click whose result line never arrived sits
		// there for a minute, and while it did the debit for the *next* flip was dropped.
		if (debited.find() && taken(nowMs) == null) {
			taken = new Taken(0, Money.parseCents(debited.group(1)), null, null, nowMs);
		}
		return null;
	}

	/**
	 * The flip the player has just clicked in the lobby, which is a better source than the
	 * debit line: it carries the id and the side each player is on.
	 */
	public static void expect(int id, long wagerCents, String opponent, String creatorFace,
			long nowMs) {
		taken = new Taken(id, wagerCents, opponent, creatorFace, nowMs);
		OPEN.remove(id);
	}

	/**
	 * A {@code /cf take <id>} the player has just sent, from wherever they sent it.
	 *
	 * <p>Clicking the broadcast in chat is how a flip is usually taken, and that click does
	 * not travel the way a typed command does — the client puts the packet on the wire
	 * itself, so no send listener ever sees it and {@link #expect} was never called. The
	 * flip screen states neither the wager nor the id, which left the board with nothing to
	 * show but a dash for every take that did not come through the lobby.
	 *
	 * <p>Only a flip already seen posted is armed. An id we know nothing about would arm a
	 * wager of zero, and the board says "this screen never states the wager" rather better
	 * than it says a figure that is wrong.
	 */
	public static void onCommandSent(String command, long nowMs) {
		if (command == null) {
			return;
		}
		Matcher take = TAKE.matcher(command.trim());
		if (!take.find()) {
			return;
		}
		OpenFlip flip = OPEN.get(Integer.parseInt(take.group(1)));
		if (flip == null) {
			return;
		}
		// The broadcast does not say which side the poster claimed, so the faces stay
		// unknown and the board falls back to "you win" / "they win".
		expect(flip.id(), flip.wagerCents(), flip.player(), null, nowMs);
	}

	/** The flip in flight, or null when nothing recent enough is known about one. */
	public static Taken taken(long nowMs) {
		if (taken == null) {
			return null;
		}
		// A lobby click names the flip outright and is trusted for as long as its screen
		// is up; a bare debit line is only trusted for the moment either side of it.
		long window = taken.id() > 0 || taken.opponent() != null
				? TAKE_TRUST_MS
				: DEBIT_TRUST_MS;
		return nowMs - taken.atMs() <= window ? taken : null;
	}

	/** Posted flips still open, newest first. */
	public static List<OpenFlip> openFlips(long nowMs) {
		OPEN.values().removeIf(flip -> nowMs - flip.seenAtMs() > EXPIRY_MS);
		List<OpenFlip> out = new ArrayList<>(OPEN.values());
		out.sort((a, b) -> Long.compare(b.seenAtMs(), a.seenAtMs()));
		return out;
	}

	public static Outcome lastOutcome() {
		return last;
	}

	public static long sessionNetCents() {
		return sessionNetCents;
	}

	public static int sessionPlayed() {
		return sessionPlayed;
	}

	public static int sessionWon() {
		return sessionWon;
	}

	/** Chat state belongs to the server we just left; the lifetime record does not. */
	public static void reset() {
		OPEN.clear();
		last = null;
		taken = null;
		sessionNetCents = 0L;
		sessionPlayed = 0;
		sessionWon = 0;
	}

	private static Outcome settle(Outcome outcome) {
		last = outcome;
		taken = null;
		sessionPlayed++;
		if (outcome.won()) {
			sessionWon++;
		}
		sessionNetCents += outcome.returnedCents() - outcome.wagerCents();
		retire(outcome.opponent(), null, outcome.wagerCents());
		return outcome;
	}

	private static void add(OpenFlip flip) {
		OPEN.remove(flip.id());
		OPEN.put(flip.id(), flip);
		while (OPEN.size() > MAX_OPEN) {
			OPEN.remove(OPEN.keySet().iterator().next());
		}
	}

	/**
	 * Drops a post that has just been played. The broadcast never carries the id, so the
	 * row is matched on the one thing both lines agree about: who posted it and for how
	 * much.
	 */
	private static void retire(String one, String two, long wagerCents) {
		OPEN.values().removeIf(flip -> flip.wagerCents() == wagerCents
				&& (flip.player().equalsIgnoreCase(one)
						|| (two != null && flip.player().equalsIgnoreCase(two))));
	}
}
