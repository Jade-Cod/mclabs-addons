package dev.jade.labsaddons.raidmine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Names for the single-character resource codes the Raid Mine holograms use, as
 * spelled by the Nexus Lab Resources board. The server writes the code, not the
 * name, so this is the only place the two are tied together.
 *
 * <p>Each resource comes in two tiers sharing a letter and told apart by case and
 * colour: the lighter lowercase form is <b>Flux</b>, the bold capital is
 * <b>Essence</b>.
 *
 * <p>An unknown code falls back to the character itself, so a resource the server
 * adds later still tracks and displays — just under its glyph until named here.
 */
public final class RaidMineResources {
	private static final Map<String, String> NAMES = new LinkedHashMap<>();

	static {
		NAMES.put("ℯ", "Energy");
		NAMES.put("𝕧", "Value Flux");
		NAMES.put("𝕍", "Value Essence");
		NAMES.put("𝕡", "Progress Flux");
		NAMES.put("ℙ", "Progress Essence");
		NAMES.put("𝕤", "Score Flux");
		NAMES.put("𝕊", "Score Essence");
		NAMES.put("🅕", "Siege Fuel");
		NAMES.put("💰", "Company Gold");
		NAMES.put("®", "Raid Points");
	}

	private RaidMineResources() {
	}

	/** The resource's name, or the code itself when we don't have one for it. */
	public static String name(String code) {
		return NAMES.getOrDefault(code, code);
	}

	/** Whether this code is one we can name (unknown ones still track, just unnamed). */
	public static boolean isKnown(String code) {
		return NAMES.containsKey(code);
	}
}
