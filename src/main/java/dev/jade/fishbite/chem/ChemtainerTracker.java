package dev.jade.fishbite.chem;

import dev.jade.fishbite.chem.ChemItems.ChemKey;
import dev.jade.fishbite.config.FishBiteConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns the Chemtainer ledger. Three things write to it:
 * <ul>
 *   <li>opening {@code /ch} replaces it with an authoritative scrape ({@link #snapshot});</li>
 *   <li>a captured {@code /ch qd} deposit adds the diffed chems ({@link #applyDeposit});</li>
 *   <li>a {@code /ch withdraw} subtracts what the server reports it returned
 *       ({@link #applyWithdraw}, driven by the "Withdrew N …" chat line).</li>
 * </ul>
 * {@code snapshotMs} is the timestamp of the most recent change of any kind, used
 * for the HUD's "as of …" age line.
 */
public final class ChemtainerTracker {
	private static final Pattern DEPOSITED =
			Pattern.compile("Deposited\\s+([\\d,]+)\\s+chems", Pattern.CASE_INSENSITIVE);
	private static final Pattern FULL =
			Pattern.compile("Chemtainer is full", Pattern.CASE_INSENSITIVE);
	private static final Pattern WITHDREW =
			Pattern.compile("Withdrew\\s+([\\d,]+)\\s+(.+?)\\s+from your chemtainer", Pattern.CASE_INSENSITIVE);

	private ChemtainerTracker() {
	}

	public static void onMessage(String text) {
		Matcher deposited = DEPOSITED.matcher(text);
		if (deposited.find()) {
			ChemtainerDepositCapture.noteDeposit(parseCount(deposited.group(1)));
		} else if (FULL.matcher(text).find()) {
			ChemtainerDepositCapture.noteFull();
		}
		Matcher withdrew = WITHDREW.matcher(text);
		if (withdrew.find()) {
			long count = parseCount(withdrew.group(1));
			ChemKey key = ChemItems.parseLabel(withdrew.group(2));
			if (count > 0 && !key.chem().isEmpty()) {
				applyWithdraw(key, count);
			}
		}
	}

	/** Replace the stored contents with a fresh authoritative scrape of /ch. */
	public static synchronized void snapshot(List<ChemtainerEntry> entries) {
		// An authoritative scrape supersedes any in-flight deposit diff; discard it so
		// a pending flush can't double-count chems this scrape already reflects.
		ChemtainerDepositCapture.cancel();
		FishBiteConfig config = FishBiteConfig.get();
		config.chemtainer = new ArrayList<>(entries);
		config.chemtainerSnapshotMs = System.currentTimeMillis();
		config.saveAsync();
	}

	/** Add a captured deposit (from the inventory diff) to the ledger. */
	public static synchronized void applyDeposit(Map<ChemKey, Long> deposited) {
		FishBiteConfig config = FishBiteConfig.get();
		for (Map.Entry<ChemKey, Long> entry : deposited.entrySet()) {
			add(config.chemtainer, entry.getKey(), entry.getValue());
		}
		config.chemtainerSnapshotMs = System.currentTimeMillis();
		config.saveAsync();
	}

	/** Subtract a withdrawn amount (from the "Withdrew N …" chat line) from the ledger. */
	public static synchronized void applyWithdraw(ChemKey key, long count) {
		FishBiteConfig config = FishBiteConfig.get();
		ChemtainerEntry entry = find(config.chemtainer, key);
		if (entry != null) {
			entry.count -= count;
			if (entry.count <= 0) {
				config.chemtainer.remove(entry);
			}
		}
		config.chemtainerSnapshotMs = System.currentTimeMillis();
		config.saveAsync();
	}

	/** The chem you have the most of (what the withdraw keybind targets), or null. */
	public static synchronized ChemKey largestChem() {
		ChemtainerEntry largest = null;
		for (ChemtainerEntry entry : entries()) {
			if (largest == null || entry.count > largest.count) {
				largest = entry;
			}
		}
		return largest == null ? null : new ChemKey(largest.chem, nullToEmpty(largest.purity));
	}

	/** Total chems stored across all entries (used for the inventory estimate). */
	public static synchronized long totalChems() {
		long total = 0;
		for (ChemtainerEntry entry : entries()) {
			total += entry.count;
		}
		return total;
	}

	public static synchronized List<ChemtainerEntry> entries() {
		return FishBiteConfig.get().chemtainer;
	}

	public static long snapshotMs() {
		return FishBiteConfig.get().chemtainerSnapshotMs;
	}

	public static boolean hasSnapshot() {
		return FishBiteConfig.get().chemtainerSnapshotMs > 0;
	}

	public static synchronized void clear() {
		FishBiteConfig config = FishBiteConfig.get();
		config.chemtainer = new ArrayList<>();
		config.chemtainerSnapshotMs = 0L;
		config.saveAsync();
	}

	private static void add(List<ChemtainerEntry> list, ChemKey key, long count) {
		ChemtainerEntry entry = find(list, key);
		if (entry != null) {
			entry.count += count;
		} else {
			list.add(new ChemtainerEntry(key.chem(), key.purity(), ChemItems.displayLabel(key), count));
		}
	}

	private static ChemtainerEntry find(List<ChemtainerEntry> list, ChemKey key) {
		for (ChemtainerEntry entry : list) {
			if (entry.chem.equalsIgnoreCase(key.chem())
					&& nullToEmpty(entry.purity).equals(key.purity())) {
				return entry;
			}
		}
		return null;
	}

	private static long parseCount(String raw) {
		try {
			return Long.parseLong(raw.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String nullToEmpty(String text) {
		return text == null ? "" : text;
	}
}
