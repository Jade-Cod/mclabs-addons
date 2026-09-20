package dev.jade.labsaddons.hud;

import dev.jade.labsaddons.booster.BoosterHudObject;
import dev.jade.labsaddons.bounty.BountyHudObject;
import dev.jade.labsaddons.chem.ChemtainerHudObject;
import dev.jade.labsaddons.chum.ChumHudObject;
import dev.jade.labsaddons.cooldown.CooldownHudObject;
import dev.jade.labsaddons.coinflip.CfOpenHudObject;
import dev.jade.labsaddons.coinflip.CfRecordHudObject;
import dev.jade.labsaddons.daily.DailyReminderHudObject;
import dev.jade.labsaddons.daily.VoteReminderHudObject;
import dev.jade.labsaddons.event.MiniEventHudObject;
import dev.jade.labsaddons.event.PitHudObject;
import dev.jade.labsaddons.labwars.LabWarsHudObject;
import dev.jade.labsaddons.mount.RentalMountHudObject;
import dev.jade.labsaddons.personal.PersonalBoosterHudObject;
import dev.jade.labsaddons.raidmine.RaidMineHudObject;
import dev.jade.labsaddons.runner.RunnerHudObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rail as it actually comes out, against the real widgets in the order
 * {@code LabsAddonsClient} registers them.
 *
 * <p>{@link HudRailTest} pins the folding rule; this pins the result of applying it to the
 * mod's own widgets, which is the thing a mis-assigned {@code group()} would spoil.
 */
class RailLayoutTest {
	/** Registration order, mirroring LabsAddonsClient. */
	private static final List<HudObject> REGISTERED = List.of(
			new ChumHudObject(),
			new BoosterHudObject(),
			new MiniEventHudObject(),
			new PitHudObject(),
			new RaidMineHudObject(),
			new LabWarsHudObject(),
			new RentalMountHudObject(),
			new PersonalBoosterHudObject(),
			new BountyHudObject(),
			new DailyReminderHudObject(),
			new VoteReminderHudObject(),
			new ChemtainerHudObject(),
			new ProgressHudObject(),
			new RunnerHudObject(),
			new CooldownHudObject(),
			new CfOpenHudObject(),
			new CfRecordHudObject());

	private static List<String> keys(List<HudRail.Row> rows) {
		return rows.stream()
				.map(row -> row.isHeader() ? "[" + row.group().getString() + "]" : row.widget().id())
				.toList();
	}

	/**
	 * Every group shut. Seventeen widgets come out as eight rows, and each group's header
	 * stands where its first widget was registered rather than at the end of the list.
	 */
	@Test
	void theRailFoldsToOneRowPerGroup() {
		assertEquals(List.of(
						"[labsaddons.hud.group.boosts]",
						"[labsaddons.hud.group.events]",
						"[labsaddons.hud.group.reminders]",
						"chemtainer",
						"progress",
						"runner_jobs",
						"ability_cooldowns",
						"[labsaddons.hud.group.gambling]"),
				keys(HudRail.rows(REGISTERED, group -> false)));
	}

	@Test
	void everyWidgetIsStillReachableWithEveryGroupOpen() {
		List<String> open = keys(HudRail.rows(REGISTERED, group -> true));
		for (HudObject widget : REGISTERED) {
			assertTrue(open.contains(widget.id()), widget.id() + " fell out of the rail");
		}
		// Every widget plus a header each for the four groups.
		assertEquals(REGISTERED.size() + 4, open.size());
	}

	/** Boosts gathers the rates and rentals, even though they are registered apart. */
	@Test
	void boostsGathersEveryRateAndRental() {
		assertEquals(
				List.of("chum_timer", "booster_timer", "lab_wars", "rental_mount",
						"personal_boosters"),
				members(HudObjects.BOOSTS.getString()));
	}

	@Test
	void eventsGathersWhatTheServerIsRunning() {
		assertEquals(List.of("mini_event", "pit_timer", "raid_mine", "bounty"),
				members(HudObjects.EVENTS.getString()));
	}

	@Test
	void remindersGathersWhatIsStillOwedToday() {
		assertEquals(List.of("dailies", "votes"), members(HudObjects.REMINDERS.getString()));
	}

	@Test
	void gamblingGathersTheCasinoWidgets() {
		assertEquals(List.of("coinflips", "coinflip_record"),
				members(HudObjects.GAMBLING.getString()));
	}

	private static List<String> members(String group) {
		return HudRail.rows(REGISTERED, g -> false).stream()
				.filter(row -> row.isHeader() && row.group().getString().equals(group))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no header for " + group))
				.members().stream()
				.map(HudObject::id)
				.toList();
	}
}
