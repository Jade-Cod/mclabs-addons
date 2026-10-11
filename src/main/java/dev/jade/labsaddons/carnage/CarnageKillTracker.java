package dev.jade.labsaddons.carnage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * Advances the {@code Kill Nx <mob>} Carnage missions live. The Pit's kill tracker reads a
 * mob's name off its own tag and counts it once it's seen dead, and on Carnage's mobs that misses
 * whole species. Measured over 2,600 kills: no Pumpkin Fiend was ever credited (what you hit is
 * the pumpkin, a falling block, or the spider under it, which is removed without ever showing
 * dead), and none of the bosses built as player NPCs (Scarecrow, Vampire, The Slimed, Dr.
 * Frankenstein), whose bodies carry no name at all. So:
 *
 * <ul>
 *   <li>Every entity's own text counts as a name: a custom name, or a line of a text display,
 *       remembered the first time it reads as a plain name, since tags turn into a heart bar
 *       mid-fight.</li>
 *   <li>A hit entity with none borrows one from its riding stack, else from the nearest named
 *       entity within {@link #NEAR_BLOCKS}, a text display first: a display is only ever a label,
 *       never a neighbouring mob, and the nearest of anything was the peon standing beside a boss
 *       about one time in ten.</li>
 *   <li>A hit entity that disappears within the window is a death too.</li>
 *   <li>A death only counts once a {@code +N Carnage Points} line pairs with it
 *       ({@link CarnageKillPairing}): the server pays the killer alone, so a mob we tagged and
 *       someone else finished no longer counts.</li>
 * </ul>
 *
 * <p>Runs only while the event is on and a Kill mission is open. Bumps are still optimistic;
 * the server's "Daily mission complete!" line and the next {@code /carnage} settle them.
 */
public final class CarnageKillTracker {
	/** How recently we must have hit a mob for its death to be a candidate for our award. */
	static final long LAST_HIT_WINDOW_MS = 2_000L;
	/** How far a nameless hit entity looks for a name to borrow. A model's name floats over its top. */
	private static final double NEAR_BLOCKS = 3.0;
	/** Letters, spaces and the punctuation in "Jack o' 'Sploder" and "Dr. Frankenstein". */
	private static final Pattern MOB_NAME = Pattern.compile("^[A-Za-z][A-Za-z .'-]{0,31}$");

	/** Where a hit entity's name came from, best first. */
	private enum Source { SELF, STACK, DISPLAY, NEARBY }

	private record Label(String name, Source source) {
	}

	private static final CarnageKillPairing pairing = new CarnageKillPairing();
	// entity id -> a plain name off its own text, learned while the tag still spells it
	private static final Map<Integer, String> names = new HashMap<>();
	// hit entity id -> the name it goes by, its own or borrowed
	private static final Map<Integer, Label> labels = new HashMap<>();
	private static final Map<Integer, Long> hitAtMs = new HashMap<>();
	// deaths already reported: the death animation reads as dead for a second
	private static final Set<Integer> counted = new HashSet<>();
	private static String lastAward;

	private CarnageKillTracker() {
	}

	public static void onPlayerHit(int entityId) {
		if (isActive()) {
			hitAtMs.put(entityId, System.currentTimeMillis());
		}
	}

	/** A kill's actionbar line, from {@link CarnageTracker#onActionbar}. */
	static void onAward(CarnageTracker.Award award) {
		if (!isActive()) {
			return;
		}
		// the server re-sends the line to keep it on screen; every real kill moves today's total
		String key = award.today() + "/" + award.stage();
		if (!key.equals(lastAward)) {
			lastAward = key;
			pairing.onAward(System.currentTimeMillis());
		}
	}

	public static void tick(ClientWorld world) {
		if (!isActive()) {
			reset();
			return;
		}
		long nowMs = System.currentTimeMillis();
		Set<Integer> present = new HashSet<>();
		List<Entity> named = new ArrayList<>();
		for (Entity entity : world.getEntities()) {
			present.add(entity.getId());
			String known = names.get(entity.getId());
			if (known == null) {
				known = ownName(entity).orElse(null);
				if (known != null) {
					names.put(entity.getId(), known);
				}
			}
			if (known != null) {
				named.add(entity);
			}
		}
		for (Integer id : List.copyOf(hitAtMs.keySet())) {
			Entity entity = world.getEntityById(id);
			if (entity == null) {
				died(id, nowMs);
				continue;
			}
			label(entity, named);
			if (entity instanceof LivingEntity mob && mob.isDead()) {
				died(id, nowMs);
			}
		}
		hitAtMs.entrySet().removeIf(hit -> !present.contains(hit.getKey()) || nowMs - hit.getValue() > LAST_HIT_WINDOW_MS);
		labels.keySet().retainAll(hitAtMs.keySet());
		names.keySet().retainAll(present);
		counted.retainAll(present);
		for (String mob : pairing.drain(nowMs)) {
			if (!CarnageKillPairing.UNNAMED.equals(mob)) {
				CarnageTracker.onKill(mob);
			}
		}
	}

	/** Drop all per-session state (disconnect, or no open Kill mission). */
	public static void reset() {
		pairing.reset();
		names.clear();
		labels.clear();
		hitAtMs.clear();
		counted.clear();
		lastAward = null;
	}

	private static boolean isActive() {
		return CarnageEvent.isOn() && !CarnageTracker.killTargets().isEmpty();
	}

	/** A plain name off the entity's own text. Players are never mobs, even when they read like one. */
	private static Optional<String> ownName(Entity entity) {
		if (entity instanceof PlayerEntity) {
			return Optional.empty();
		}
		if (entity instanceof DisplayEntity.TextDisplayEntity display) {
			return Arrays.stream(display.getText().getString().split("\n"))
					.map(String::trim)
					.filter(line -> MOB_NAME.matcher(line).matches())
					.findFirst();
		}
		Text tag = entity.getCustomName();
		String text = tag == null ? null : tag.getString().trim();
		return text != null && MOB_NAME.matcher(text).matches() ? Optional.of(text) : Optional.empty();
	}

	/** Names a hit entity, keeping the best source seen so far. */
	private static void label(Entity entity, List<Entity> named) {
		Label current = labels.get(entity.getId());
		if (current != null && current.source() == Source.SELF) {
			return;
		}
		Label found = findLabel(entity, named);
		if (found != null && (current == null || found.source().ordinal() < current.source().ordinal())) {
			labels.put(entity.getId(), found);
		}
	}

	private static Label findLabel(Entity entity, List<Entity> named) {
		String own = names.get(entity.getId());
		if (own != null) {
			return new Label(own, Source.SELF);
		}
		// a hitbox and a name display riding one base, or riding each other
		Optional<String> stacked = entity.getRootVehicle().streamSelfAndPassengers()
				.map(member -> names.get(member.getId()))
				.filter(Objects::nonNull)
				.findFirst();
		if (stacked.isPresent()) {
			return new Label(stacked.get(), Source.STACK);
		}
		Entity display = nearest(entity, named, true);
		if (display != null) {
			return new Label(names.get(display.getId()), Source.DISPLAY);
		}
		Entity other = nearest(entity, named, false);
		return other == null ? null : new Label(names.get(other.getId()), Source.NEARBY);
	}

	private static Entity nearest(Entity entity, List<Entity> named, boolean displaysOnly) {
		Entity best = null;
		for (Entity other : named) {
			if (other == entity || displaysOnly && !(other instanceof DisplayEntity.TextDisplayEntity)) {
				continue;
			}
			double distance = other.squaredDistanceTo(entity);
			if (distance <= NEAR_BLOCKS * NEAR_BLOCKS && (best == null || distance < best.squaredDistanceTo(entity))) {
				best = other;
			}
		}
		return best;
	}

	private static void died(int id, long nowMs) {
		Long hitMs = hitAtMs.get(id);
		if (hitMs == null || nowMs - hitMs > LAST_HIT_WINDOW_MS || !counted.add(id)) {
			return;
		}
		Label label = labels.get(id);
		pairing.onDeath(label == null ? CarnageKillPairing.UNNAMED : label.name(), nowMs);
	}
}
