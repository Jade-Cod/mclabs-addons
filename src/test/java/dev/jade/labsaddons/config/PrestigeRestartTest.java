package dev.jade.labsaddons.config;

import dev.jade.labsaddons.mastery.MasteryGains;
import dev.jade.labsaddons.prestige.PrestigeChat;
import dev.jade.labsaddons.prestige.PrestigeChem;
import dev.jade.labsaddons.prestige.PrestigeStore;
import dev.jade.labsaddons.prestige.PrestigeTracker;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sale lines end to end, in both server formats, with a restart in between. */
public class PrestigeRestartTest {
	@TempDir
	Path configDir;

	@BeforeEach
	public void reset() {
		PrestigeTracker.clear();
		PrestigeChat.reset();
		MasteryGains.clear();
	}

	private static Component hovered(String visible, String tooltip) {
		return Component.literal(visible).withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(tooltip))));
	}

	private static double current(int index) {
		return PrestigeTracker.chems().get(index).current();
	}

	@Test
	public void aSaleAfterARestartAdvancesTheRestoredTrack() {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));

		PrestigeChat.onMessage(Component.literal("Your Prestige Progress"));
		PrestigeChat.onMessage(hovered("[||||] Cactium", "Cactium: 412,880/1,382,400"));
		assertTrue(PrestigeChat.onMessage(Component.literal("Hover over a chem to see full progress amount.")));
		PrestigeStore.save();
		LabsAddonsConfig.get().saveNow();

		// Restart: fresh store, fresh instance, empty tracker.
		PrestigeTracker.clear();
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		PrestigeStore.load();

		// Several chems (compound, or more than one raw chem): no number, amounts in the hover.
		assertTrue(PrestigeChat.onMessage(hovered("» Earned prestige progress for Cactium and Potatium.",
				"Cactium x8,273\nPotatium x5,516")));
		assertEquals(412_880 + 8_273, current(0));
	}

	/** One raw chem, verbatim from MCLabs: the amount is in the line and the hover has none. */
	@Test
	public void aSingleRawChemSaleAdvancesFromTheLine() {
		PrestigeTracker.merge(List.of(new PrestigeChem("Betronium", 150_000, 806_400)));
		String line = "» Earned 307 prestige progress for Betronium. (1.6x rate)";

		assertTrue(PrestigeChat.isSaleLine(line));
		assertTrue(PrestigeChat.onMessage(hovered(line, "Click to see progress")));
		assertEquals(150_307, current(0));
	}

	/** A single total for several chems has never been seen; if it turns up, nothing is guessed. */
	@Test
	public void aTotalForSeveralChemsIsNotGuessedAt() {
		PrestigeTracker.merge(List.of(new PrestigeChem("Cactium", 1_000, 1_382_400)));

		assertFalse(PrestigeChat.onMessage(Component.literal("» Earned 1,000 prestige progress for Cactium and Potatium.")));
		assertEquals(1_000, current(0));
	}
}
