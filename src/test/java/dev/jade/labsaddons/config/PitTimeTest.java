package dev.jade.labsaddons.config;

import dev.jade.labsaddons.event.PitTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pit week adds 25% to a book pop, so sponsor lines arrive with a fraction
 * ("37.5 minutes"). Both the sentence-end match and the duration parser have to
 * survive the decimal point.
 */
public class PitTimeTest {
	/** Chat and the clock never line up to the millisecond. */
	private static final long SLACK_MS = 2_000L;

	@TempDir
	Path configDir;

	@BeforeEach
	public void useTempConfig() {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		PitTracker.clear();
	}

	private static void assertRemaining(double expectedMinutes) {
		long expected = Math.round(expectedMinutes * 60_000.0);
		long actual = PitTracker.remainingMs();
		assertTrue(Math.abs(actual - expected) < SLACK_MS,
				"expected ~" + expected + "ms, got " + actual + "ms");
	}

	@Test
	public void fractionalSponsorsKeepTheirFraction() {
		PitTracker.onMessage("The Pit » AtomicBomb has sponsored The Pit for 37.5 minutes! Join with /pit");
		assertRemaining(37.5);

		PitTracker.clear();
		PitTracker.onMessage("The Pit » AtomicBomb has sponsored The Pit for 18.75 minutes! Join with /pit");
		assertRemaining(18.75);
	}

	@Test
	public void wholeMinuteSponsorsStillWork() {
		PitTracker.onMessage("The Pit » MC_Labs has sponsored The Pit for 25 minutes! Join with /pit");
		assertRemaining(25);
	}

	@Test
	public void extendMessagesUseTheOpenForAnotherFigure() {
		PitTracker.onMessage("The Pit » Dakotaa has extended the Pit time by 75 minutes! "
				+ "The Pit will be open for another 224 minutes. Join with /pit");
		assertRemaining(224);
	}

	@Test
	public void aSmallerSponsorDoesNotShortenTheWindow() {
		PitTracker.onMessage("The Pit » AtomicBomb has sponsored The Pit for 37.5 minutes! Join with /pit");
		PitTracker.onMessage("The Pit » MC_Labs has sponsored The Pit for 25 minutes! Join with /pit");
		assertRemaining(37.5);
	}
}
