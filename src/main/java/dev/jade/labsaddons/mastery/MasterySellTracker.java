package dev.jade.labsaddons.mastery;

import dev.jade.labsaddons.chem.ChemItems;
import dev.jade.labsaddons.chem.SmugglerSatchel;
import net.minecraft.entity.player.PlayerInventory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advances the {@code Sell <Chem>} and {@code Sell to <Dealer>} Mastery challenges
 * live, by reconstructing what a sale was worth from three sources the client can
 * see:
 *
 * <pre>
 *   delta = Σ  count × rate × MULT[progress purity]      (compound chems only)
 * </pre>
 *
 * <ul>
 * <li><b>count</b> — from the inventory, diffed across the sale. Chat reports only a
 * grand total, never a per-chem breakdown.</li>
 * <li><b>rate</b> — the one number worth taking from
 * {@code » Earned prestige progress for Cactium and Potatium. (1.89x rate)}. The chems
 * it names are the <em>base components</em> of what was sold, not the items themselves,
 * so they identify nothing and are deliberately ignored.</li>
 * <li><b>progress purity</b> — the middle field of a chem's {@code value-progress-score},
 * mapped through {@link #PROGRESS_MULTIPLIER}. Base crops carry no purity at all and
 * earn no Mastery, which is exactly what a missing tier resolves to.</li>
 * </ul>
 *
 * <p><b>The sale line is what arms the capture</b>, not the dealer interaction. An
 * earlier version armed on {@link #onInteract}, which made every chem challenge depend
 * on a client-side entity hook firing and on an NPC's entity name matching its visible
 * nameplate — if either failed, nothing was tracked at all. Now a rolling inventory
 * history supplies the baseline the moment the server confirms a sale, and the dealer
 * is only ever an attribution hint: lose it and {@code Sell to <Dealer>} goes uncredited
 * while every {@code Sell <Chem>} challenge still moves.
 *
 * <p>A sneak-sell empties the Smuggler Satchel too, and those chems never touch the
 * inventory. Their count is the server's total minus everything the diff saw; their
 * identity comes from {@link SmugglerSatchel}, which the player's own satchel opening
 * populates. With no such read the remainder is dropped rather than guessed.
 *
 * <p>Every fallback here under-counts rather than inventing progress, because the
 * next {@code /mastery} scrape is still the authority and will true up the difference.
 */
public final class MasterySellTracker {
	/** Quest name prefixes exactly as the /mastery GUI spells them. */
	private static final String CHEM_QUEST = "Sell ";
	private static final String DEALER_QUEST = "Sell to ";

	/**
	 * How much a chem is worth per progress-purity tier: 0 → 1.00x … 3 → 1.50x.
	 * A tier outside this table earns nothing rather than an extrapolated multiplier —
	 * guessing high would overstate the bar, and the scrape corrects an undercount.
	 */
	static final double[] PROGRESS_MULTIPLIER = {1.00, 1.15, 1.30, 1.50};

	/** Deliberately not anchored on "You've" — the apostrophe's encoding is not worth trusting. */
	private static final Pattern SOLD = Pattern.compile("\\bsold ([\\d,]+) chems\\b");
	private static final String PRESTIGE = "earned prestige progress";
	private static final Pattern RATE = Pattern.compile("\\(([0-9]+(?:\\.[0-9]+)?)x rate\\)");
	/** The rate a prestige line means when it doesn't print one — plain, unboosted. */
	static final double BASE_RATE = 1.0;
	/**
	 * The "<Colour> Dealer" inside a nameplate. Matched loosely rather than anchored,
	 * because several dealers carry a tier symbol ("◆ Orange Dealer", "▲ Green Dealer")
	 * that an anchored pattern rejects while leaving plain ones like "Traveling Dealer"
	 * working — which is exactly how this failed in-game.
	 */
	private static final Pattern DEALER = Pattern.compile("(\\w+)\\s+Dealer\\b");
	/** Legacy colour codes survive in some nameplates; they are not part of the name. */
	private static final Pattern FORMATTING = Pattern.compile("§.");

	/** Quiet ticks after the last sale line before flushing (lets the inventory sync land). */
	private static final int SETTLE_TICKS = 8;
	/**
	 * How far back the baseline is taken from. The server can empty the inventory a
	 * little before it announces the sale, so the diff has to start from before the
	 * chat line, not at it.
	 */
	private static final int LEAD_TICKS = 20;
	/**
	 * How long a dealer interaction stays valid as attribution for the next sale. Sixty
	 * seconds: the menu path lets the player browse before pressing Sell, and every sale
	 * needs a dealer anyway, so a stale hint is far likelier than a wrong one.
	 */
	private static final int DEALER_TTL_TICKS = 1200;

	private static final Deque<Map<ChemItems.ChemKey, Long>> history = new ArrayDeque<>();

	// Null unless a sale is in flight; armed by the server's own confirmation line.
	private static Map<ChemItems.ChemKey, Long> reference;
	private static Map<ChemItems.ChemKey, Long> pending;
	private static String dealer;
	private static int dealerTtl;
	private static long reportedTotal;
	private static double rate;
	private static int settleCountdown;

	private MasterySellTracker() {
	}

	/**
	 * Remembers the dealer the player just interacted with, so the next sale can be
	 * attributed. Both sell paths — sneak right-click, and right-click then the Sell
	 * button — begin with this interaction. Purely an attribution hint: a sale with no
	 * remembered dealer still credits every chem challenge.
	 */
	public static void onInteract(String entityName) {
		String name = dealerName(entityName);
		if (name != null) {
			dealer = name;
			dealerTtl = DEALER_TTL_TICKS;
		}
	}

	/** Arms on the sale total, then collects the rate that follows it a tick later. */
	public static void onMessage(String text) {
		if (text == null) {
			return;
		}
		Matcher sold = SOLD.matcher(text);
		if (sold.find()) {
			if (reference == null) {
				reference = new HashMap<>(baseline());
				pending = new HashMap<>();
				reportedTotal = 0;
				rate = 0;
			}
			// Summed, not replaced: two sales inside one settle window are one flush.
			reportedTotal += parseCount(sold.group(1));
			settleCountdown = SETTLE_TICKS;
			return;
		}
		if (reference == null) {
			return;
		}
		double announced = rateFrom(text);
		if (announced > 0) {
			// The latest rate wins; back-to-back sales at different rates are rare
			// enough that the scrape can own the rounding.
			rate = announced;
			settleCountdown = SETTLE_TICKS;
		}
	}

	/**
	 * The multiplier a prestige line announces, or -1 if this isn't one.
	 *
	 * <p>The server prints "(1.31x rate)" only when the rate is boosted; an unboosted
	 * sale ends the sentence at the chem list. A bare line therefore means
	 * {@value #BASE_RATE}, not "no progress earned" — reading it as zero is what made
	 * the first version credit nothing at all.
	 */
	static double rateFrom(String text) {
		if (text == null || !text.toLowerCase(Locale.ROOT).contains(PRESTIGE)) {
			return -1;
		}
		Matcher matched = RATE.matcher(text);
		return matched.find() ? parseRate(matched.group(1)) : BASE_RATE;
	}

	/** @return true if an active challenge advanced, so the caller can persist the board. */
	public static boolean tick(PlayerInventory inventory) {
		if (dealerTtl > 0 && --dealerTtl == 0) {
			dealer = null;
		}
		if (inventory == null || !hasSellQuest()) {
			// Nothing to track for: drop the history rather than keep snapshotting.
			history.clear();
			reference = null;
			return false;
		}
		Map<ChemItems.ChemKey, Long> snapshot = ChemItems.snapshot(inventory);
		if (reference != null) {
			track(snapshot);
		}
		history.addLast(snapshot);
		while (history.size() > LEAD_TICKS) {
			history.removeFirst();
		}
		if (reference == null || --settleCountdown > 0) {
			return false;
		}
		return flush();
	}

	public static void reset() {
		history.clear();
		reference = null;
		pending = null;
		dealer = null;
		dealerTtl = 0;
		reportedTotal = 0;
		rate = 0;
	}

	private static boolean flush() {
		Map<ChemItems.ChemKey, Long> sold = drops();
		long total = reportedTotal;
		double soldRate = rate;
		String soldTo = dealer;
		ChemItems.ChemKey satchel = SmugglerSatchel.contents();
		reference = null;
		pending = null;
		reportedTotal = 0;
		rate = 0;
		// The hint is spent: the next sale needs its own interaction, so a second sale
		// can't inherit the first one's dealer.
		dealer = null;
		dealerTtl = 0;
		return credit(sold, total, soldRate, soldTo, satchel);
	}

	/**
	 * Applies the formula to one completed sale.
	 *
	 * <p>Package-private seam so the maths can be tested without a Minecraft session.
	 *
	 * @param sold          what left the inventory, by chem and purity
	 * @param reportedTotal the server's grand total, which also covers the satchel
	 * @param rate          the sale's {@code (x.xx rate)} multiplier
	 * @param dealerName    the dealer sold to, or null if it could not be attributed
	 * @param satchel       what the satchel was loaded with, or null if never seen
	 */
	static boolean credit(Map<ChemItems.ChemKey, Long> sold, long reportedTotal, double rate,
			String dealerName, ChemItems.ChemKey satchel) {
		if (rate <= 0) {
			return false; // no rate parsed: every term would be zero anyway
		}
		Map<String, Double> earned = new HashMap<>();
		long counted = 0;
		for (Map.Entry<ChemItems.ChemKey, Long> entry : sold.entrySet()) {
			// Base crops count toward the total the server reported even though they
			// earn no Mastery, so they must be tallied before the tier check drops them.
			counted += entry.getValue();
			add(earned, entry.getKey(), entry.getValue(), rate);
		}
		if (satchel != null) {
			add(earned, satchel, reportedTotal - counted, rate);
		}
		double whole = 0;
		boolean advanced = false;
		for (Map.Entry<String, Double> chem : earned.entrySet()) {
			whole += chem.getValue();
			// OR after the fact: short-circuiting would skip later chems whenever an
			// earlier one is the active pick.
			advanced |= MasteryTracker.advance(CHEM_QUEST + chem.getKey(), chem.getValue());
		}
		if (dealerName != null && whole > 0) {
			advanced |= MasteryTracker.advance(DEALER_QUEST + dealerName, whole);
		}
		return advanced;
	}

	/** Adds one stack's worth of Mastery to its chem, skipping anything that earns none. */
	private static void add(Map<String, Double> earned, ChemItems.ChemKey key, long count, double rate) {
		int tier = progressTier(key.purity());
		if (count <= 0 || tier < 0) {
			return;
		}
		earned.merge(key.chem(), count * rate * PROGRESS_MULTIPLIER[tier], Double::sum);
	}

	/**
	 * The progress tier of a {@code "value-progress-score"} purity — the middle field —
	 * or -1 when there is none. Base chems are plain vanilla items with no purity
	 * component at all, and Mastery does not count them, so -1 is the right answer
	 * rather than a failure.
	 */
	static int progressTier(String purity) {
		if (purity == null) {
			return -1;
		}
		String[] parts = purity.split("-");
		if (parts.length != 3) {
			return -1;
		}
		try {
			int tier = Integer.parseInt(parts[1]);
			return tier >= 0 && tier < PROGRESS_MULTIPLIER.length ? tier : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** "Orange Dealer" out of a nameplate, or null when the entity isn't a dealer. */
	public static String dealerName(String entityName) {
		if (entityName == null) {
			return null;
		}
		String plain = FORMATTING.matcher(entityName).replaceAll("").trim();
		Matcher matched = DEALER.matcher(plain);
		return matched.find() ? matched.group(1) + " Dealer" : null;
	}

	/** Whether any picked challenge is a sell one — the only reason to snapshot at all. */
	private static boolean hasSellQuest() {
		for (MasteryQuest quest : MasteryTracker.quests()) {
			if (quest.name().toLowerCase(Locale.ROOT).startsWith("sell ")) {
				return true;
			}
		}
		return false;
	}

	/** The oldest snapshot still held, i.e. the inventory as of ~{@value #LEAD_TICKS} ticks ago. */
	private static Map<ChemItems.ChemKey, Long> baseline() {
		Map<ChemItems.ChemKey, Long> oldest = history.peekFirst();
		return oldest == null ? Map.of() : oldest;
	}

	/** Fold the latest snapshot into reference/pending — a sale only ever removes chems. */
	private static void track(Map<ChemItems.ChemKey, Long> current) {
		Set<ChemItems.ChemKey> keys = new HashSet<>(reference.keySet());
		keys.addAll(current.keySet());
		for (ChemItems.ChemKey key : keys) {
			long ref = reference.getOrDefault(key, 0L);
			long now = current.getOrDefault(key, 0L);
			if (now < ref) {
				pending.merge(key, ref - now, Long::sum);
			}
			if (now != ref) {
				// A rise is farming or a withdrawal, never a sale; either way the
				// baseline moves so it can't be credited on the next sale.
				reference.put(key, now);
			}
		}
	}

	private static Map<ChemItems.ChemKey, Long> drops() {
		Map<ChemItems.ChemKey, Long> result = new HashMap<>(pending);
		result.values().removeIf(count -> count <= 0);
		return result;
	}

	private static long parseCount(String grouped) {
		try {
			return Long.parseLong(grouped.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static double parseRate(String value) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
