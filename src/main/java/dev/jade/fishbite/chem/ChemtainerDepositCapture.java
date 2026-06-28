package dev.jade.fishbite.chem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Learns which chems a {@code /ch qd} quick-deposit moved by watching the
 * inventory, since the server prints only a grand total ("Deposited N chems"),
 * never a per-item breakdown.
 *
 * <p>A quick-deposit only ever <em>removes</em> chems from the inventory, so each
 * downward change of a chem's count is a deposit and each upward change (farming /
 * pickup) is not. We keep a per-key {@code reference} of what's already accounted
 * for: a drop credits the difference to {@code pending} and lowers the reference;
 * a rise just raises the reference. This is what makes farming-while-depositing and
 * rapid back-to-back deposits reconcile correctly, where the old single before/after
 * diff subtracted concurrent pickups straight out of the deposit (and a re-press
 * threw the whole in-flight capture away).
 *
 * <p>At flush we reconcile {@code pending} against the authoritative server total
 * (summed from the "Deposited N chems" lines): the inventory diff decides
 * <em>which</em> chems moved and in what proportion, the server total decides
 * <em>how many</em>. That clamps phantom counts from eating/dropping a chem mid
 * window and recovers a deposit a same-tick pickup would otherwise hide. A
 * "Chemtainer is full" line carries no number, so when only those arrive we trust
 * the raw drops (a partial deposit leaves the overflow in the inventory untouched,
 * so the observed drop already equals what was banked).
 */
public final class ChemtainerDepositCapture {
	/** Quiet ticks after the last deposit line before we flush (lets async sync land). */
	private static final int SETTLE_TICKS = 8;
	/** Ticks to wait for a deposit confirmation before discarding an unconfirmed arm. */
	private static final int CONFIRM_TIMEOUT_TICKS = 60;

	private static Map<ChemItems.ChemKey, Long> reference;
	private static Map<ChemItems.ChemKey, Long> pending;
	private static long reportedTotal;
	private static boolean haveTotal;
	private static boolean depositSeen;
	private static int settleCountdown;
	private static int confirmTimeout;

	private ChemtainerDepositCapture() {
	}

	/**
	 * Arm (or extend) a capture around a quick-deposit. The first arm snapshots the
	 * inventory as the baseline; arming again while a capture is still active only
	 * extends the settle window, so the double-arm per keypress and rapid re-presses
	 * never discard an in-flight capture.
	 */
	public static void arm(Map<ChemItems.ChemKey, Long> snapshot) {
		if (reference == null) {
			reference = new HashMap<>(snapshot);
			pending = new HashMap<>();
			reportedTotal = 0;
			haveTotal = false;
			depositSeen = false;
			settleCountdown = SETTLE_TICKS;
			confirmTimeout = CONFIRM_TIMEOUT_TICKS;
		} else {
			// A new quick-deposit while one is still settling: keep the running
			// reference/pending and just give the new deposit time to confirm.
			settleCountdown = SETTLE_TICKS;
		}
	}

	/** A numbered "Deposited N chems" line: confirm the deposit and add N to the total. */
	public static void noteDeposit(long count) {
		if (reference == null) {
			return;
		}
		depositSeen = true;
		haveTotal = true;
		reportedTotal += Math.max(0, count);
		settleCountdown = SETTLE_TICKS;
	}

	/** A "Chemtainer is full" line (no number): confirm a deposit happened, total unknown. */
	public static void noteFull() {
		if (reference == null) {
			return;
		}
		depositSeen = true;
		settleCountdown = SETTLE_TICKS;
	}

	/** Discard any in-flight capture — an authoritative /ch scrape supersedes the diff. */
	public static void cancel() {
		disarm();
	}

	/** Advance the capture each client tick: track inventory changes, flush when settled. */
	public static void tick() {
		if (reference == null) {
			return;
		}
		PlayerInventory inventory = inventory();
		if (inventory == null) {
			return;
		}
		track(ChemItems.snapshot(inventory));
		if (!depositSeen) {
			if (--confirmTimeout <= 0) {
				disarm(); // never confirmed — the removals weren't a deposit
			}
			return;
		}
		if (--settleCountdown > 0) {
			return;
		}
		flush();
	}

