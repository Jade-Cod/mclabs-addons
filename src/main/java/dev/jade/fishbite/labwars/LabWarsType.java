package dev.jade.fishbite.labwars;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.Locale;

/** Maps Lab Wars category names (chat + /lw rates GUI) to a key, display name, and icon. */
public final class LabWarsType {
	private LabWarsType() {
	}

	/** @return canonical key, or null if the name isn't a known booster category. */
	public static String keyOf(String rawName) {
		String l = rawName.toLowerCase(Locale.ROOT);
		if (l.contains("cop") || l.contains("arrest")) {
			return "arrests";
		}
		if (l.contains("chem") && (l.contains("sell") || l.contains("sale"))) {
			return "chem_sales";
		}
		if (l.contains("fish")) {
			return "fishing";
		}
		if (l.contains("pit")) {
			return "pit";
		}
		if (l.contains("vot")) {
			return "voting";
		}
		if (l.contains("event")) {
			return "events";
		}
		return null;
	}

	public static String display(String key) {
		return switch (key) {
			case "chem_sales" -> "Chem Sales";
			case "arrests" -> "Arrests";
			case "fishing" -> "Fishing";
			case "pit" -> "The Pit";
			case "voting" -> "Voting";
			case "events" -> "Events";
			default -> key;
		};
	}

	public static Item icon(String key) {
		return switch (key) {
			case "chem_sales" -> Items.WHEAT;
			case "arrests" -> Items.STICK;
			case "fishing" -> Items.FISHING_ROD;
			case "pit" -> Items.NETHERITE_SWORD;
			case "voting" -> Items.SUNFLOWER;
			case "events" -> Items.EMERALD;
			default -> Items.PAPER;
		};
	}
}
