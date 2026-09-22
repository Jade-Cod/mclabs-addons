package dev.jade.labsaddons.police;

import dev.jade.labsaddons.mastery.MasteryQuest;
import dev.jade.labsaddons.mastery.MasteryTracker;
import dev.jade.labsaddons.server.McLabsWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

import java.util.Locale;

/**
 * Decides which {@code Patrol} Mastery challenges a confiscation is allowed to
 * credit. Every patrol counts the same contraband; they differ only in where or
 * how it was collected:
 *
 * <ul>
 * <li><b>Patrol</b> — anywhere, so every confiscation counts.</li>
 * <li><b>Boat Patrol</b> / <b>Mount Patrol</b> — the client knows what it is riding.</li>
 * <li><b>Blue/Green/Prison/… Patrol</b> — {@link Neighbourhoods} answers from the
 * player's position, which the boss bar cannot: it shows for a few seconds on entry
 * and never on exit.</li>
 * </ul>
 *
 * <p>A condition that cannot be read resolves to "no", never to "yes": an uncredited
 * bump is corrected by the next {@code /mastery} scrape, an invented one sits on the
 * HUD as a lie until then.
 *
 * <p>Only reached for contraband taken off a player. A bounty chest earns the same police
 * prestige but no patrol — see {@link PoliceContraband} — so "anywhere" above means
 * anywhere you frisked someone, not anywhere you earned progress.
 */
final class PatrolQuests {
	private static final String PATROL = "patrol";
	private static final String BOAT = "boat patrol";
	private static final String MOUNT = "mount patrol";

	private PatrolQuests() {
	}

	/** Advances every eligible active patrol challenge; true if any moved. */
	static boolean advance(double amount) {
		boolean changed = false;
		for (MasteryQuest quest : MasteryTracker.quests()) {
			if (isEligible(quest.name())) {
				changed |= MasteryTracker.advance(quest.name(), amount);
			}
		}
		return changed;
	}

	private static boolean isEligible(String questName) {
		String name = questName.toLowerCase(Locale.ROOT).trim();
		if (name.equals(PATROL)) {
			return true;
		}
		if (!name.endsWith(" " + PATROL)) {
			return false;
		}
		if (name.equals(BOAT)) {
			return vehicle() instanceof AbstractBoat;
		}
		if (name.equals(MOUNT)) {
			// Covers horses, donkeys, mules, llamas and camels — every rideable mount on
			// the server extends this one class.
			return vehicle() instanceof AbstractHorse;
		}
		// "Blue Patrol" -> the "blue" region. Regions only exist at Spawn, and the
		// coordinates repeat in the other worlds, so anywhere else credits nothing.
		return McLabsWorld.current() == McLabsWorld.SPAWN
				&& Neighbourhoods.holdsPlayer(name.substring(0, name.length() - PATROL.length() - 1));
	}

	private static Entity vehicle() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? null : player.getVehicle();
	}
}
