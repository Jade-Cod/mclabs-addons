package dev.jade.labsaddons;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One-time carry-over of keybind rebinds from the mod's pre-rename id, in two
 * phases because neither moment alone works (verified against 1.21.11): the
 * game loads options.txt before client entrypoints run, so renaming the old
 * "key_key.fishbite.*" lines on disk at init is ignored in-memory and then
 * clobbered by the boot-time options save — and that same save drops the
 * unknown legacy lines before CLIENT_STARTED, so reading the file that late
 * finds nothing. Therefore {@link #capture()} stashes the legacy values at
 * init while they still exist on disk, and {@link #apply} binds them once the
 * client is up, then persists. Best-effort throughout: a missing file,
 * missing lines, or unparsable key names silently keep the defaults.
 */
final class KeybindMigration {
	private static final Logger LOGGER = LoggerFactory.getLogger("labsaddons");
	private static final String LEGACY_PREFIX = "key_key.fishbite.";

	/** Legacy suffix ("chum_editor") → bound key name; captured once at init. */
	private static Map<String, String> pending = Map.of();

	private KeybindMigration() {
	}

	/** Init phase: stash the legacy rebinds before Minecraft's first options save. */
	static void capture() {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		if (config.keybindsMigrated) {
			return;
		}
		// Flag first: a crash below can't re-run the migration every launch.
		config.keybindsMigrated = true;
		config.saveAsync();
		try {
			Path optionsPath = FabricLoader.getInstance().getGameDir().resolve("options.txt");
			if (Files.exists(optionsPath)) {
				pending = parseLegacyBindings(Files.readAllLines(optionsPath));
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("[labsaddons] Keybind migration skipped.", e);
		}
	}

	/** Extracts {@code key_key.fishbite.<suffix>:<key name>} lines as suffix → key name. */
	static Map<String, String> parseLegacyBindings(List<String> lines) {
		Map<String, String> found = new HashMap<>();
		for (String line : lines) {
			int colon = line.indexOf(':');
			if (line.startsWith(LEGACY_PREFIX) && colon > LEGACY_PREFIX.length()) {
				found.put(line.substring(LEGACY_PREFIX.length(), colon),
						line.substring(colon + 1).trim());
			}
		}
		return Map.copyOf(found);
	}

	/** CLIENT_STARTED phase: bind the captured keys and persist them. */
	static void apply(Minecraft client, KeyMapping chumEditor,
			KeyMapping chemDeposit, KeyMapping chemWithdraw) {
		if (pending.isEmpty()) {
			return;
		}
		Map<String, KeyMapping> bindings = Map.of(
				"chum_editor", chumEditor,
				"chem_deposit", chemDeposit,
				"chem_withdraw", chemWithdraw);
		boolean changed = false;
		for (Map.Entry<String, String> entry : pending.entrySet()) {
			KeyMapping binding = bindings.get(entry.getKey());
			if (binding == null) {
				continue;
			}
			try {
				binding.setKey(InputConstants.getKey(entry.getValue()));
				changed = true;
			} catch (IllegalArgumentException ignored) {
				// Unparsable key name — keep this binding's default.
			}
		}
		pending = Map.of();
		if (changed) {
			KeyMapping.resetMapping();
			client.options.save();
			LOGGER.info("[labsaddons] Carried over pre-rename keybind rebinds from options.txt.");
		}
	}
}
