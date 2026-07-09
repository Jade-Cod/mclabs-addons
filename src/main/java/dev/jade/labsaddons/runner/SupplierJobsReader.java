package dev.jade.labsaddons.runner;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Reconciles the outstanding posted-jobs count from YOUR {@code /supplier} GUI,
 * the same passive once-per-open scrape pattern as
 * {@link dev.jade.labsaddons.chem.ChemtainerReader}. Each of your jobs is an item
 * whose lore shows either "Click to cancel job." (unclaimed) or "Runner: &lt;name&gt;"
 * (claimed); we count those slots and hand the total to
 * {@link RunnerTracker#reconcilePosted(int)}.
 *
 * <p>Critically this must NOT match the public {@code /runner jobs} board (which
 * lists everyone's jobs) — those items carry a "Supplier:" line and an "Enter
 * /runner duty" line, neither of which appear on your own jobs, so they are
 * excluded. Fail-soft: any other screen (or an empty supplier menu, which is
 * indistinguishable) leaves the tracked count untouched.
 */
public final class SupplierJobsReader {
	private static final String CANCEL = "click to cancel job";
	private static final String CLAIMED = "runner:";
	private static final String BOARD_SUPPLIER = "supplier:";
	private static final String BOARD_DUTY = "/runner duty";

	private SupplierJobsReader() {
	}

	/** @return true if this looked like the /supplier GUI (and the count was reconciled). */
	public static boolean tryRead(HandledScreen<?> screen) {
		ScreenHandler handler = screen.getScreenHandler();
		int jobs = 0;
		for (Slot slot : handler.slots) {
			ItemStack stack = slot.getStack();
			if (!stack.isEmpty() && isSupplierJob(stack)) {
				jobs++;
			}
		}
		if (jobs == 0) {
			return false;
		}
		RunnerTracker.reconcilePosted(jobs);
		return true;
	}

	/** A slot on your own /supplier menu (unclaimed or claimed), excluding the public board. */
	private static boolean isSupplierJob(ItemStack stack) {
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore == null) {
			return false;
		}
		boolean own = false;
		boolean board = false;
		for (Text line : lore.lines()) {
			String text = line.getString().toLowerCase(Locale.ROOT);
			if (text.contains(CANCEL) || text.contains(CLAIMED)) {
				own = true;
			}
			if (text.contains(BOARD_SUPPLIER) || text.contains(BOARD_DUTY)) {
				board = true;
			}
		}
		return own && !board;
	}
}
