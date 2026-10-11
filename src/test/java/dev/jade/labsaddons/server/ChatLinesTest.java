package dev.jade.labsaddons.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lines copied from the 2026-10 captures. */
class ChatLinesTest {
	@Test
	void playerChatIsCaught() {
		for (String line : List.of(
				"[S] [⚛ Curie ⚛] [☠ Pit Boss ☠] fayevil: the devil",
				"[A] [I] [Chemist] [LWVII Vet.] Castlenite: anyone want to buy",
				"[S] [Admin] aubslutster: you know",
				"[S] [✦ Director ✦] [845 Keys] _MikeHunt: 100m cfup",
				"[S] [VIP] bob: Vote registered! Claim your vote rewards with /claimvotes",
				"Someone: Double fish time remaining: 999h",
				"\nAdvert by Nothing_but_fail:\nNoob items on /cchest\n")) {
			assertTrue(ChatLines.isPlayerChat(line), line);
		}
	}

	@Test
	void serverLinesAreNot() {
		for (String line : List.of(
				"\nCoinflip » You have lost the $750 coinflip against GonjaMayor.\n",
				"MCLabs » $750 has been taken from your account.",
				"Lab Wars » Revenue Boost Event: The revenue rate of Fishing has been boosted by 1.25x for the next 1 hour.",
				"» Booster activated! All Chems boosted 1.5x by Ophiliah for 20 minutes",
				"Mini-event ends in 12:14",
				"Welcome to MCLabs Spawn, Ophiliah!",
				"[07:48:27] [!] Welcome to The Pit, Ophiliah!",
				"**SUPER BREAKER ACTIVATED**",
				"  Super Breaker - 45 seconds left",
				"[⚡ Your Exceedingly Rare odds have been JACKED-UP! ⚡]",
				"│ Coinflips Played: 283",
				"♣ GCL » Ophiliah purchased 1 of double fish")) {
			assertFalse(ChatLines.isPlayerChat(line), line);
		}
	}
}
