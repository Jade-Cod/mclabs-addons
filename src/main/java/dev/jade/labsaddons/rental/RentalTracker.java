package dev.jade.labsaddons.rental;

import dev.jade.labsaddons.config.LabsAddonsConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code /rent} items you are holding and when each has to go back.
 *
 * <p>Three sources, in order of authority: the {@code /rent return} menu (every rental you
 * hold, with its expiry — replaces the lot), the extend and return chat lines, and the rented
 * item's own lore. The lore only ever pushes an end time later, because nothing says the
 * server rewrites the item when you extend, and a stale window must not undo an extension.
 *
 * <p>Death and a broken item send lines we have not captured yet, so those rentals stay until
 * the menu is opened or the widget is cleared by hand.
 */
public final class RentalTracker {
	/**
	 * The item can still be in the inventory for a scan or two after the return line lands;
	 * without this it would be read straight back in as a fresh rental.
	 */
	private static final long RETURN_GRACE_MS = 5_000L;

	private static final Map<String, Long> returnedAtMs = new HashMap<>();

	private RentalTracker() {
	}

	// ponytail: name + owner is the identity; two of the same item from the same owner at once
	// would merge into one row. The item's rental_id would split them, but chat and the menu
	// never state it.
	static String key(String name, String owner) {
		return name + "\n" + owner;
	}

	/** Every tracked rental, soonest to end first. A copy; callers only read it. */
	public static List<RentalEntry> entries() {
		List<RentalEntry> sorted = new ArrayList<>(LabsAddonsConfig.get().rentals);
		sorted.sort(Comparator.comparingLong(e -> e.endMs));
		return sorted;
	}

	public static boolean any() {
		return !LabsAddonsConfig.get().rentals.isEmpty();
	}

	public static synchronized void onMessage(String text, long nowMs) {
		RentalLore.Returned returned = RentalLore.returned(text);
		if (returned != null) {
			String key = key(returned.name(), returned.owner());
			returnedAtMs.put(key, nowMs);
			save(without(key));
			return;
		}
		RentalLore.Extension extension = RentalLore.extended(text);
		if (extension != null) {
			String key = key(extension.name(), extension.owner());
			long endMs = nowMs + extension.remainingMs();
			save(LabsAddonsConfig.get().rentals.stream()
					.map(e -> e.key().equals(key) ? new RentalEntry(e.itemId, e.name, e.owner, endMs) : e)
					.toList());
		}
	}

	/** A rented item seen in the inventory. Adds it, or moves its end later; never earlier. */
	public static synchronized void onHeld(String itemId, RentalLore.Rental rental, long nowMs) {
		String key = key(rental.name(), rental.owner());
		Long returnedAt = returnedAtMs.get(key);
		if (returnedAt != null && nowMs - returnedAt < RETURN_GRACE_MS) {
			return;
		}
		List<RentalEntry> current = LabsAddonsConfig.get().rentals;
		RentalEntry known = current.stream().filter(e -> e.key().equals(key)).findFirst().orElse(null);
		if (known != null && known.endMs >= rental.endMs() && known.itemId.equals(itemId)) {
			return;
		}
		long endMs = known == null ? rental.endMs() : Math.max(known.endMs, rental.endMs());
		List<RentalEntry> next = new ArrayList<>(without(key));
		next.add(new RentalEntry(itemId, rental.name(), rental.owner(), endMs));
		save(next);
	}

	/**
	 * The {@code /rent return} menu: exactly the rentals you hold. Keeps each one's icon
	 * if the item has been seen, and drops anything not listed.
	 */
	public static synchronized void reconcile(List<RentalEntry> listed) {
		Map<String, String> icons = new HashMap<>();
		LabsAddonsConfig.get().rentals.forEach(e -> icons.put(e.key(), e.itemId));
		save(listed.stream()
				.map(e -> e.itemId.isBlank()
						? new RentalEntry(icons.getOrDefault(e.key(), ""), e.name, e.owner, e.endMs) : e)
				.toList());
	}

	public static synchronized void clear() {
		returnedAtMs.clear();
		save(List.of());
	}

	private static List<RentalEntry> without(String key) {
		return LabsAddonsConfig.get().rentals.stream().filter(e -> !e.key().equals(key)).toList();
	}

	private static void save(List<RentalEntry> next) {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.rentals = new ArrayList<>(next);
		config.save();
	}
}
