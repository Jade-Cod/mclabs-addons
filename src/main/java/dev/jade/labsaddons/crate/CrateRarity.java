package dev.jade.labsaddons.crate;

import java.util.Locale;

/**
 * The six rarities every MCLabs crate is built out of, and the two ways the server says
 * which one it means.
 *
 * <p>The reward menu says it in words — {@code "┃ Rarity: Very Rare"} — but the spin does
 * not. Mid-spin the only signal is the colour of the stained glass pane above and below
 * each column, so that mapping is the whole reason this class exists. It was read off ten
 * captured spins rather than guessed: nine of the pairings turned up in ordinary rolls and
 * the last one, Super Rare, only in a Mystery Crate, which draws its candidates from other
 * crates' pools and so happened to show a Supply Crate I reward this player had never won.
 *
 * <p>Declared commonest-first so {@link #compareTo} ranks them, which is what picks the
 * best rarity still alive.
 */
public enum CrateRarity {
	COMMON("Common", "light_blue_stained_glass_pane", 0xFF55FFFF),
	UNCOMMON("Uncommon", "purple_stained_glass_pane", 0xFFAA00AA),
	RARE("Rare", "magenta_stained_glass_pane", 0xFFFF55FF),
	VERY_RARE("Very Rare", "red_stained_glass_pane", 0xFFFF5555),
	SUPER_RARE("Super Rare", "yellow_stained_glass_pane", 0xFFFFFF55),
	/** Orange, not gold: the pane is orange where the item's own name is gold. */
	EXCEEDINGLY_RARE("Exceedingly Rare", "orange_stained_glass_pane", 0xFFFFAA00);

	/** What the reward menu's lore calls it. */
	private final String label;
	/** The registry path of the pane the spin frames this rarity's column with. */
	private final String paneId;
	/** The chat colour the server gives this rarity's item name, which the board reuses. */
	private final int color;

	CrateRarity(String label, String paneId, int color) {
		this.label = label;
		this.paneId = paneId;
		this.color = color;
	}

	public String label() {
		return label;
	}

	public int color() {
		return color;
	}

	/** Whether a rarity is Rare or above, which is the set the server's pity applies to. */
	public boolean isPitied() {
		return compareTo(RARE) >= 0;
	}

	/**
	 * The rarity a spin column's pane stands for, or null for the grey pane a column shows
	 * before it is filled and again once it has been culled.
	 *
	 * @param itemPath a registry path, so this never depends on the client's language
	 */
	public static CrateRarity fromPane(String itemPath) {
		if (itemPath == null) {
			return null;
		}
		for (CrateRarity rarity : values()) {
			if (rarity.paneId.equals(itemPath)) {
				return rarity;
			}
		}
		return null;
	}

	/**
	 * The rarity spelled out exactly, as the odds menu's title spells it, or null.
	 *
	 * <p>Exact where {@link #fromLore} is lenient, because the caller has already stripped the
	 * words around it and a near miss here would anchor the wrong ladder.
	 */
	public static CrateRarity fromLabel(String label) {
		if (label == null) {
			return null;
		}
		String trimmed = label.trim();
		for (CrateRarity rarity : values()) {
			if (rarity.label.equalsIgnoreCase(trimmed)) {
				return rarity;
			}
		}
		return null;
	}

	/**
	 * The rarity named by a reward's lore line, or null. Matched longest-label-first because
	 * "Rare" is a suffix of three of the others and would otherwise swallow them.
	 */
	public static CrateRarity fromLore(String line) {
		if (line == null) {
			return null;
		}
		int marker = line.toLowerCase(Locale.ROOT).indexOf("rarity:");
		if (marker < 0) {
			return null;
		}
		String tail = line.substring(marker + "rarity:".length()).toLowerCase(Locale.ROOT).trim();
		CrateRarity best = null;
		for (CrateRarity rarity : values()) {
			String candidate = rarity.label.toLowerCase(Locale.ROOT);
			if (tail.startsWith(candidate)
					&& (best == null || candidate.length() > best.label.length())) {
				best = rarity;
			}
		}
		return best;
	}
}
