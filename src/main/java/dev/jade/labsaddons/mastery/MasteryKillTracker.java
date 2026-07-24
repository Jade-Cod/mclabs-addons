package dev.jade.labsaddons.mastery;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Advances the {@code Kill <mob>} Mastery challenges live from the world. The Pit
 * is solo, so every mob death there is the player's own kill and no attribution is
 * needed — and no chat line announces it, so the death has to be read off the world.
 *
 * <p>A dying mob is observable client-side from its own custom name (the name tag):
 * the server rewrites that tag to a heart bar while you fight the mob and back to the
 * mob's name as it dies, and {@link LivingEntity#isDead()} flips once its health hits
 * zero. We remember each matched mob by entity id the first time its tag is readable —
 * so the mid-fight heart bar, which no longer spells the mob name, cannot lose it —
 * then credit the kill exactly once when it dies (the death animation lingers ~20
 * ticks, all reporting {@code isDead()}).
 *
 * <p>Gated implicitly on an active {@code Kill} challenge: with none, nothing is
 * scanned, and {@link MasteryTracker#advance} ignores any mob whose challenge the
 * player has not selected — so a plain-name mob elsewhere can never miscredit. The
 * bumps are optimistic; the next {@code /mastery} scrape reconciles against the server.
 */
public final class MasteryKillTracker {
	private static final String KILL_PREFIX = "kill ";

	// entity id -> the active challenge its tag matched; survives the heart-bar phase.
	private static final Map<Integer, String> matched = new HashMap<>();
	// Ids already credited, so the multi-tick death animation counts a kill once.
	private static final Set<Integer> counted = new HashSet<>();

	private MasteryKillTracker() {
	}

	/** @return true if a kill advanced an active challenge, so the caller can persist the board. */
	public static boolean tick(ClientWorld world) {
		Map<String, String> targets = activeKillTargets();
		if (targets.isEmpty()) {
			reset();
			return false;
		}
		Set<Integer> present = new HashSet<>();
		boolean advanced = false;
		for (Entity entity : world.getEntities()) {
			if (!(entity instanceof LivingEntity mob)) {
				continue;
			}
			int id = mob.getId();
			present.add(id);
			Text name = mob.getCustomName();
			advanced |= observe(id, name == null ? null : name.getString(), mob.isDead(), targets);
		}
		// Bound both maps to currently-loaded entities: a removed mob's id drops out
		// (it has already been counted), and a reused id starts tracking fresh.
		matched.keySet().retainAll(present);
		counted.retainAll(present);
		return advanced;
	}

	/**
	 * Records one mob sighting for a tick. Minecraft-free seam so the matching,
	 * heart-phase survival, and count-once behaviour are unit-testable.
	 *
	 * @return true if this call is the one that credits the kill.
	 */
	static boolean observe(int id, String tag, boolean dead, Map<String, String> targets) {
		String challenge = matched.get(id);
		if (challenge == null && tag != null) {
			challenge = matchChallenge(tag, targets);
			if (challenge != null) {
				matched.put(id, challenge);
			}
		}
		if (challenge != null && dead && counted.add(id)) {
			return MasteryTracker.advance(challenge, 1);
		}
		return false;
	}

	/** Active {@code Kill X} challenges as lowercase mob name -> the exact challenge name. */
	static Map<String, String> activeKillTargets() {
		Map<String, String> targets = new HashMap<>();
		for (MasteryQuest quest : MasteryTracker.quests()) {
			String name = quest.name();
			if (name.length() > KILL_PREFIX.length()
					&& name.toLowerCase(Locale.ROOT).startsWith(KILL_PREFIX)) {
				targets.put(name.substring(KILL_PREFIX.length()).toLowerCase(Locale.ROOT), name);
			}
		}
		return targets;
	}

	/** The challenge a name tag belongs to, or null. */
	private static String matchChallenge(String tag, Map<String, String> targets) {
		String lower = tag.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, String> target : targets.entrySet()) {
			// contains, not equals: a tag may carry a level prefix or a trailing
			// health figure around the mob name.
			if (lower.contains(target.getKey())) {
				return target.getValue();
			}
		}
		return null;
	}

	/** Drop all per-session state (call on disconnect / when no Kill challenge is active). */
	public static void reset() {
		matched.clear();
		counted.clear();
	}
}
