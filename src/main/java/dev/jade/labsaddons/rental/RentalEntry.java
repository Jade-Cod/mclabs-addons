package dev.jade.labsaddons.rental;

/**
 * One {@code /rent} rental as persisted in the config: the flat, Gson-friendly form.
 * Treated as a value — the tracker replaces an entry rather than editing it.
 */
public class RentalEntry {
	/** Item registry id, for the widget's icon; blank until the item itself has been seen. */
	public String itemId = "";
	/** The item's name as the server spells it, e.g. {@code "Portable Raft"}. */
	public String name = "";
	/** Who it is rented from. With the name, this is what chat and the menu match on. */
	public String owner = "";
	/** Epoch ms the rental ends. */
	public long endMs = 0L;

	public RentalEntry() {
	}

	public RentalEntry(String itemId, String name, String owner, long endMs) {
		this.itemId = itemId;
		this.name = name;
		this.owner = owner;
		this.endMs = endMs;
	}

	public String key() {
		return RentalTracker.key(name, owner);
	}
}
