package dev.jade.labsaddons.pititem;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The Pit's MythicMobs-backed items with an on-use cooldown ability. Unlike
 * {@link dev.jade.labsaddons.mcmmo.McmmoAbility}, no held-item resolution is
 * needed: every trigger message (chat or actionbar) is unique per item, so
 * text matching alone identifies which one fired.
 */
public enum PitItem {
	STORMBREAKER("Stormbreaker", 45, "sky darkens as Stormbreaker summons thunder"),
	HEAVY_STEEL_CHESTPLATE("Heavy Steel Chestplate", 30, "You summon a friendly Possessed Armour"),
	BODY_SLAM("Body Slam", 15, null),
	SCYTHE_SWEEP("Scythe Sweep", 30, null),
	BLINK_BOOTS("Blink Boots", 10, "You blink forward"),
	EXCALIBUR("Excalibur", 30, "Excalibur channels divine power");

	private final String displayName;
	private final int cooldownSeconds;
	@Nullable
	private final String chatFragment;

	PitItem(String displayName, int cooldownSeconds, @Nullable String chatFragment) {
		this.displayName = displayName;
		this.cooldownSeconds = cooldownSeconds;
		this.chatFragment = chatFragment;
	}

	public String displayName() {
		return displayName;
	}

	public int cooldownSeconds() {
		return cooldownSeconds;
	}

	/** Chat substring that announces this item's activation, or null if it has none. */
	@Nullable
	public String chatFragment() {
		return chatFragment;
	}

	/** Item whose display name matches an actionbar's "<name> [Ns]" text (case-insensitive), or null. */
	@Nullable
	public static PitItem fromActionbarName(@Nullable String name) {
		if (name == null) {
			return null;
		}
		String wanted = name.trim().toLowerCase(Locale.ROOT);
		for (PitItem item : values()) {
			if (item.displayName.toLowerCase(Locale.ROOT).equals(wanted)) {
				return item;
			}
		}
		return null;
	}
}
