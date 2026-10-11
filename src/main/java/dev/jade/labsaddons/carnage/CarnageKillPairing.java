package dev.jade.labsaddons.carnage;

import java.util.ArrayList;
import java.util.List;

/**
 * Which deaths were really our kills. Only the killer is paid Carnage Points, so the
 * {@code +N Carnage Points} actionbar line is the confirmation a death alone can't give: a mob
 * we tagged and someone else finished dies in front of us all the same. Each award takes the
 * death nearest it in time, and a death no award claims is dropped.
 *
 * <p>Nearest both ways: an award only takes a death that has no nearer award of its own, so a
 * stolen kill a second before ours can't take our credit and shift every later one. A named
 * death beats a nameless one, because one kill can leave several: the bigger mobs are a few
 * entities, and the one we hit is often a nameless hitbox removed a beat after the named mob
 * dies. A death more than {@link #DEATH_LEAD_MS} before an award is never that award's (the
 * server sends both in one tick), or the last kill's leftover hitbox would take the next kill.
 *
 * <p>Ported from the Puppies mod's Carnage tracker, where it was measured against the server's
 * own counts.
 */
public final class CarnageKillPairing {
	/** A death with no readable name: it can confirm a kill but credit no mission. */
	public static final String UNNAMED = "";

	/** How long after its award a death may be seen: a removed hitbox goes most of a second later. */
	static final long PAIR_WINDOW_MS = 2_000;
	/** How long before its award a death may be seen. */
	static final long DEATH_LEAD_MS = 300;
	/** How long an award waits for a nearer death before taking one. */
	static final long SETTLE_MS = 500;

	private record Death(String mob, long atMs) {
		boolean isNamed() {
			return !UNNAMED.equals(mob);
		}
	}

	private record Award(long atMs) {
	}

	private final List<Death> deaths = new ArrayList<>();
	private final List<Award> awards = new ArrayList<>();

	public void onDeath(String mob, long nowMs) {
		deaths.add(new Death(mob == null ? UNNAMED : mob, nowMs));
	}

	/** An award, already deduped against the actionbar re-showing the same line. */
	public void onAward(long nowMs) {
		awards.add(new Award(nowMs));
	}

	/** The mobs of the kills settled by now, {@link #UNNAMED} for one nothing named. Call once a tick. */
	public List<String> drain(long nowMs) {
		List<String> kills = new ArrayList<>();
		Award award;
		while ((award = nextPairable(nowMs)) != null) {
			Death death = nearestDeath(award.atMs());
			awards.remove(award);
			deaths.remove(death);
			kills.add(death.mob());
		}
		// paid, but no death of ours was seen: still a kill, of nothing we can name
		awards.removeIf(stale -> nowMs - stale.atMs() >= PAIR_WINDOW_MS);
		deaths.removeIf(death -> nowMs - death.atMs() >= PAIR_WINDOW_MS);
		return kills;
	}

	public void reset() {
		deaths.clear();
		awards.clear();
	}

	private Award nextPairable(long nowMs) {
		for (Award award : awards) {
			if (nowMs - award.atMs() < SETTLE_MS) {
				continue;
			}
			Death death = nearestDeath(award.atMs());
			if (death != null && nearestAward(death.atMs()) == award) {
				return award;
			}
		}
		return null;
	}

	private Death nearestDeath(long awardMs) {
		Death best = null;
		for (Death death : deaths) {
			if (!canPair(death.atMs(), awardMs)) {
				continue;
			}
			if (best == null || death.isNamed() && !best.isNamed()
					|| death.isNamed() == best.isNamed()
							&& Math.abs(death.atMs() - awardMs) < Math.abs(best.atMs() - awardMs)) {
				best = death;
			}
		}
		return best;
	}

	private Award nearestAward(long deathMs) {
		Award best = null;
		for (Award award : awards) {
			if (canPair(deathMs, award.atMs())
					&& (best == null || Math.abs(award.atMs() - deathMs) < Math.abs(best.atMs() - deathMs))) {
				best = award;
			}
		}
		return best;
	}

	private static boolean canPair(long deathMs, long awardMs) {
		return deathMs >= awardMs - DEATH_LEAD_MS && deathMs - awardMs < PAIR_WINDOW_MS;
	}
}
