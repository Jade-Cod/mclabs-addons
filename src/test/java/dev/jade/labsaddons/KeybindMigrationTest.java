package dev.jade.labsaddons;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KeybindMigrationTest {
	@Test
	public void extractsLegacyBindingsIgnoringOtherLines() {
		Map<String, String> found = KeybindMigration.parseLegacyBindings(List.of(
				"key_key.attack:key.mouse.left",
				"key_key.fishbite.chum_editor:key.keyboard.k",
				"key_key.fishbite.chem_deposit:key.keyboard.o",
				"key_key.fishbite.chem_withdraw:key.keyboard.p",
				"key_key.labsaddons.chum_editor:key.keyboard.semicolon",
				"fov:0.5"));

		assertEquals(Map.of(
				"chum_editor", "key.keyboard.k",
				"chem_deposit", "key.keyboard.o",
				"chem_withdraw", "key.keyboard.p"), found);
	}

	@Test
	public void emptyWhenNoLegacyLines() {
		assertTrue(KeybindMigration.parseLegacyBindings(List.of(
				"key_key.attack:key.mouse.left",
				"key_key.labsaddons.chum_editor:key.keyboard.semicolon")).isEmpty());
	}

	@Test
	public void skipsMalformedLegacyLines() {
		assertTrue(KeybindMigration.parseLegacyBindings(List.of(
				"key_key.fishbite.",
				"key_key.fishbite.:key.keyboard.k",
				"key_key.fishbite.chum_editor")).isEmpty());
	}
}
