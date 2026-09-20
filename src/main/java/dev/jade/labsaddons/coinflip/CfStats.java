package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.config.LabsAddonsConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Your lifetime coinflip record, as {@code /cf stats} states it and as every flip since
 * has changed it.
 *
 * <p>The mod asks the server for this <b>once ever</b>, the first time you open the lobby,
 * and then never again — the flag and the figures are both on disk, so it does not come
 * back after a restart either. After that seed the record maintains itself from the win and
 * loss lines, so it stays right without another round trip. Running {@code /cf stats}
 * yourself always re-reads it.
 *
 * <p>The lobby also keeps your own last few flips beside it, so the rail says what has
 * actually been happening rather than only what it adds up to.
 */
public final class CfStats {
	/**
	 * A lifetime record. Immutable: a flip returns a new one rather than editing this.
	 *
	 * @param winningsCents everything the server has ever paid back, not the profit
	 */
	public record Record(int played, int won, long wageredCents, long winningsCents) {
		public static final Record EMPTY = new Record(0, 0, 0L, 0L);

		public boolean isEmpty() {
			return played <= 0;
		}

		public int lost() {
			return Math.max(0, played - won);
		}

		/** What the record is actually worth: paid back less staked. */
		public long profitCents() {
			return winningsCents - wageredCents;
		}

		/** "45.9%", or "—" before the first flip. */
		public String winRateText() {
			return isEmpty() ? "—"
					: String.format(Locale.ROOT, "%.1f%%", 100d * won / played);
		}

		public Record plusWin(long wagerCents, long returnedCents) {
			return new Record(played + 1, won + 1, wageredCents + wagerCents,
					winningsCents + returnedCents);
		}

		public Record plusLoss(long wagerCents) {
			return new Record(played + 1, won, wageredCents + wagerCents, winningsCents);
		}
	}

	// Each line of /cf stats arrives as its own chat message, so each is matched alone.
	private static final Pattern PLAYED = Pattern.compile(
			"Coinflips\\s+Played:\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern WON = Pattern.compile(
			"Coinflips\\s+Won:\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE);
	/** Total Wagered is the one line the server prints without a dollar sign. */
	private static final Pattern WAGERED = Pattern.compile(
			"Total\\s+Wagered:\\s*(\\$?[\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern WINNINGS = Pattern.compile(
			"Total\\s+Winnings:\\s*(\\$?[\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);

	/** How many of your own flips the lobby keeps. Two more than it can show. */
	private static final int RECENT_KEPT = 7;

	/**
	 * How many times one run of the game may ask for the seed before giving up.
	 *
	 * <p>The flag that stops it asking is only set by a line the parse recognises, so if
	 * the server ever reworded {@code /cf stats}, "ask once ever" would quietly become "ask
	 * on every lobby open" — and the lobby is reopened by its own refresh button. Counting
	 * the asks bounds that at three commands and three blocks of chat rather than one per
	 * refresh for as long as somebody is browsing.
	 */
	private static final int MAX_ASKS = 3;
	private static int asks;

	private CfStats() {
	}

	/**
	 * Applies one line of {@code /cf stats} to a record, returning the result — or the
	 * record unchanged when the line says nothing about it.
	 *
	 * <p>Pure, so the whole parse can be tested against the exact five lines the server
	 * sends without a config or a client in the way.
	 */
	public static Record apply(Record current, String line) {
		if (line == null) {
			return current;
		}
		Matcher played = PLAYED.matcher(line);
		if (played.find()) {
			return new Record(count(played.group(1)), current.won(),
					current.wageredCents(), current.winningsCents());
		}
		Matcher won = WON.matcher(line);
		if (won.find()) {
			return new Record(current.played(), count(won.group(1)),
					current.wageredCents(), current.winningsCents());
		}
		Matcher wagered = WAGERED.matcher(line);
		if (wagered.find()) {
			return new Record(current.played(), current.won(),
					Money.parseCents(wagered.group(1)), current.winningsCents());
		}
		Matcher winnings = WINNINGS.matcher(line);
		if (winnings.find()) {
			return new Record(current.played(), current.won(),
					current.wageredCents(), Money.parseCents(winnings.group(1)));
		}
		return current;
	}

	private static int count(String digits) {
		try {
			return Integer.parseInt(digits.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// --- the live record, kept in the config so the seed is asked for once ----

	public static Record current() {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		return new Record(config.coinflipPlayed, config.coinflipWon,
				config.coinflipWageredCents, config.coinflipWinningsCents);
	}

	/** Whether the server has ever told us the lifetime figures. */
	public static boolean seeded() {
		return LabsAddonsConfig.get().coinflipStatsSeeded;
	}

	/**
	 * Whether to send {@code /cf stats} now, counting the ask so an answer that never
	 * arrives cannot keep the question open. Call it last, once the screen is known to be
	 * the lobby — it has a side effect.
	 *
	 * <p>The count is per run of the game rather than persisted: a seed that failed because
	 * the player was mid-join is worth one more try next launch, and three asks is already
	 * the bound that matters.
	 */
	public static boolean shouldAsk() {
		if (seeded() || asks >= MAX_ASKS) {
			return false;
		}
		asks++;
		return true;
	}

	/**
	 * Reads a {@code /cf stats} line if this is one.
	 *
	 * @return true when the record changed, so the caller can persist it
	 */
	public static boolean onMessage(String text) {
		Record before = current();
		Record after = apply(before, text);
		// Reference identity, not equality. Once the record is being kept up to date from
		// the result lines, a /cf stats that agrees with it changes no value — and testing
		// for a changed value meant the seed flag was never set, so it asked again on every
		// single open. A matched line is proof the server answered, which is all the flag
		// is for.
		if (after == before) {
			return false;
		}
		store(after, true);
		return true;
	}

	/** A flip we just won, folded into the record so it never needs re-asking. */
	public static void recordWin(long wagerCents, long returnedCents, String opponent) {
		remember(new CfPlayed(true, returnedCents - wagerCents, opponent));
		store(current().plusWin(wagerCents, returnedCents), seeded());
	}

	public static void recordLoss(long wagerCents, String opponent) {
		remember(new CfPlayed(false, -wagerCents, opponent));
		store(current().plusLoss(wagerCents), seeded());
	}

	/** Your own last few flips, newest first. */
	public static List<CfPlayed> recent() {
		List<CfPlayed> kept = LabsAddonsConfig.get().coinflipRecent;
		return kept == null ? List.of() : List.copyOf(kept);
	}

	private static void remember(CfPlayed flip) {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		if (config.coinflipRecent == null) {
			config.coinflipRecent = new ArrayList<>();
		}
		config.coinflipRecent.add(0, flip);
		while (config.coinflipRecent.size() > RECENT_KEPT) {
			config.coinflipRecent.remove(config.coinflipRecent.size() - 1);
		}
	}

	private static void store(Record record, boolean seeded) {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.coinflipPlayed = record.played();
		config.coinflipWon = record.won();
		config.coinflipWageredCents = record.wageredCents();
		config.coinflipWinningsCents = record.winningsCents();
		config.coinflipStatsSeeded = seeded;
		config.save();
	}
}
