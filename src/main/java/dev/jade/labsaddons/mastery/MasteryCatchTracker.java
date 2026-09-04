package dev.jade.labsaddons.mastery;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Advances the {@code Catch <fish>} Mastery challenges live by watching the catch
 * land in the player's inventory, so the HUD moves on the reel-in instead of
 * waiting for the next {@code /mastery} scrape.
 *
 * <p>The inventory is the signal rather than the dropped item entity, because it is
 * the one place the catch shows up either way: whether the server spawns an item at
 * the bobber (vanilla behaviour) or hands it straight to the player, the stack ends
 * up in the inventory. This mirrors {@code ChemtainerDepositCapture}'s snapshot-and-
 * diff of {@link Inventory}.
 *
 * <p>Crediting is gated on actually fishing — a bobber in the water, plus a short
 * grace window so the catch still counts once the bobber is gone on reel-in. Without
 * that gate, buying cod from a shop would read as catching it. The baseline is
 * refreshed on <em>every</em> tick regardless, so items gained while not fishing move
 * the baseline silently instead of being credited on the next cast.
 *
 * <p>Names are compared with punctuation and spacing removed: the challenge is
 * {@code Catch Tropicalfish} while the item is "Tropical Fish", and both normalise to
 * {@code tropicalfish}. Matching is on equality of those normalised forms, not
 * {@code contains} — "Cooked Cod" must not credit {@code Catch Cod}.
 *
 * <p>Bumps are optimistic; {@link MasteryTracker#advance} ignores quests the player
 * has not selected, and the next GUI scrape reconciles against the server.
 */
public final class MasteryCatchTracker {
	private static final String CATCH_PREFIX = "catch ";
	/** Ticks after the bobber leaves the water during which a catch still counts. */
	static final int GRACE_TICKS = 40;

	// Null until the first tick, so an opening inventory is a baseline and never a haul.
	private static Map<String, Integer> lastCounts;
	private static int graceTicks;

	private MasteryCatchTracker() {
	}

	/** @return true if a catch advanced an active challenge, so the caller can persist the board. */
	public static boolean tick(LocalPlayer player) {
		Map<String, String> targets = activeCatchTargets();
		if (targets.isEmpty()) {
			reset();
			return false;
		}
		return observe(count(player.getInventory(), targets), player.fishing != null);
	}

	/**
	 * Records one tick's inventory totals. Minecraft-free seam so the gating, the
	 * baseline handling, and the delta crediting are unit-testable.
	 *
	 * @param counts  total held per challenge name, for active {@code Catch} challenges only.
	 * @param fishing whether the player's bobber is currently out.
	 * @return true if this call credited a catch.
	 */
	static boolean observe(Map<String, Integer> counts, boolean fishing) {
		if (fishing) {
			graceTicks = GRACE_TICKS;
		} else if (graceTicks > 0) {
			graceTicks--;
		}
		Map<String, Integer> previous = lastCounts;
		lastCounts = counts;
		if (previous == null || !(fishing || graceTicks > 0)) {
			return false;
		}
		boolean advanced = false;
		for (Map.Entry<String, Integer> held : counts.entrySet()) {
			int gained = held.getValue() - previous.getOrDefault(held.getKey(), 0);
			if (gained > 0) {
				advanced |= MasteryTracker.advance(held.getKey(), gained);
			}
		}
		return advanced;
	}

	/** Total held per challenge name, counting only items an active challenge asks for. */
	private static Map<String, Integer> count(Inventory inventory, Map<String, String> targets) {
		Map<String, Integer> totals = new HashMap<>();
		// Seed every target at zero: a stack that runs out must still be a known key,
		// otherwise its baseline vanishes and the next catch reads as a fresh gain.
		for (String challenge : targets.values()) {
			totals.put(challenge, 0);
		}
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			String challenge = targets.get(normalize(displayName(stack)));
			if (challenge != null) {
				totals.merge(challenge, stack.getCount(), Integer::sum);
			}
		}
		return totals;
	}

	/** The server's rename if there is one, else the item's own name. */
	private static String displayName(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name != null ? name.getString() : stack.getHoverName().getString();
	}

	/** Active {@code Catch X} challenges as normalised item name -> the exact challenge name. */
	static Map<String, String> activeCatchTargets() {
		Map<String, String> targets = new HashMap<>();
		for (MasteryQuest quest : MasteryTracker.quests()) {
			String name = quest.name();
			if (name.length() > CATCH_PREFIX.length()
					&& name.toLowerCase(Locale.ROOT).startsWith(CATCH_PREFIX)) {
				targets.put(normalize(name.substring(CATCH_PREFIX.length())), name);
			}
		}
		return targets;
	}

	/** Lowercase, stripped of everything but letters and digits: "Tropical Fish" -> "tropicalfish". */
	static String normalize(String name) {
		return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	/** Drop all per-session state (call on disconnect / when no Catch challenge is active). */
	public static void reset() {
		lastCounts = null;
		graceTicks = 0;
	}
}
