package dev.jade.labsaddons.mastery;

/**
 * One active Mastery challenge as persisted in the config, so the board survives a
 * client restart. The live form is {@link MasteryQuest}; this is the flat,
 * Gson-friendly mirror ({@code ItemStack} cannot be serialised directly).
 *
 * <p>The icon is stored as a plain item id. Every quest icon observed on MCLabs is
 * a vanilla item with no custom model data, so the id round-trips losslessly.
 */
public class MasteryQuestEntry {
	/** Item id of the GUI icon, e.g. {@code "minecraft:red_wool"}. */
	public String icon = "";
	/** Quest name exactly as the GUI spells it — chat tracking matches on it. */
	public String name = "";
	public double current = 0;
	public double target = 0;
	public int percent = 0;

	public MasteryQuestEntry() {
	}

	public MasteryQuestEntry(String icon, String name, double current, double target, int percent) {
		this.icon = icon;
		this.name = name;
		this.current = current;
		this.target = target;
		this.percent = percent;
	}
}
