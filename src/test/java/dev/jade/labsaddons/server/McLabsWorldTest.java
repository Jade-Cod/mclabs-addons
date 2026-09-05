package dev.jade.labsaddons.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The banner lines are the exact five the server sends. */
public class McLabsWorldTest {
	private static final String ME = "Ophiliah";

	@Test
	public void readsEveryWorldWeBindProfilesTo() {
		assertEquals(McLabsWorld.SPAWN,
				McLabsWorld.from("Welcome to MCLabs Spawn, Ophiliah!", ME));
		assertEquals(McLabsWorld.OVERWORLD,
				McLabsWorld.from("Welcome to the MCLabs Overworld, Ophiliah!", ME));
		assertEquals(McLabsWorld.UNDERWORLD,
				McLabsWorld.from("Welcome to the MCLabs Underworld, Ophiliah!", ME));
		assertEquals(McLabsWorld.EVENTS,
				McLabsWorld.from("Welcome to MCLabs Events, Ophiliah!", ME));
		assertEquals(McLabsWorld.PIT,
				McLabsWorld.from("Welcome to The Pit, Ophiliah!", ME),
				"The Pit's banner omits \"MCLabs\" entirely");
	}

	/** A chat mod (ChatPlus, in these logs) prefixes a timestamp and a [!] marker. */
	@Test
	public void readsTheTimestampedFormOtherChatModsProduce() {
		assertEquals(McLabsWorld.UNDERWORLD,
				McLabsWorld.from("[07:48:27] [!] Welcome to the MCLabs Underworld, Ophiliah!", ME));
		assertEquals(McLabsWorld.SPAWN,
				McLabsWorld.from("[08:29:03] [!] Welcome to MCLabs Spawn, Ophiliah!", ME));
	}

	@Test
	public void ignoresAnotherPlayersJoin() {
		assertNull(McLabsWorld.from("Welcome to MCLabs Spawn, SomeoneElse!", ME));
	}

	@Test
	public void ignoresPlayerChatThatLooksLikeABanner() {
		// Both of these are real lines from the logs.
		assertNull(McLabsWorld.from("[O] [Biochemist] [MVP+] Monster: Welcome to MCLabs", ME));
		assertNull(McLabsWorld.from("[S] [Owner] [SUS] Dakotaa: welcome to the real MCLabs", ME));
	}

	@Test
	public void ignoresAPlayerImpersonatingTheBanner() {
		assertNull(McLabsWorld.from("Someone: Welcome to the MCLabs Underworld, Ophiliah!", ME),
				"a chat prefix carries the sender's name, which the name check alone would not catch");
		assertNull(McLabsWorld.from("[MVP+] Troll: Welcome to The Pit, Ophiliah!", ME));
	}

	@Test
	public void ignoresUnrelatedLines() {
		assertNull(McLabsWorld.from("Fishing Weekend » Sunken treasure found for 50 score!", ME));
		assertNull(McLabsWorld.from("Welcome to MCLabs Spawn", ME), "no name tail");
		assertNull(McLabsWorld.from("", ME));
		assertNull(McLabsWorld.from(null, ME));
		assertNull(McLabsWorld.from("Welcome to MCLabs Spawn, Ophiliah!", null));
	}

	@Test
	public void idsRoundTripAndAreStableConfigKeys() {
		for (McLabsWorld world : McLabsWorld.values()) {
			assertEquals(world, McLabsWorld.byId(world.id()));
			assertNotNull(world.id());
		}
		assertNull(McLabsWorld.byId("nowhere"));
		assertNull(McLabsWorld.byId(null));
	}
}
