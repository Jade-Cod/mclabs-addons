package dev.jade.labsaddons.config;

import dev.jade.labsaddons.item.ItemUsesCorner;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Cloth Config screen shown from Mod Menu: marker toggle, size
 * slider, two colour pickers, other-bobber muting, and the catch-sound picker.
 */
public final class LabsAddonsConfigScreenFactory {
	private static final int SCALE_SLIDER_MIN = 25;
	private static final int SCALE_SLIDER_MAX = 400;
	private static final int SCALE_SLIDER_DEFAULT = 100;
	private static final float PERCENT = 100.0f;
	private static final int ITEM_USES_SCALE_SLIDER_MIN = 80;
	private static final int ITEM_USES_SCALE_SLIDER_MAX = 110;
	private static final int ITEM_USES_SCALE_SLIDER_DEFAULT = 100;

	private LabsAddonsConfigScreenFactory() {
	}

	public static Screen create(Screen parent) {
		LabsAddonsConfig config = LabsAddonsConfig.get();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Text.translatable("labsaddons.config.title"))
				.setSavingRunnable(config::save);

		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory general = builder.getOrCreateCategory(
				Text.translatable("labsaddons.config.category.general"));

		general.addEntry(entries
				.startBooleanToggle(Text.translatable("labsaddons.config.enabled"), config.enabled)
				.setDefaultValue(true)
				.setTooltip(Text.translatable("labsaddons.config.enabled.tooltip"))
				.setSaveConsumer(value -> config.enabled = value)
				.build());

		general.addEntry(entries
				.startIntSlider(Text.translatable("labsaddons.config.scale"),
						Math.round(config.markerScale * PERCENT), SCALE_SLIDER_MIN, SCALE_SLIDER_MAX)
				.setDefaultValue(SCALE_SLIDER_DEFAULT)
				.setTooltip(Text.translatable("labsaddons.config.scale.tooltip"))
				.setTextGetter(value -> Text.literal(value + "%"))
				.setSaveConsumer(value -> config.markerScale = value / PERCENT)
				.build());

		general.addEntry(entries
				.startColorField(Text.translatable("labsaddons.config.waiting_color"), config.waitingColor)
				.setDefaultValue(LabsAddonsConfig.DEFAULT_WAITING_COLOR)
				.setTooltip(Text.translatable("labsaddons.config.waiting_color.tooltip"))
				.setSaveConsumer(value -> config.waitingColor = value)
				.build());

		general.addEntry(entries
				.startColorField(Text.translatable("labsaddons.config.bite_color"), config.biteColor)
				.setDefaultValue(LabsAddonsConfig.DEFAULT_BITE_COLOR)
				.setTooltip(Text.translatable("labsaddons.config.bite_color.tooltip"))
				.setSaveConsumer(value -> config.biteColor = value)
				.build());

		general.addEntry(entries
				.startBooleanToggle(Text.translatable("labsaddons.config.mute_others"), config.muteOtherBobbers)
				.setDefaultValue(false)
				.setTooltip(Text.translatable("labsaddons.config.mute_others.tooltip"))
				.setSaveConsumer(value -> config.muteOtherBobbers = value)
				.build());

		general.addEntry(entries
				.startDropdownMenu(Text.translatable("labsaddons.config.catch_sound"),
						config.catchSound, value -> value,
						value -> Text.literal(value.isEmpty()
								? Text.translatable("labsaddons.config.catch_sound.vanilla").getString()
								: value))
				.setSelections(soundIdSuggestions())
				.setSuggestionMode(false)
				.setDefaultValue("")
				.setTooltip(Text.translatable("labsaddons.config.catch_sound.tooltip"))
				.setSaveConsumer(value -> config.catchSound = normalizeSoundId(value))
				.build());


		addItemUsesEntries(entries, builder.getOrCreateCategory(
				Text.translatable("labsaddons.config.category.item_uses")), config);

		return builder.build();
	}

	private static void addItemUsesEntries(ConfigEntryBuilder entries, ConfigCategory category,
			LabsAddonsConfig config) {
		String prefix = "labsaddons.config.item_uses";

		category.addEntry(entries
				.startBooleanToggle(Text.translatable(prefix + ".enabled"), config.itemUsesEnabled)
				.setDefaultValue(true)
				.setTooltip(Text.translatable(prefix + ".enabled.tooltip"))
				.setSaveConsumer(value -> config.itemUsesEnabled = value)
				.build());

		category.addEntry(entries
				.startEnumSelector(Text.translatable(prefix + ".corner"),
						ItemUsesCorner.class, ItemUsesCorner.valueOf(config.itemUsesCorner))
				.setDefaultValue(ItemUsesCorner.TOP_LEFT)
				.setEnumNameProvider(value -> Text.translatable(
						prefix + ".corner." + ((ItemUsesCorner) value).name().toLowerCase(java.util.Locale.ROOT)))
				.setTooltip(Text.translatable(prefix + ".corner.tooltip"))
				.setSaveConsumer(value -> config.itemUsesCorner = value.name())
				.build());

		category.addEntry(entries
				.startColorField(Text.translatable(prefix + ".color"), config.itemUsesColor & 0xFFFFFF)
				.setDefaultValue(LabsAddonsConfig.DEFAULT_ITEM_USES_COLOR & 0xFFFFFF)
				.setTooltip(Text.translatable(prefix + ".color.tooltip"))
				.setSaveConsumer(value -> config.itemUsesColor = 0xFF000000 | value)
				.build());

		category.addEntry(entries
				.startIntSlider(Text.translatable(prefix + ".scale"),
						Math.round(config.itemUsesScale / LabsAddonsConfig.DEFAULT_ITEM_USES_SCALE * PERCENT),
						ITEM_USES_SCALE_SLIDER_MIN, ITEM_USES_SCALE_SLIDER_MAX)
				.setDefaultValue(ITEM_USES_SCALE_SLIDER_DEFAULT)
				.setTooltip(Text.translatable(prefix + ".scale.tooltip"))
				.setTextGetter(value -> Text.literal(value + "%"))
				.setSaveConsumer(value -> config.itemUsesScale =
						value / PERCENT * LabsAddonsConfig.DEFAULT_ITEM_USES_SCALE)
				.build());
	}

	/**
	 * In suggestion mode the dropdown's displayed text round-trips through save,
	 * so the friendly "(vanilla splash)" label can leak in as the stored value.
	 * Keep only real registered sound ids; everything else means "vanilla".
	 */
	private static String normalizeSoundId(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		Identifier id = Identifier.tryParse(value.trim());
		if (id == null || !Registries.SOUND_EVENT.containsId(id)) {
			return "";
		}
		return id.toString();
	}

	private static List<String> soundIdSuggestions() {
		List<String> ids = new ArrayList<>();
		ids.add("");
		Registries.SOUND_EVENT.getIds().stream()
				.map(Identifier::toString)
				.sorted()
				.forEach(ids::add);
		return ids;
	}
}
