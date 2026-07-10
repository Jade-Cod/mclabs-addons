package dev.jade.labsaddons.runner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RunnerMessagesTest {
	@Test
	public void parsesPosted() {
		RunnerMessages.Event e = RunnerMessages.parse(
				"MCLabs » Runner job posted: 2,176x Papwartinide-2-2-2 at 100%.");
		assertNotNull(e);
		assertEquals(RunnerMessages.Type.POSTED, e.type());
	}

	@Test
	public void parsesTakenWithRunnerDrugQty() {
		RunnerMessages.Event e = RunnerMessages.parse(
				"Runner » FantasyTagz has taken your runner job (Papwartinide-2-2-2 x2176)");
		assertNotNull(e);
		assertEquals(RunnerMessages.Type.TAKEN, e.type());
		assertEquals("FantasyTagz", e.runner());
		assertEquals("Papwartinide-2-2-2", e.drug());
		assertEquals(2176, e.qty());
	}

	@Test
	public void parsesCompletedWithRunnerAndValue() {
		RunnerMessages.Event e = RunnerMessages.parse(
				"Runner » FantasyTagz has completed your runner job, you've earned $2.1m.");
		assertNotNull(e);
		assertEquals(RunnerMessages.Type.COMPLETED, e.type());
		assertEquals("FantasyTagz", e.runner());
		assertEquals(2_100_000.0, e.value(), 1e-6);
	}

	@Test
	public void parsesCompletedZeroPayout() {
		RunnerMessages.Event e = RunnerMessages.parse(
				"Runner » FantasyTagz has completed your runner job, you've earned $0.");
		assertNotNull(e);
		assertEquals(RunnerMessages.Type.COMPLETED, e.type());
		assertEquals(0.0, e.value(), 1e-9);
	}

	@Test
	public void parsesFailedWithRunnerDrugQty() {
		RunnerMessages.Event e = RunnerMessages.parse(
				"Runner » Steve failed your runner job! (Papwartinide-2-2-2 x2,176)");
		assertNotNull(e);
		assertEquals(RunnerMessages.Type.FAILED, e.type());
		assertEquals("Steve", e.runner());
		assertEquals("Papwartinide-2-2-2", e.drug());
		assertEquals(2176, e.qty());
	}

	@Test
	public void ignoresRunnerDutyAndUnrelatedLines() {
		assertNull(RunnerMessages.parse(
				"Runner » Runner job started! You have 5 minutes to sell the chems to any dealer."));
		assertNull(RunnerMessages.parse("Runner » 5 new jobs posted!"));
		assertNull(RunnerMessages.parse("Runner » New job posted! (Chorumpkinate-3-3-3 x2,176 [0%/100%, $0])"));
		assertNull(RunnerMessages.parse("Steve: hello there"));
	}
}
