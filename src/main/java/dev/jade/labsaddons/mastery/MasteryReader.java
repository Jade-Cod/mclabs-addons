package dev.jade.labsaddons.mastery;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads your active Mastery challenges from the {@code /mastery} GUI — the same
 * passive once-per-open scrape as {@link dev.jade.labsaddons.runner.SupplierJobsReader}
 * and {@code ChemtainerReader}. Purely passive: never sends commands or closes
 * the screen.
 *
 * <p>An active challenge is a slot whose lore carries <em>both</em> a progress
 * line ({@code "631076.685/1,152,000 (54%)"}) and the re-roll hint
 * {@value #ACTIVE_MARKER}. That hint is what separates the main menu's active
 * quests from the challenge-picker sub-GUI, whose otherwise identical items say
 * {@value #PICKER_MARKER} and carry no progress at all.
 *
 * <p>Slots are matched by lore shape rather than by index, so a server-side
 * layout change degrades to "no quests found" instead of reading the wrong item.
 * Fail-soft: any other screen leaves the tracked set untouched.
 */
public final class MasteryReader {
	/** On active quests only; the server splits it across lore lines after "new". */
	private static final String ACTIVE_MARKER = "right-click to select new";
	/** On the challenge-picker sub-GUI's items, which must not be scraped. */
	private static final String PICKER_MARKER = "click to start challenge";

	/** e.g. "631076.685/1,152,000 (54%)" or "6/15 (40%)" — current may be fractional. */
	private static final Pattern PROGRESS =
			Pattern.compile("([\\d,]+(?:\\.\\d+)?)\\s*/\\s*([\\d,]+(?:\\.\\d+)?)\\s*\\((\\d+)%\\)");

	private MasteryReader() {
	}

	/** @return true if this looked like the /mastery GUI (and the quests were replaced). */
	public static boolean tryRead(AbstractContainerScreen<?> screen) {
		AbstractContainerMenu handler = screen.getMenu();
		List<MasteryQuest> found = new ArrayList<>();
		for (Slot slot : handler.slots) {
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			MasteryQuest quest = parseQuest(stack);
			if (quest != null) {
				found.add(quest);
			}
		}
		if (found.isEmpty()) {
			return false;
		}
		MasteryTracker.setQuests(found);
		return true;
	}

	/** Progress parsed out of an active quest's lore. */
	record Progress(double current, double target, int percent) {
	}

	/** @return the quest this stack represents, or null if it is not an active challenge. */
	private static MasteryQuest parseQuest(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return null;
		}
		List<String> lines = lore.lines().stream().map(Component::getString).toList();
		Progress progress = parseProgress(lines);
		return progress == null ? null
				: new MasteryQuest(stack.copy(), displayName(stack),
						progress.current(), progress.target(), progress.percent());
	}

	/**
	 * Pure lore-shape parse, split out so it is testable without a Minecraft bootstrap.
	 *
	 * @return progress if these lore lines belong to an <em>active</em> challenge, else null.
	 */
	static Progress parseProgress(List<String> loreLines) {
		boolean active = false;
		Matcher progress = null;
		for (String line : loreLines) {
			String text = line.toLowerCase(Locale.ROOT);
			if (text.contains(PICKER_MARKER)) {
				return null;
			}
			if (text.contains(ACTIVE_MARKER)) {
				active = true;
			}
			if (progress == null) {
				Matcher matcher = PROGRESS.matcher(text);
				if (matcher.find()) {
					progress = matcher;
				}
			}
		}
		if (!active || progress == null) {
			return null;
		}
		double target = parseNumber(progress.group(2));
		if (target <= 0) {
			return null;
		}
		return new Progress(parseNumber(progress.group(1)), target, Integer.parseInt(progress.group(3)));
	}

	/** Strips digit grouping ("1,152,000" -> 1152000.0). */
	private static double parseNumber(String raw) {
		try {
			return Double.parseDouble(raw.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String displayName(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name != null ? name.getString() : stack.getHoverName().getString();
	}
}
