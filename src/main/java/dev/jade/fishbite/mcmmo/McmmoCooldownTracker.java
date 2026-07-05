package dev.jade.fishbite.mcmmo;

import dev.jade.fishbite.cooldown.CooldownEntry;
import dev.jade.fishbite.cooldown.CooldownSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks mcMMO super-ability cooldowns from the messages mcMMO prints (chat or
 * actionbar, en_US locale). Session-scoped and in-memory, like the other
 * runtime trackers: cooldowns are minutes-long, so nothing is persisted.
 *
 * <p>Signals, from weakest to most authoritative:
 * <ul>
 * <li>"**SUPER BREAKER ACTIVATED**" — ability running (shown as Active).</li>
 * <li>"**Super Breaker has worn off**" — recharge starts; seeded with mcMMO's
 * default 240s and self-corrected by any authoritative line below.</li>
 * <li>"You are too tired to use that ability again. (45s)" — exact remaining;
 * the ability is unnamed, so it is resolved from the held tool.</li>
 * <li>"You ready your Axe. (Skull Splitter is on cooldown for 30s)" — exact.</li>
 * <li>"/mccooldown" rows ("Super Breaker - 45 seconds left" / "- Ready!") —
 * exact, the sync command surfaced in the Help guide.</li>
 * <li>"Your Super Breaker ability is refreshed!" — ready (brief pulse, then
 * the row disappears).</li>
 * </ul>
 */
public final class McmmoCooldownTracker {
	/** mcMMO's default super-ability recharge; seed until an exact line arrives. */
	public static final long DEFAULT_COOLDOWN_MS = 240_000L;
	/** How long a finished cooldown lingers as a pulsing "Ready!" row. */
	public static final long READY_LINGER_MS = 2_500L;

	/** Some servers replace mcMMO's ** emphasis with ×× — accept either. */
	private static final String MARK = "[*×]{2}";
	private static final Pattern ACTIVATED = Pattern.compile(
			MARK + "(.+?) ACTIVATED" + MARK, Pattern.CASE_INSENSITIVE);
	/** Self only — the "worn off for PlayerName" broadcast has no ** markers. */
	private static final Pattern WORN_OFF = Pattern.compile(
			MARK + "(.+?) has worn off" + MARK, Pattern.CASE_INSENSITIVE);
	private static final Pattern REFRESHED = Pattern.compile(
			"Your (.+?) ability is refreshed!", Pattern.CASE_INSENSITIVE);
	private static final Pattern ALL_REFRESHED = Pattern.compile(
			MARK + "ABILITIES REFRESHED!" + MARK, Pattern.CASE_INSENSITIVE);
	private static final Pattern TOO_TIRED = Pattern.compile(
			"too tired to use that ability again.*?\\((\\d+)\\s*s\\)", Pattern.CASE_INSENSITIVE);
	private static final Pattern NAMED_COOLDOWN = Pattern.compile(
			"\\((.+?) is on cooldown for (\\d+)\\s*s\\)", Pattern.CASE_INSENSITIVE);
	/** /mccooldown rows: "  Super Breaker - 45 seconds left" / "  Tree Feller - Ready!". */
	private static final Pattern COOLDOWN_ROW = Pattern.compile(
			"^\\s*(.+?)\\s*-\\s*(\\d+) seconds left", Pattern.CASE_INSENSITIVE);
	private static final Pattern READY_ROW = Pattern.compile(
			"^\\s*(.+?)\\s*-\\s*Ready!", Pattern.CASE_INSENSITIVE);

	/** Per-ability runtime state; readyAtMs/totalMs are meaningless while active. */
	private static final class Slot {
		boolean active;
		long readyAtMs;
		/** Countdown seeded from the default duration, not a server-stated value. */
		boolean approximate;
		/** Full cooldown length, for the HUD ring's recharge progress. */
		long totalMs;
	}

	private static final Map<McmmoAbility, Slot> STATE = new EnumMap<>(McmmoAbility.class);

	/** What the player is holding, for the unnamed "too tired" line. */
	private static Supplier<McmmoAbility.@Nullable Tool> heldToolResolver = () -> null;

	private static CooldownSource source;

	private McmmoCooldownTracker() {
	}

	public static void onMessage(String text) {
		onMessage(text, System.currentTimeMillis());
	}

