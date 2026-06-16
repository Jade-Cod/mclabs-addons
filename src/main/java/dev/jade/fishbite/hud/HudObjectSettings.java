package dev.jade.fishbite.hud;

/**
 * Per-HUD-object display settings, stored in the config under the object's id.
 * Positions are screen fractions so they survive resolution changes.
 */
public class HudObjectSettings {
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 3.0f;

	public boolean enabled = true;
	public float x = 0.02f;
	public float y = 0.40f;
	public float scale = 1.0f;
	/** Content text colour (alpha forced opaque when drawing). */
	public int textColor = 0xFFFFFFFF;
	public boolean backgroundEnabled = true;
	/** Background colour, ARGB — alpha is the transparency control. */
	public int backgroundColor = 0x80000000;

	public HudObjectSettings copy() {
		HudObjectSettings c = new HudObjectSettings();
		c.enabled = enabled;
		c.x = x;
		c.y = y;
		c.scale = scale;
		c.textColor = textColor;
		c.backgroundEnabled = backgroundEnabled;
		c.backgroundColor = backgroundColor;
		return c;
	}

	/** Copies every display field from {@code src} into this instance (used by editor resets). */
	public void resetTo(HudObjectSettings src) {
		enabled = src.enabled;
		x = src.x;
		y = src.y;
		scale = src.scale;
		textColor = src.textColor;
		backgroundEnabled = src.backgroundEnabled;
		backgroundColor = src.backgroundColor;
	}

	/** Clamps all values into valid ranges (used on config load). */
	public void sanitize() {
		x = Math.clamp(x, 0.0f, 1.0f);
		y = Math.clamp(y, 0.0f, 1.0f);
		scale = Math.clamp(scale, MIN_SCALE, MAX_SCALE);
		textColor |= 0xFF000000;
	}
}
