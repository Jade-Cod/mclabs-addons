package dev.jade.labsaddons.casino;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mines states fractions of a dollar, which is why this exists at all — the captured
 * round offered "$3,828.25" and "$10,062.5", and whole-dollar money loses both.
 */
class MoneyTest {
	@Test
	void readsTheFiguresMinesActuallyPrinted() {
		assertEquals(382_825L, Money.parseCents("for $3,828.25"));
		// One decimal place is tenths, not hundredths: $10,062.5 is not $10,062.05.
		assertEquals(1_006_250L, Money.parseCents("star for $10,062.5"));
		assertEquals(1_702_846L, Money.parseCents("star for $17,028.46"));
		assertEquals(612_500L, Money.parseCents("for $6,125"));
	}

	@Test
	void readsWholeFigures() {
		assertEquals(350_000L, Money.parseCents("Investment: $3,500"));
		assertEquals(310_000L, Money.parseCents("BondJoules ($3,100) [16 - 10]"));
		assertEquals(0L, Money.parseCents("Cash Out"));
		assertEquals(0L, Money.parseCents(null));
	}

	@Test
	void showsCentsOnlyWhenThereAreSome() {
		assertEquals("$6,125", Money.format(612_500L));
		assertEquals("$3,828.25", Money.format(382_825L));
		assertEquals("$10,062.50", Money.format(1_006_250L));
		assertEquals("$0", Money.format(0L));
	}

	@Test
	void shortensFiguresThatWouldNotFit() {
		assertEquals("$3,828.25", Money.compact(382_825L));
		assertEquals("$17.0k", Money.compact(1_702_846L));
		assertEquals("$1.5m", Money.compact(Money.fromDollars(1_500_000L)));
	}
}
