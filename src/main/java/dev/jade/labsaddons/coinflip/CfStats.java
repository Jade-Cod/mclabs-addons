package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.config.LabsAddonsConfig;

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
 * <p>The interesting figure is not the profit. It is the profit split in two: 5% of
 * everything you have ever wagered was never yours to keep, and whatever is left over is
 * the coin. {@link Record#luckCents()} is that remainder, and {@link Record#sigma()} says
 * whether it is a bad run or an ordinary one.
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

		/** The part of the loss the 5% tax always accounted for. */
		public long taxCostCents() {
			return -CfOdds.expectedLossCents(wageredCents);
		}

		/** The rest of it — the coin, not the house. */
		public long luckCents() {
			return profitCents() - taxCostCents();
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
	public static void recordWin(long wagerCents, long returnedCents) {
		store(current().plusWin(wagerCents, returnedCents), seeded());
	}

	public static void recordLoss(long wagerCents) {
		store(current().plusLoss(wagerCents), seeded());
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