	/** Fold the latest inventory snapshot into reference/pending (drops = deposits). */
	private static void track(Map<ChemItems.ChemKey, Long> current) {
		Set<ChemItems.ChemKey> keys = new HashSet<>(reference.keySet());
		keys.addAll(current.keySet());
		for (ChemItems.ChemKey key : keys) {
			long ref = reference.getOrDefault(key, 0L);
			long cur = current.getOrDefault(key, 0L);
			if (cur < ref) {
				pending.merge(key, ref - cur, Long::sum);
				reference.put(key, cur);
			} else if (cur > ref) {
				reference.put(key, cur);
			}
		}
	}

	private static void flush() {
		Map<ChemItems.ChemKey, Long> deposited = reconcile();
		disarm();
		if (!deposited.isEmpty()) {
			ChemtainerTracker.applyDeposit(deposited);
		}
	}

	/**
	 * Reconcile observed drops against the server total. With a numbered total we
	 * scale the per-key drops to sum to exactly that total (clamping phantom churn
	 * and recovering same-tick-hidden amounts); a zero total means nothing was
	 * banked. Without a number (full-only) we trust the raw drops.
	 */
	private static Map<ChemItems.ChemKey, Long> reconcile() {
		Map<ChemItems.ChemKey, Long> drops = positive(pending);
		if (!haveTotal) {
			return drops;
		}
		if (reportedTotal <= 0) {
			return new HashMap<>(); // server banked nothing ("Deposited 0 chems")
		}
		long sum = total(drops);
		if (sum <= 0) {
			return new HashMap<>(); // confirmed deposit but no observed drop to attribute it to
		}
		if (sum == reportedTotal) {
			return drops;
		}
		return scaleTo(drops, reportedTotal, sum);
	}

	/** Scale counts to sum to {@code target} exactly, distributing rounding by largest remainder. */
	private static Map<ChemItems.ChemKey, Long> scaleTo(
			Map<ChemItems.ChemKey, Long> counts, long target, long sum) {
		Map<ChemItems.ChemKey, Long> result = new HashMap<>();
		List<Map.Entry<ChemItems.ChemKey, Double>> remainders = new ArrayList<>();
		long allocated = 0;
		for (Map.Entry<ChemItems.ChemKey, Long> entry : counts.entrySet()) {
			double exact = entry.getValue() * (double) target / sum;
			long floor = (long) Math.floor(exact);
			result.put(entry.getKey(), floor);
			allocated += floor;
			remainders.add(Map.entry(entry.getKey(), exact - floor));
		}
		remainders.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
		long remaining = target - allocated;
		for (int i = 0; i < remainders.size() && remaining > 0; i++, remaining--) {
			result.merge(remainders.get(i).getKey(), 1L, Long::sum);
		}
		result.values().removeIf(value -> value <= 0);
		return result;
	}

	private static Map<ChemItems.ChemKey, Long> positive(Map<ChemItems.ChemKey, Long> counts) {
		Map<ChemItems.ChemKey, Long> result = new HashMap<>();
		for (Map.Entry<ChemItems.ChemKey, Long> entry : counts.entrySet()) {
			if (entry.getValue() > 0) {
				result.put(entry.getKey(), entry.getValue());
			}
		}
		return result;
	}

	private static long total(Map<ChemItems.ChemKey, Long> counts) {
		long sum = 0;
		for (long value : counts.values()) {
			sum += value;
		}
		return sum;
	}

	private static void disarm() {
		reference = null;
		pending = null;
		reportedTotal = 0;
		haveTotal = false;
		depositSeen = false;
		settleCountdown = 0;
		confirmTimeout = 0;
	}

	private static PlayerInventory inventory() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player == null ? null : client.player.getInventory();
	}
}
