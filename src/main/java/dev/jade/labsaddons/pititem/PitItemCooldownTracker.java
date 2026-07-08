package dev.jade.labsaddons.pititem;

import dev.jade.labsaddons.cooldown.CooldownEntry;
import dev.jade.labsaddons.cooldown.CooldownSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks Pit item cooldowns from the messages the server prints (chat or
 * actionbar). Session-scoped and in-memory, like {@link dev.jade.labsaddons.mcmmo.McmmoCooldownTracker}.
 *
 * <p>Two signals, from weakest to most authoritative:
 * <ul>
 * <li>A private, narrator-style chat line (e.g. "You blink forward!") — seeds
 * the cooldown from the item's known nominal duration.</li>
 * <li>An actionbar "<name> [Ns]" countdown — exact remaining time; corrects
 * (without resetting) any cooldown already in progress, and is the only
 * signal for items with no chat feedback.</li>
 * </ul>
 */
public final class PitItemCooldownTracker {
	/** How long a finished cooldown lingers as a pulsing "Ready!" row. */
	public static final long READY_LINGER_MS = 2_500L;

	private static final Pattern ACTIONBAR = Pattern.compile(
			"^\\s*(.+?)\\s*\\[(\\d+)s]\\s*$", Pattern.CASE_INSENSITIVE);

	private static final Map<PitItem, Pattern> CHAT_PATTERNS = new EnumMap<>(PitItem.class);
	static {
		for (PitItem item : PitItem.values()) {
			String fragment = item.chatFragment();
			if (fragment != null) {
				CHAT_PATTERNS.put(item, Pattern.compile(Pattern.quote(fragment), Pattern.CASE_INSENSITIVE));
			}
		}
	}

	/** Per-item runtime state; readyAtMs/totalMs are meaningless once expired past the linger window. */
	private static final class Slot {
		long readyAtMs;
		/** Countdown seeded from the item's nominal duration, not a server-stated value. */
		boolean approximate;
		/** Full cooldown length, for the HUD ring's recharge progress. */
		long totalMs;
	}

	private static final Map<PitItem, Slot> STATE = new EnumMap<>(PitItem.class);

	private static CooldownSource source;

	private PitItemCooldownTracker() {
	}

	public static void onMessage(String text) {
		onMessage(text, System.currentTimeMillis());
	}

	static void onMessage(String rawText, long nowMs) {
		String text = rawText.indexOf('§') >= 0 ? rawText.replaceAll("§.", "") : rawText;
		for (Map.Entry<PitItem, Pattern> chat : CHAT_PATTERNS.entrySet()) {
			if (chat.getValue().matcher(text).find()) {
				startCooldown(chat.getKey(), chat.getKey().cooldownSeconds() * 1000L, nowMs, true);
				return;
			}
		}
		Matcher actionbar = ACTIONBAR.matcher(text);
		if (actionbar.find()) {
			PitItem item = PitItem.fromActionbarName(actionbar.group(1));
			if (item != null) {
				startCooldown(item, Long.parseLong(actionbar.group(2)) * 1000L, nowMs, false);
			}
		}
	}

	private static void startCooldown(PitItem item, long durationMs, long nowMs, boolean approximate) {
		// A chat trigger (approximate) always begins a fresh recharge cycle, and so does the
		// first sighting of an item we aren't already tracking. An actionbar reading on a cycle
		// already in progress is just a correction of the remaining time, so totalMs (the HUD
		// ring's progress denominator) must be left alone — otherwise every correction would
		// make the ring's recharge progress jump back to 0%.
		boolean isNewCycle = approximate || !STATE.containsKey(item);
		Slot slot = STATE.computeIfAbsent(item, key -> new Slot());
		slot.readyAtMs = nowMs + durationMs;
		slot.approximate = approximate;
		if (isNewCycle) {
			slot.totalMs = durationMs;
		}
	}

	/** Rows for display: soonest-ready first; finished rows linger. */
	public static List<CooldownEntry> entries(long nowMs) {
		List<CooldownEntry> entries = new ArrayList<>();
		Iterator<Map.Entry<PitItem, Slot>> it = STATE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<PitItem, Slot> entry = it.next();
			Slot slot = entry.getValue();
			if (nowMs > slot.readyAtMs + READY_LINGER_MS) {
				it.remove();
				continue;
			}
			entries.add(new CooldownEntry(keyFor(entry.getKey()),
					entry.getKey().displayName(), slot.readyAtMs, false, slot.approximate, slot.totalMs));
		}
		entries.sort(Comparator.comparingLong((CooldownEntry e) -> e.remainingMs(nowMs))
				.thenComparing(CooldownEntry::label));
		return entries;
	}

	public static void clear() {
		STATE.clear();
	}

	static String keyFor(PitItem item) {
		return "pit_item:" + item.name().toLowerCase(Locale.ROOT);
	}

	/** This tracker as a {@link CooldownSource} for the cooldown HUD widget. */
	public static CooldownSource source() {
		if (source == null) {
			source = new CooldownSource() {
				@Override
				public List<CooldownEntry> entries(long nowMs) {
					return PitItemCooldownTracker.entries(nowMs);
				}

				@Override
				@Nullable
				public net.minecraft.item.ItemStack iconFor(String key) {
					return PitItemIcons.iconFor(key);
				}

				@Override
				public void clear() {
					PitItemCooldownTracker.clear();
				}

				@Override
				public String categoryLabel() {
					return "PIT ITEMS";
				}

				@Override
				public List<CooldownSource.Toggleable> allKeys() {
					return java.util.Arrays.stream(PitItem.values())
							.map(item -> new CooldownSource.Toggleable(keyFor(item), item.displayName()))
							.toList();
				}
			};
		}
		return source;
	}
}
