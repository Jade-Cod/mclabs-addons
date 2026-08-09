package dev.jade.labsaddons.prestige;

/**
 * One chem's prestige track as persisted in the config. The live form is
 * {@link PrestigeChem}; this is the flat, Gson-friendly mirror.
 *
 * <p>No icon field: prestige chems are all base chems, so {@code ChemIcons} resolves the
 * icon from the name at render time and there is nothing item-shaped to round-trip.
 */
public class PrestigeChemEntry {
	/** Chem name as the server spells it, e.g. {@code "Wheatium"}. */
	public String chem = "";
	public double current = 0;
	public double target = 0;

	public PrestigeChemEntry() {
	}

	public PrestigeChemEntry(String chem, double current, double target) {
		this.chem = chem;
		this.current = current;
		this.target = target;
	}
}
