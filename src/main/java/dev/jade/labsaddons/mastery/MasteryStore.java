package dev.jade.labsaddons.mastery;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists the Mastery board to the config so it survives a client restart.
 *
 * <p>Without this the board is empty on every launch, and since
 * {@link MasteryTracker#advance} ignores quests that are not active, a chat
 * reaction won before the session's first {@code /mastery} was silently dropped.
 * Restoring the board on start closes that gap.
 *
 * <p>Deliberately separate from {@link MasteryTracker}: the tracker stays free of
 * Minecraft and config types so it remains unit-testable without a game runtime.
 * The two save points ({@code /mastery} scrape and chat advance) are both reachable
 * from the client, so no callback plumbing is needed.
 */
public final class MasteryStore {
	/**
	 * A restored board older than this is discarded. Challenges get re-rolled, and
	 * crediting a chat reaction to one the player no longer has selected is worse
	 * than showing nothing until the next {@code /mastery}.
	 */
	// ponytail: fixed window; MCLabs exposes no re-roll timestamp to key off.
	static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;

	private MasteryStore() {
	}

	/** Restores the last saved board. Call once the item registry is available. */
	public static void load() {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		if (config.masteryQuests.isEmpty()) {
			return;
		}
		if (System.currentTimeMillis() - config.masterySnapshotMs > MAX_AGE_MS) {
			clear();
			return;
		}
		List<MasteryQuest> restored = new ArrayList<>(config.masteryQuests.size());
		for (MasteryQuestEntry entry : config.masteryQuests) {
			restored.add(new MasteryQuest(icon(entry.icon), entry.name, entry.current, entry.target, entry.percent));
		}
		// The tracker is empty at this point, so this is a baseline: restoring must
		// not fire the pop-up rows as though every quest had just gained.
		MasteryTracker.setQuests(restored);
	}

	/** Writes the current board back to the config. */
	public static void save() {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		List<MasteryQuestEntry> entries = new ArrayList<>();
		for (MasteryQuest quest : MasteryTracker.quests()) {
			entries.add(new MasteryQuestEntry(
					iconId(quest.icon()), quest.name(), quest.current(), quest.target(), quest.percent()));
		}
		config.masteryQuests = entries;
		config.masterySnapshotMs = System.currentTimeMillis();
		config.save();
	}

	/** Drops the board from memory and from disk, so "Clear Mastery" actually sticks. */
	public static void clear() {
		MasteryTracker.clear();
		MasteryGains.clear();
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.masteryQuests = new ArrayList<>();
		config.masterySnapshotMs = 0L;
		config.save();
	}

	private static String iconId(ItemStack stack) {
		return stack == null || stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	/** An id that no longer resolves yields an empty stack: the row still shows, minus its icon. */
	private static ItemStack icon(String id) {
		if (id == null || id.isBlank()) {
			return ItemStack.EMPTY;
		}
		Identifier parsed = Identifier.tryParse(id);
		Item item = parsed == null ? null : BuiltInRegistries.ITEM.getOptional(parsed).orElse(null);
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}
}
