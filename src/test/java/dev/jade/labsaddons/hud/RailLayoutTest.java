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
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rail as it actually comes out, against the real widgets {@code LabsAddonsClient}
 * registers. {@link HudRailTest} pins the rule; this pins which widget landed in which
 * group, the thing a {@code group()} on the wrong class would spoil.
 *
 * <p><b>Nothing here asserts alphabetical order, deliberately.</b> A plain test JVM loads no
 * language file, so {@code displayName()} comes back as its own translation key and the rail
 * sorts by {@code labsaddons.hud.pit_timer.name} where the game sorts by "The Pit". Pinning
 * the key order here would read as a check on the ordering while testing something else.
 * {@link HudRailTest} covers the comparator with literal names instead.
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

	/** Seventeen widgets come out as eight rows: four on their own, and four groups. */
	@Test
	void theRailFoldsToEightRows() {
		List<HudRail.Row> rows = HudRail.rows(REGISTERED, group -> false);
		assertEquals(8, rows.size());
		assertEquals(Set.of("chemtainer", "progress", "runner_jobs", "ability_cooldowns"),
				rows.stream().filter(row -> !row.isHeader())
						.map(row -> row.widget().id()).collect(Collectors.toSet()));
		assertEquals(Set.of(HudObjects.BOOSTS.getString(), HudObjects.EVENTS.getString(),
						HudObjects.REMINDERS.getString(), HudObjects.GAMBLING.getString()),
				rows.stream().filter(HudRail.Row::isHeader)
						.map(row -> row.group().getString()).collect(Collectors.toSet()));
	}

	/** Every loose widget is above every group, so the actionable rows are all together. */
	@Test
	void theGroupsAllSitBelowTheUngroupedWidgets() {
		List<HudRail.Row> rows = HudRail.rows(REGISTERED, group -> false);
		int firstHeader = rows.size();
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).isHeader()) {
				firstHeader = i;
				break;
			}
		}
		for (int i = firstHeader; i < rows.size(); i++) {
			assertTrue(rows.get(i).isHeader(), "row " + i + " is a widget below a group header");
		}
		assertEquals(4, firstHeader);
	}

	@Test
	void everyWidgetIsStillReachableWithEveryGroupOpen() {
		List<HudRail.Row> rows = HudRail.rows(REGISTERED, group -> true);
		Set<String> shown = rows.stream().filter(row -> !row.isHeader())
				.map(row -> row.widget().id()).collect(Collectors.toSet());
		for (HudObject widget : REGISTERED) {
			assertTrue(shown.contains(widget.id()), widget.id() + " fell out of the rail");
		}
		// Every widget plus a header each for the four groups.
		assertEquals(REGISTERED.size() + 4, rows.size());
	}

	/** Boosts gathers the rates and rentals, though they are registered far apart. */
	@Test
	void boostsGathersEveryRateAndRental() {
		assertEquals(Set.of("chum_timer", "booster_timer", "lab_wars", "rental_mount",
				"personal_boosters"), members(HudObjects.BOOSTS));
	}

	@Test
	void eventsGathersWhatTheServerIsRunning() {
		assertEquals(Set.of("mini_event", "pit_timer", "raid_mine", "bounty"),
				members(HudObjects.EVENTS));
	}

	@Test
	void remindersGathersWhatIsStillOwedToday() {
		assertEquals(Set.of("dailies", "votes"), members(HudObjects.REMINDERS));
	}

	@Test
	void gamblingGathersTheCasinoWidgets() {
		assertEquals(Set.of("coinflips", "coinflip_record"), members(HudObjects.GAMBLING));
	}

	private static Set<String> members(Component group) {
		return HudRail.rows(REGISTERED, g -> false).stream()
				.filter(row -> row.isHeader() && row.group().getString().equals(group.getString()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no header for " + group.getString()))
				.members().stream()
				.map(HudObject::id)
				.collect(Collectors.toSet());
	}
}
