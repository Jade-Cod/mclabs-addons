package dev.jade.labsaddons.mcmmo;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumMap;
import java.util.Map;

/**
 * Icon stacks for mcMMO abilities, analogous to {@link dev.jade.labsaddons.chem.ChemIcons}:
 * the vanilla tool each ability is used with (iron axe for Skull Splitter so it
 * reads differently from Tree Feller's diamond axe). Stacks are cached — no
 * per-frame allocation.
 */
public final class McmmoSkillIcons {
	private static final Map<McmmoAbility, ItemStack> CACHE = new EnumMap<>(McmmoAbility.class);

	private McmmoSkillIcons() {
	}

	/** Icon for a tracker entry key ("mcmmo:super_breaker"), or null if unknown. */
	public static ItemStack iconFor(String key) {
		McmmoAbility ability = abilityForKey(key);
		return ability == null ? null : CACHE.computeIfAbsent(ability, a -> new ItemStack(itemFor(a)));
	}

	private static McmmoAbility abilityForKey(String key) {
		for (McmmoAbility ability : McmmoAbility.values()) {
			if (McmmoCooldownTracker.keyFor(ability).equals(key)) {
				return ability;
			}
		}
		return null;
	}

	private static Item itemFor(McmmoAbility ability) {
		return switch (ability) {
			case SUPER_BREAKER -> Items.DIAMOND_PICKAXE;
			case GIGA_DRILL_BREAKER -> Items.DIAMOND_SHOVEL;
			case TREE_FELLER -> Items.DIAMOND_AXE;
			case SKULL_SPLITTER -> Items.IRON_AXE;
			case GREEN_TERRA -> Items.DIAMOND_HOE;
			case SERRATED_STRIKES -> Items.DIAMOND_SWORD;
			case BERSERK -> Items.LEATHER;
			case EXPLOSIVE_SHOT -> Items.BOW;
			case SUPER_SHOTGUN -> Items.CROSSBOW;
			case BLAST_MINING -> Items.TNT;
		};
	}
}
