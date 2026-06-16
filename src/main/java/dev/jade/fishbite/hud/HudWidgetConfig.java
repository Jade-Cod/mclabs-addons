package dev.jade.fishbite.hud;

import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.text.Text;

/**
 * Shared Cloth Config entries for a HUD widget (visibility, text colour,
 * background, size). Used by the main config screen; the HUD editor renders its
 * own inline inspector controls rather than these Cloth entries.
 */
public final class HudWidgetConfig {
	private static final int SCALE_MIN = 50;
	private static final int SCALE_MAX = 300;
	private static final float PERCENT = 100.0f;

	private HudWidgetConfig() {
	}

	public static void addEntries(ConfigEntryBuilder entries, ConfigCategory category, HudObject object) {
		HudObjectSettings settings = object.settings();
		int defaultTextColor = object.defaultSettings().textColor;
		String prefix = "fishbite.config.hud";

		category.addEntry(entries
				.startBooleanToggle(Text.translatable(prefix + ".enabled"), settings.enabled)
				.setDefaultValue(true)
				.setTooltip(Text.translatable(prefix + ".enabled.tooltip"))
				.setSaveConsumer(value -> settings.enabled = value)
				.build());
		category.addEntry(entries
				.startIntSlider(Text.translatable(prefix + ".scale"),
						Math.round(settings.scale * PERCENT), SCALE_MIN, SCALE_MAX)
				.setDefaultValue(100)
				.setTextGetter(value -> Text.literal(value + "%"))
				.setTooltip(Text.translatable(prefix + ".scale.tooltip"))
				.setSaveConsumer(value -> settings.scale = value / PERCENT)
				.build());
		category.addEntry(entries
				.startColorField(Text.translatable(prefix + ".text_color"), settings.textColor & 0xFFFFFF)
				.setDefaultValue(defaultTextColor & 0xFFFFFF)
				.setTooltip(Text.translatable(prefix + ".text_color.tooltip"))
				.setSaveConsumer(value -> settings.textColor = 0xFF000000 | value)
				.build());
		category.addEntry(entries
				.startBooleanToggle(Text.translatable(prefix + ".background"), settings.backgroundEnabled)
				.setDefaultValue(true)
				.setTooltip(Text.translatable(prefix + ".background.tooltip"))
				.setSaveConsumer(value -> settings.backgroundEnabled = value)
				.build());
		category.addEntry(entries
				.startAlphaColorField(Text.translatable(prefix + ".background_color"), settings.backgroundColor)
				.setDefaultValue(0x80000000)
				.setTooltip(Text.translatable(prefix + ".background_color.tooltip"))
				.setSaveConsumer(value -> settings.backgroundColor = value)
				.build());
	}

}
