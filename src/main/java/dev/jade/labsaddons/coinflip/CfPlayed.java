package dev.jade.labsaddons.coinflip;

/**
 * One of your own flips, as the lobby's recent list shows it.
 *
 * <p>A plain class with public fields rather than a record, because this is what Gson
 * writes into {@code state.json} — the same shape every other persisted setting in this
 * config uses. It survives a restart so the list is still there when you come back, which
 * an empty one would read as broken.
 */
public class CfPlayed {
	public boolean won;
	/** What the flip did to your balance: the profit on a win, the whole stake on a loss. */
	public long netCents;
	public String opponent = "";

	public CfPlayed() {
	}

	public CfPlayed(boolean won, long netCents, String opponent) {
		this.won = won;
		this.netCents = netCents;
		this.opponent = opponent == null ? "" : opponent;
	}
}
