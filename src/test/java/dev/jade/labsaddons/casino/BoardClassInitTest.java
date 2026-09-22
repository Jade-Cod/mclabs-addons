package dev.jade.labsaddons.casino;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Forces every board's class initialiser to run.
 *
 * <p>Every board is a singleton whose {@code INSTANCE} is declared first, so its constructor
 * runs before any static field below it has been assigned. An {@code int} constant is fine
 * there — the compiler inlines it — but an array, a string or anything else is still null, and
 * a field initialiser that reads one throws {@code ExceptionInInitializerError}.
 *
 * <p>That is not a quiet failure. {@link CasinoBoards} is touched from the tooltip hook on
 * every container screen, so one bad board takes the player's own inventory down with it. It
 * shipped exactly once, from {@code new String[REELS.length]} in the voter board, and no test
 * loaded these classes to notice.
 *
 * <p>Deliberately no assertions about behaviour: reaching the end of class-init without
 * throwing is the whole test.
 */
class BoardClassInitTest {
	@Test
	void everyBoardInitialisesWithoutThrowing() {
		assertDoesNotThrow(() -> {
			// Going through CasinoBoards is what the game does, and it initialises all of them
			// as its own static list is built — so this covers any board added later for free.
			assertNotNull(CasinoBoards.class.getDeclaredFields());
			Class.forName(CasinoBoards.class.getName(), true,
					CasinoBoards.class.getClassLoader());
		});
	}
}
