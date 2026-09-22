package dev.jade.labsaddons.double2;

import java.util.Locale;

/**
 * The five labs of Double². Everything the overlay knows about one lives here: the
 * code the server prints, the glyph and colours it draws it in, the payout it
 * advertises, and the container slot you click to select it.
 *
 * <p>Colours are the server's own, read off a capture of the menu rather than
 * guessed from the concrete block each pane uses. {@code lift} is the lighter tint
 * used for a glyph sitting on top of {@code color}.
 */
public enum Lab {
	CQL("Crimson Quadri", "■", 2.0, 0xFF8E2121, 0xFFE4705C, 2),
	MSL("Magenta Star", "⭑", 4.0, 0xFFA9309F, 0xFFE176D8, 3),
	ADL("Amber Delta", "▲", 6.0, 0xFFF1AF15, 0xFFFFD064, 4),
	RDL("Royal Diamond", "◆", 11.0, 0xFF4169E1, 0xFF8AA6F5, 5),
	EOL("Emerald Orb", "●", 24.0, 0xFF5EA918, 0xFF96E03F, 6);

	private final String shortName;
	private final String glyph;
	private final double multiplier;
	private final int color;
	private final int lift;
	private final int pickSlot;

	Lab(String shortName, String glyph, double multiplier, int color, int lift, int pickSlot) {
		this.shortName = shortName;
		this.glyph = glyph;
		this.multiplier = multiplier;
		this.color = color;
		this.lift = lift;
		this.pickSlot = pickSlot;
	}

	public String shortName() {
		return shortName;
	}

	public String glyph() {
		return glyph;
	}

	public double multiplier() {
		return multiplier;
	}

	/** e.g. "2.0x" — the same string the server puts in the lore. */
	public String multiplierText() {
		return String.format(Locale.ROOT, "%.1fx", multiplier);
	}

	public int color() {
		return color;
	}

	public int lift() {
		return lift;
	}

	public int pickSlot() {
		return pickSlot;
	}

	/**
	 * The lab named somewhere in {@code text}, or null.
	 *
	 * <p>Handles every form the server uses: the menu's "■ Crimson Quadri Lab
	 * (2.0x Profit)", the settled banner's bare "ADL", and both casings chat prints —
	 * the loss line says "in EOL" while the win line says "in cql".
	 */
	public static Lab fromText(String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		String upper = text.toUpperCase(Locale.ROOT);
		for (Lab lab : values()) {
			if (upper.contains(lab.shortName.toUpperCase(Locale.ROOT))) {
				return lab;
			}
		}
		// No full name, so fall back to the three-letter code as a standalone word.
		for (Lab lab : values()) {
			int at = upper.indexOf(lab.name());
			while (at >= 0) {
				boolean beforeOk = at == 0 || !Character.isLetterOrDigit(upper.charAt(at - 1));
				int end = at + 3;
				boolean afterOk = end >= upper.length() || !Character.isLetterOrDigit(upper.charAt(end));
				if (beforeOk && afterOk) {
					return lab;
				}
				at = upper.indexOf(lab.name(), at + 1);
			}
		}
		return null;
	}

	/** The lab whose code is exactly {@code text} (the settled banner's name), or null. */
	public static Lab fromExactCode(String text) {
		if (text == null) {
			return null;
		}
		String trimmed = text.trim().toUpperCase(Locale.ROOT);
		for (Lab lab : values()) {
			if (lab.name().equals(trimmed)) {
				return lab;
			}
		}
		return null;
	}
}
