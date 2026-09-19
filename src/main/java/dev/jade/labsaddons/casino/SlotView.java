package dev.jade.labsaddons.casino;

import java.util.List;
import java.util.Locale;

/**
 * One container slot, flattened to the two things the casino readers care about: the
 * item's name and its lore.
 *
 * <p>Deliberately Minecraft-free, so every reader that consumes it is unit-testable
 * against the exact strings the server sent — the same seam
 * {@code SunkenTreasureReader.cratesLeft} uses. No item id: all three games identify
 * everything by name ("Safe", "Mine", "???", "Energize"), so carrying the id would be
 * one more thing to keep in step for nothing.
 */
public record SlotView(int index, String name, List<String> lore, int count) {
	/** An empty slot, for tests and for padding a short container. */
	public static SlotView empty(int index) {
		return new SlotView(index, "", List.of(), 0);
	}

	public boolean isEmpty() {
		return name == null || name.isEmpty();
	}

	public String loreLine(int i) {
		return lore != null && i < lore.size() ? lore.get(i) : "";
	}

	/** Case-insensitive name test, which is how every phase marker is read. */
	public boolean named(String needle) {
		return name != null
				&& name.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
	}

	public boolean loreContains(String needle) {
		if (lore == null) {
			return false;
		}
		String lower = needle.toLowerCase(Locale.ROOT);
		for (String line : lore) {
			if (line != null && line.toLowerCase(Locale.ROOT).contains(lower)) {
				return true;
			}
		}
		return false;
	}

	/** The slot at {@code index}, or null when the container is shorter than that. */
	public static SlotView at(List<SlotView> slots, int index) {
		return slots != null && index >= 0 && index < slots.size() ? slots.get(index) : null;
	}

	/** That slot's name, or "" — saves a null check at every call site. */
	public static String nameAt(List<SlotView> slots, int index) {
		SlotView slot = at(slots, index);
		return slot == null || slot.name() == null ? "" : slot.name();
	}
}