	static void onMessage(String rawText, long nowMs) {
		// Servers sometimes embed legacy §-format codes in the literal text.
		String text = rawText.indexOf('§') >= 0 ? rawText.replaceAll("§.", "") : rawText;
		Matcher activated = ACTIVATED.matcher(text);
		if (activated.find()) {
			McmmoAbility ability = McmmoAbility.fromName(activated.group(1));
			if (ability != null) {
				Slot slot = STATE.computeIfAbsent(ability, key -> new Slot());
				slot.active = true;
				slot.readyAtMs = 0L;
			}
			return;
		}
		if (ALL_REFRESHED.matcher(text).find()) {
			STATE.clear();
			return;
		}
		Matcher wornOff = WORN_OFF.matcher(text);
		if (wornOff.find()) {
			startCooldown(McmmoAbility.fromName(wornOff.group(1)), DEFAULT_COOLDOWN_MS, nowMs, true);
			return;
		}
		Matcher refreshed = REFRESHED.matcher(text);
		if (refreshed.find()) {
			// Ready now: linger as a pulsing "Ready!" row, then drop off.
			startCooldown(McmmoAbility.fromName(refreshed.group(1)), 0L, nowMs, false);
			return;
		}
		Matcher tooTired = TOO_TIRED.matcher(text);
		if (tooTired.find()) {
			startCooldown(resolveHeldAbility(), Long.parseLong(tooTired.group(1)) * 1000L, nowMs, false);
			return;
		}
		Matcher named = NAMED_COOLDOWN.matcher(text);
		if (named.find()) {
			startCooldown(McmmoAbility.fromName(named.group(1)),
					Long.parseLong(named.group(2)) * 1000L, nowMs, false);
			return;
		}
		Matcher row = COOLDOWN_ROW.matcher(text);
		if (row.find()) {
			startCooldown(McmmoAbility.fromName(row.group(1)),
					Long.parseLong(row.group(2)) * 1000L, nowMs, false);
			return;
		}
		Matcher ready = READY_ROW.matcher(text);
		if (ready.find()) {
			McmmoAbility ability = McmmoAbility.fromName(ready.group(1));
			if (ability != null) {
				// Already ready when synced — nothing to celebrate, just drop it.
				STATE.remove(ability);
			}
		}
	}

	private static void startCooldown(@Nullable McmmoAbility ability, long durationMs, long nowMs,
			boolean approximate) {
		if (ability == null) {
			return;
		}
		Slot slot = STATE.computeIfAbsent(ability, key -> new Slot());
		slot.active = false;
		slot.readyAtMs = nowMs + durationMs;
		slot.approximate = approximate;
		slot.totalMs = durationMs;
	}

	/**
	 * The ability behind an unnamed "too tired" line: unique for most tools, but
	 * an axe serves both Tree Feller and Skull Splitter — then only an already
	 * tracked candidate is refined, never guessed.
	 */
	@Nullable
	private static McmmoAbility resolveHeldAbility() {
		List<McmmoAbility> candidates = McmmoAbility.forTool(heldToolResolver.get());
		if (candidates.size() == 1) {
			return candidates.get(0);
		}
		List<McmmoAbility> tracked = candidates.stream().filter(STATE::containsKey).toList();
		return tracked.size() == 1 ? tracked.get(0) : null;
	}

	/** Rows for display: active first, then soonest-ready; finished rows linger. */
	public static List<CooldownEntry> entries(long nowMs) {
		List<CooldownEntry> entries = new ArrayList<>();
		Iterator<Map.Entry<McmmoAbility, Slot>> it = STATE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<McmmoAbility, Slot> entry = it.next();
			Slot slot = entry.getValue();
			if (!slot.active && nowMs > slot.readyAtMs + READY_LINGER_MS) {
				it.remove();
				continue;
			}
			entries.add(new CooldownEntry(keyFor(entry.getKey()),
					entry.getKey().displayName(), slot.readyAtMs, slot.active, slot.approximate,
					slot.totalMs));
		}
		entries.sort(Comparator.comparing((CooldownEntry e) -> !e.active())
				.thenComparingLong(e -> e.remainingMs(nowMs))
				.thenComparing(CooldownEntry::label));
		return entries;
	}

	public static void setHeldToolResolver(Supplier<McmmoAbility.@Nullable Tool> resolver) {
		heldToolResolver = resolver;
	}

	public static void clear() {
		STATE.clear();
	}

	static String keyFor(McmmoAbility ability) {
		return "mcmmo:" + ability.name().toLowerCase(Locale.ROOT);
	}

	/** This tracker as a {@link CooldownSource} for the cooldown HUD widget. */
	public static CooldownSource source() {
		if (source == null) {
			source = new CooldownSource() {
				@Override
				public List<CooldownEntry> entries(long nowMs) {
					return McmmoCooldownTracker.entries(nowMs);
				}

				@Override
				public net.minecraft.item.ItemStack iconFor(String key) {
					return McmmoSkillIcons.iconFor(key);
				}

				@Override
				public void clear() {
					McmmoCooldownTracker.clear();
				}
			};
		}
		return source;
	}
}
