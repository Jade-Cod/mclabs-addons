package dev.jade.labsaddons.mcmmo;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The mcMMO super abilities that announce activation/cooldown in chat (one per
 * skill, names as printed by mcMMO's en_US locale). {@code tool} is the item
 * the ability is used with, for resolving the unnamed "You are too tired..."
 * line from whatever the player is holding.
 */
public enum McmmoAbility {
	SUPER_BREAKER("Super Breaker", Tool.PICKAXE),
	GIGA_DRILL_BREAKER("Giga Drill Breaker", Tool.SHOVEL),
	TREE_FELLER("Tree Feller", Tool.AXE),
	SKULL_SPLITTER("Skull Splitter", Tool.AXE),
	GREEN_TERRA("Green Terra", Tool.HOE),
	SERRATED_STRIKES("Serrated Strikes", Tool.SWORD),
	BERSERK("Berserk", Tool.FISTS),
	EXPLOSIVE_SHOT("Explosive Shot", Tool.BOW),
	SUPER_SHOTGUN("Super Shotgun", Tool.CROSSBOW),
	BLAST_MINING("Blast Mining", null);

	/** Tool kinds mcMMO abilities are bound to, matched from item registry ids. */
	public enum Tool {
		PICKAXE, SHOVEL, AXE, HOE, SWORD, FISTS, BOW, CROSSBOW;

		/** Tool kind for an item id like "minecraft:diamond_pickaxe", or null. */
		@Nullable
		public static Tool fromItemId(@Nullable String itemId) {
			if (itemId == null) {
				return null;
			}
			String id = itemId.toLowerCase(Locale.ROOT);
			if (id.endsWith("_pickaxe")) {
				return PICKAXE;
			}
			if (id.endsWith("_shovel")) {
				return SHOVEL;
			}
			if (id.endsWith("_axe")) {
				return AXE;
			}
			if (id.endsWith("_hoe")) {
				return HOE;
			}
			if (id.endsWith("_sword")) {
				return SWORD;
			}
			if (id.endsWith(":bow")) {
				return BOW;
			}
			if (id.endsWith("crossbow")) {
				return CROSSBOW;
			}
			return null;
		}
	}

	private final String displayName;
	@Nullable
	private final Tool tool;

	McmmoAbility(String displayName, @Nullable Tool tool) {
		this.displayName = displayName;
		this.tool = tool;
	}

	public String displayName() {
		return displayName;
	}

	/** Ability whose display name matches (case-insensitive), or null. */
	@Nullable
	public static McmmoAbility fromName(@Nullable String name) {
		if (name == null) {
			return null;
		}
		String wanted = name.trim().toLowerCase(Locale.ROOT);
		for (McmmoAbility ability : values()) {
			if (ability.displayName.toLowerCase(Locale.ROOT).equals(wanted)) {
				return ability;
			}
		}
		return null;
	}

	/** All abilities bound to the given tool (an axe maps to two skills). */
	public static List<McmmoAbility> forTool(@Nullable Tool tool) {
		if (tool == null) {
			return List.of();
		}
		return java.util.Arrays.stream(values())
				.filter(ability -> ability.tool == tool)
				.toList();
	}
}
