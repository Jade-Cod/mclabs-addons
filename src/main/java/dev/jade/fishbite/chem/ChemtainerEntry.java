package dev.jade.fishbite.chem;

/**
 * One chem type stored in the Chemtainer, as scraped from the {@code /ch} GUI.
 * Persisted in the config so the last-known contents survive relogs.
 */
public class ChemtainerEntry {
	/** Lowercase chem key (e.g. {@code "chowartusite"}), used to pick the icon. */
	public String chem = "";
	/** Purity string "s-v-p" (e.g. {@code "2-2-2"}), or "" when the chem has none. */
	public String purity = "";
	/** Display label including purity, e.g. {@code "Chowartusite-2-2-2"}. */
	public String label = "";
	public long count = 0L;

	public ChemtainerEntry() {
	}

	public ChemtainerEntry(String chem, String purity, String label, long count) {
		this.chem = chem;
		this.purity = purity;
		this.label = label;
		this.count = count;
	}
}
