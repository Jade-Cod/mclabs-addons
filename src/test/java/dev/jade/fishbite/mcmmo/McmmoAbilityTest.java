package dev.jade.fishbite.mcmmo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class McmmoAbilityTest {

	@Test
	public void fromNameMatchesCaseInsensitively() {
		assertEquals(McmmoAbility.SUPER_BREAKER, McmmoAbility.fromName("Super Breaker"));
		assertEquals(McmmoAbility.SUPER_BREAKER, McmmoAbility.fromName("SUPER BREAKER"));
		assertEquals(McmmoAbility.GIGA_DRILL_BREAKER, McmmoAbility.fromName("Giga Drill Breaker"));
		assertEquals(McmmoAbility.TREE_FELLER, McmmoAbility.fromName("tree feller"));
		assertNull(McmmoAbility.fromName("Totally Unknown"));
		assertNull(McmmoAbility.fromName(null));
	}

	@Test
	public void toolResolvesFromItemId() {
		assertEquals(McmmoAbility.Tool.PICKAXE, McmmoAbility.Tool.fromItemId("minecraft:diamond_pickaxe"));
		assertEquals(McmmoAbility.Tool.SHOVEL, McmmoAbility.Tool.fromItemId("minecraft:netherite_shovel"));
		assertEquals(McmmoAbility.Tool.AXE, McmmoAbility.Tool.fromItemId("minecraft:iron_axe"));
		assertEquals(McmmoAbility.Tool.HOE, McmmoAbility.Tool.fromItemId("minecraft:golden_hoe"));
		assertEquals(McmmoAbility.Tool.SWORD, McmmoAbility.Tool.fromItemId("minecraft:stone_sword"));
		assertEquals(McmmoAbility.Tool.BOW, McmmoAbility.Tool.fromItemId("minecraft:bow"));
		assertEquals(McmmoAbility.Tool.CROSSBOW, McmmoAbility.Tool.fromItemId("minecraft:crossbow"));
		assertNull(McmmoAbility.Tool.fromItemId("minecraft:stick"));
		assertNull(McmmoAbility.Tool.fromItemId(null));
	}

	@Test
	public void pickaxeResolvesUniquelyButAxeIsAmbiguous() {
		assertEquals(java.util.List.of(McmmoAbility.SUPER_BREAKER),
				McmmoAbility.forTool(McmmoAbility.Tool.PICKAXE));
		// Tree Feller (Woodcutting) and Skull Splitter (Axes) both use an axe.
		assertEquals(2, McmmoAbility.forTool(McmmoAbility.Tool.AXE).size());
		assertEquals(java.util.List.of(McmmoAbility.BERSERK),
				McmmoAbility.forTool(McmmoAbility.Tool.FISTS));
		assertTrue(McmmoAbility.forTool(null).isEmpty());
	}
}
