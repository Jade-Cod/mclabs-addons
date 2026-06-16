package dev.jade.fishbite.hud.editor;

/**
 * Visual language for the HUD Studio editor: a dark, translucent, aqua-accented
 * theme. All colours are full-alpha-safe ARGB; spacing is in GUI-scaled pixels.
 * Centralising these keeps the screen/painter free of magic numbers.
 */
public final class EditorTheme {
	private EditorTheme() {
	}

	// --- accent ---------------------------------------------------------------
	public static final int ACCENT = 0xFF4FE3E3;
	/** RGB-only accent, for compositing a pulsing alpha at draw time. */
	public static final int ACCENT_RGB = 0x004FE3E3;

	// --- backdrop -------------------------------------------------------------
	/** Light dim so the live game stays visible for truthful placement. */
	public static final int DIM = 0x55000000;
	public static final int GRID = 0x14FFFFFF;
	public static final int GUIDE = 0xCC4FE3E3;

	// --- selection ------------------------------------------------------------
	public static final int HOVER_OUTLINE = 0x90FFFFFF;
	public static final int HANDLE = 0xFF4FE3E3;

	// --- text -----------------------------------------------------------------
	public static final int TITLE = 0xFFFFFFFF;
	public static final int TEXT = 0xFFEAEEF3;
	public static final int TEXT_DIM = 0xFF9AA3AD;
	public static final int TEXT_ACCENT = 0xFF4FE3E3;
	public static final int TEXT_HIDDEN = 0xFFFF8080;

	// --- panels ---------------------------------------------------------------
	public static final int PANEL_BG = 0xF21A1D24;
	public static final int PANEL_BORDER = 0xFF3A4150;
	public static final int TOOLBAR_BG = 0xE015171D;
	public static final int CHIP_BG = 0xCC0A0C10;

	// --- rail rows ------------------------------------------------------------
	public static final int ROW_HOVER = 0x22FFFFFF;
	public static final int ROW_SELECTED = 0x334FE3E3;
	public static final int TOGGLE_ON = 0xFF4FE3E3;
	public static final int TOGGLE_OFF = 0x66FFFFFF;

	// --- swatch checkerboard --------------------------------------------------
	public static final int CHECK_A = 0xFF9A9A9A;
	public static final int CHECK_B = 0xFF666666;

	// --- spacing (GUI-scaled px) ---------------------------------------------
	public static final int MARGIN = 6;
	public static final int TOP = 38;
	public static final int PAD = 8;
	public static final int GAP = 4;
	public static final int ROW = 18;
	public static final int NAME_H = 13;
	public static final int SWATCH = 18;
	public static final int RAIL_W = 108;
	public static final int RAIL_HEADER_H = 14;
	public static final int RAIL_ROW_H = 15;
	public static final int RAIL_TOGGLE = 8;
	public static final int PANEL_W = 158;
	public static final int GRID_STEP = 16;
	public static final int HANDLE_SZ = 5;
	public static final int TOOLBAR_H = 30;

	/** Aqua accent with a gentle breathing alpha (0xB4–0xFF), for the selection frame. */
	public static int accentPulse() {
		double phase = (System.currentTimeMillis() % 1600L) / 1600.0 * Math.PI * 2.0;
		int alpha = 0xB4 + (int) Math.round((Math.sin(phase) * 0.5 + 0.5) * (0xFF - 0xB4));
		return (alpha << 24) | ACCENT_RGB;
	}
}
