package dev.jade.labsaddons.crate;

import java.util.Locale;

/**
 * The titles the crate menus go by, in one place and free of Minecraft so they can be tested
 * against the real strings.
 *
 * <p>All four are load-bearing. A crate screen is a chest full of stained glass panes, a shape
 * far too ordinary for slot names to claim — as {@code CfFlipBoard} found the hard way, a
 * board that latches onto a menu it then fails to draw costs that menu its chest texture and
 * its tooltips. The title is the only unambiguous signal, so these are the gate.
 */
public final class CrateTitles {
	private CrateTitles() {
	}

	/**
	 * A supply-crate spin: "Opening a Supply Crate II...", and the same shape on all ten
	 * crates captured — Favourites, Mystery, Spawner, Summer, Supply I to V and Tool.
	 */
	public static boolean isSpin(String title) {
		if (title == null) {
			return false;
		}
		String lower = title.toLowerCase(Locale.ROOT).trim();
		return lower.startsWith("opening ") && lower.contains("crate") && lower.endsWith("...");
	}

	/** The voter crate's three reels, before the choice. */
	public static boolean isVoteRoll(String title) {
		return title != null && title.toLowerCase(Locale.ROOT).contains("rolling reward");
	}

	/** The voter crate's three-way choice, which arrives as a fresh container. */
	public static boolean isVoteChoice(String title) {
		return title != null && title.toLowerCase(Locale.ROOT).contains("choose a reward");
	}

	/**
	 * The crate's own name out of a spin title: "Opening a Supply Crate II..." to "Supply
	 * Crate II".
	 *
	 * <p>Note this is the crate whose <em>animation</em> is playing, which is not always the
	 * crate that pays out: a Mystery Crate rolls a rarity and then hands over an item from some
	 * other crate entirely, and its chat line says so. Never treat this as the source of a
	 * result.
	 */
	public static String crateName(String title) {
		if (title == null) {
			return "";
		}
		String name = title.trim();
		if (name.endsWith("...")) {
			name = name.substring(0, name.length() - 3).trim();
		}
		String lower = name.toLowerCase(Locale.ROOT);
		for (String prefix : new String[] {"opening an ", "opening a ", "opening "}) {
			if (lower.startsWith(prefix)) {
				return name.substring(prefix.length()).trim();
			}
		}
		return name;
	}
}
