package dev.jade.fishbite.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import dev.jade.fishbite.hud.HudObjects;
import dev.jade.fishbite.hud.HudWidgetConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Cloth Config screen shown from Mod Menu: marker toggle, size
 * slider, two colour pickers, other-bobber muting, and the catch-sound picker.
 */
public final class FishBiteConfigScreenFactory {
	private static final int SCALE_SLIDER_MIN = 25;
	private static final int SCALE_SLIDER_MAX = 400;
	private static final int SCALE_SLIDER_DEFAULT = 100;
	private static final float PERCENT = 100.0f;

	private FishBiteConfigScreenFactory() {
	}

	public static Screen create(Screen parent) {
		FishBiteConfig config = FishBiteConfig.get();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Text.translatable("fishbite.config.title"))
				.setSavingRunnable(config::save);

		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory general = builder.getOrCreateCategory(
				Text.translatable("fishbite.config.category.general"));

		general.addEntry(entries
				.startBooleanToggle(Text.translatable("fishbite.config.enabled"), config.enabled)
				.setDefaultValue(true)
				.setTooltip(Text.translatable("fishbite.config.enabled.tooltip"))
				.setSaveConsumer(value -> config.enabled = value)
				.build());

		general.addEntry(entries
				.startIntSlider(Text.translatable("fishbite.config.scale"),
						Math.round(config.markerScale * PERCENT), SCALE_SLIDER_MIN, SCALE_SLIDER_MAX)
				.setDefaultValue(SCALE_SLIDER_DEFAULT)
				.setTooltip(Text.translatable("fishbite.config.scale.tooltip"))
				.setTextGetter(value -> Text.literal(value + "%"))
				.setSaveConsumer(value -> config.markerScale = value / PERCENT)
				.build());

		general.addEntry(entries
				.startColorField(Text.translatable("fishbite.config.waiting_color"), config.waitingColor)
				.setDefaultValue(FishBiteConfig.DEFAULT_WAITING_COLOR)
				.setTooltip(Text.translatable("fishbite.config.waiting_color.tooltip"))
				.setSaveConsumer(value -> config.waitingColor = value)
				.build());

		general.addEntry(entries
				.startColorField(Text.translatable("fishbite.config.bite_color"), config.biteColor)
				.setDefaultValue(FishBiteConfig.DEFAULT_BITE_COLOR)
				.setTooltip(Text.translatable("fishbite.config.bite_color.tooltip"))
				.setSaveConsumer(value -> config.biteColor = value)
				.build());

		general.addEntry(entries
				.startBooleanToggle(Text.translatable("fishbite.config.mute_others"), config.muteOtherBobbers)
				.setDefaultValue(false)
				.setTooltip(Text.translatable("fishbite.config.mute_others.tooltip"))
				.setSaveConsumer(value -> config.muteOtherBobbers = value)
				.build());

		general.addEntry(entries
				.startDropdownMenu(Text.translatable("fishbite.config.catch_sound"),
						config.catchSound, value -> value,
						value -> Text.literal(value.isEmpty()
								? Text.translatable("fishbite.config.catch_sound.vanilla").getString()
								: value))
				.setSelections(soundIdSuggestions())
				.setSuggestionMode(false)
				.setDefaultValue("")
				.setTooltip(Text.translatable("fishbite.config.catch_sound.tooltip"))
				.setSaveConsumer(value -> config.catchSound = normalizeSoundId(value))
				.build());


		// One category per registered HUD widget, all sharing the same entries.
		HudObjects.all().forEach(object -> HudWidgetConfig.addEntries(entries,
				builder.getOrCreateCategory(object.displayName()), object));

		return builder.build();
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
