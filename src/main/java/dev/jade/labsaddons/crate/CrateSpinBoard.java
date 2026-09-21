package dev.jade.labsaddons.crate;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The crate opening, drawn as a chamber the candidates are vented out of.
 *
 * <p>The server's own screen is three rows of nine stained glass panes that go grey one at a
 * time. Everything a player wants to know is already in it — each column's pane is coloured
 * for its candidate's rarity — but reading it means matching nine colours by eye while they
 * disappear. This draws the same eight eliminations as one light: the chamber glows whatever
 * the <em>best rarity still in play</em> is, so a field that still holds an Exceedingly Rare
 * is orange, and the moment that one dies you watch it fall.
 *
 * <p>It runs entirely on the server's clock. The winner is not in any packet until the eighth
 * cull lands, around five seconds in, so there is nothing to reveal early and nothing to
 * spoil — the only thing predicted is <em>when</em> the next cull is due, from the ramp the
 * server keeps to (the k-th gap is about {@code 100·k} ms), and that only draws a bar.
 *
 * @see CrateSpin for the beats, and for why the winner walking to the centre is the one part
 *      that needs care
 */
public final class CrateSpinBoard extends CasinoPanel {
	public static final CrateSpinBoard INSTANCE = new CrateSpinBoard();

	/** A crate menu is a single chest: three rows, twenty-seven slots. */
	private static final int CRATE_SLOTS = 27;
	/** The row holding the candidates; the rows either side are their rarity panes. */
	private static final int CANDIDATE_ROW = 9;

	private static final int CENTRE_X = PANEL_W / 2;
	private static final int CHAMBER_Y = 21;
	private static final int CHAMBER_H = 92;
	/** Where the candidates hang. Above centre, to leave the name room underneath. */
	private static final int ARC_Y = CHAMBER_Y + 38;
	private static final int ARC_W = 214;
	/** How far the arc sags in the middle. Enough to read as a curve, not as a bowl. */
	private static final int ARC_SAG = 13;

	private static final int TENSION_Y = CHAMBER_Y + CHAMBER_H + 4;
	private static final int TENSION_H = 2;
	private static final int LADDER_Y = TENSION_Y + 9;
	private static final int STATUS_Y = LADDER_Y + 1;
	private static final int NAME_Y = LADDER_Y + 16;
	private static final int SUB_Y = NAME_Y + 12;

	/** How long a vented candidate takes to fade and fall out of the chamber. */
	private static final long VENT_MS = 340L;
	/** How far it falls while it does. */
	private static final int VENT_DROP = 26;
	/** How long the winner takes to swell once it is the only thing left. */
	private static final long SWELL_MS = 300L;
	private static final float WINNER_SCALE = 1.75f;
	/** The white-out on the landing frame. */
	private static final long FLASH_MS = 220L;
	/**
	 * Smoothing constant for candidates sliding to their new places as the field narrows.
	 * Applied against real elapsed time, so the glide is the same at any frame rate.
	 */
	private static final float GLIDE_TAU_MS = 80f;

	private CrateSpin spin = new CrateSpin();
	/** Where each candidate was last drawn, so a vented one falls from where it was. */
	private final float[] atX = new float[CrateSpin.COLUMNS];
	private final float[] atY = new float[CrateSpin.COLUMNS];
	/** The stack each column held, kept so a vented candidate keeps its icon on the way out. */
	private final ItemStack[] held = new ItemStack[CrateSpin.COLUMNS];
	private boolean placed;
	private long lastFrameMs;
	/** Which column the winner was last drawn in, so its position survives the walk. */
	private int drawnWinner = -1;

	private CrateSpinBoard() {
	}

	@Override
	protected boolean enabled() {
		return LabsAddonsConfig.get().crateOverlay;
	}

	@Override
	protected int containerSlots() {
		return CRATE_SLOTS;
	}

	/**
	 * Cheap enough to run against every container in the game: one slot, and only whether it
	 * is a pane. The title is what actually claims the menu — see {@link #titleAllows}.
	 */
	@Override
	protected boolean looksLike(ScreenHandler handler) {
		return isPane(stackOf(handler, 0));
	}

	/** "Opening a Supply Crate II..." — unmistakable, and the same shape on all ten crates. */
	@Override
	protected boolean titleAllows(String title) {
		return CrateTitles.isSpin(title);
	}


	/** Slot names cannot tell a crate spin from any other chest full of panes; the title can. */
	@Override
	protected boolean parses(List<SlotView> slots) {
		return false;
	}

	/**
	 * Every slot of a spin holds something for its whole run — a pane or a candidate — so an
	 * empty one means this is a chest that merely shares the title's shape.
	 */
	@Override
	protected boolean parses(List<SlotView> slots, String title) {
		if (!CrateTitles.isSpin(title) || slots.size() < CRATE_SLOTS) {
			return false;
		}
		for (int i = 0; i < CRATE_SLOTS; i++) {
			if (slots.get(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected void onContainerChange() {
		spin = new CrateSpin();
		placed = false;
		lastFrameMs = 0L;
		drawnWinner = -1;
		for (int i = 0; i < CrateSpin.COLUMNS; i++) {
			held[i] = null;
		}
	}

	@Override
	protected void draw(DrawContext context, TextRenderer font, List<SlotView> slots,
			String title, float deviceScale) {
		long now = Util.getMeasuringTimeMs();
		spin.observe(read(), now);

		CrateRarity best = spin.bestAlive();
		CrateSpin.Column winner = spin.winner();
		CrateRarity shown = winner != null ? winner.rarity() : best;
		float tightness = tightness();

		header(context, font, "CRATE", CrateTitles.crateName(title), TEXT_DIM);

		CrateChamber.glow(context, CENTRE_X, ARC_Y, shown, tightness);
		CrateChamber.vignette(context, 0, CHAMBER_Y, PANEL_W, CHAMBER_H, tightness * 0.8f);
		candidates(context, now);
		flash(context, now, shown);

		CrateChamber.tension(context, PAD, TENSION_Y, PANEL_W - PAD * 2, TENSION_H,
				spin.tension(now), shown == null ? accent() : shown.color());
		CrateChamber.ladder(context, PAD, LADDER_Y, spin);
		status(context, font, best);
		reward(context, font, winner);
	}

	// --- reading the container ----------------------------------------------

	/** One cell per column: whether it still holds a candidate, and what its pane says. */
	private List<CrateSpin.Cell> read() {
		List<CrateSpin.Cell> cells = new ArrayList<>(CrateSpin.COLUMNS);
		for (int column = 0; column < CrateSpin.COLUMNS; column++) {
			ItemStack candidate = stackAt(CANDIDATE_ROW + column);
			boolean alive = !candidate.isEmpty() && !isPane(candidate);
			if (alive) {
				held[column] = candidate;
			}
			cells.add(new CrateSpin.Cell(alive, CrateRarity.fromPane(path(stackAt(column))),
					alive ? candidate.getName().getString() : ""));
		}
		return cells;
	}

	/** How narrow the field has become, 0 with all nine up and 1 with one left. */
	private float tightness() {
		int alive = Math.max(1, spin.aliveCount());
		return 1f - (alive - 1f) / (CrateSpin.COLUMNS - 1f);
	}

	// --- the chamber ---------------------------------------------------------

	private void candidates(DrawContext context, long now) {
		List<Integer> alive = new ArrayList<>(CrateSpin.COLUMNS);
		for (int column = 0; column < CrateSpin.COLUMNS; column++) {
			if (spin.column(column).alive()) {
				alive.add(column);
			}
		}
		carryWinner();
		glide(alive, now);

		// The vented ones first, so a survivor is never drawn under something falling away.
		for (int column = 0; column < CrateSpin.COLUMNS; column++) {
			CrateSpin.Column state = spin.column(column);
			if (state.state() != CrateSpin.ColumnState.CULLED) {
				continue;
			}
			float age = (now - state.changedAtMs()) / (float) VENT_MS;
			if (age >= 1f || age < 0f) {
				continue;
			}
			CrateChamber.mote(context, held[column], CrateChamber.colourOf(state.rarity()),
					atX[column], atY[column] + VENT_DROP * age, 1f - age * 0.4f, 1f - age);
		}

		boolean swelling = spin.winner() != null;
		for (int column : alive) {
			CrateSpin.Column state = spin.column(column);
			float scale = 1f;
			if (swelling && column == spin.winnerColumn()) {
				float grown = Math.clamp((now - state.changedAtMs()) / (float) SWELL_MS, 0f, 1f);
				scale = 1f + (WINNER_SCALE - 1f) * grown;
			}
			CrateChamber.mote(context, held[column], CrateChamber.colourOf(state.rarity()),
					atX[column], atY[column], scale, 1f);
		}
	}

	/**
	 * Moves the winner's drawn position with it when the server walks it into the next column.
	 *
	 * <p>Without this the reward snaps back to wherever that column sat in the original arc
	 * before gliding on — four times over, at the most conspicuous moment of the spin. The
	 * column is only an index the server happens to be using; the thing on screen is one
	 * object and has to move like one.
	 */
	private void carryWinner() {
		int now = spin.winnerColumn();
		if (now < 0) {
			return;
		}
		if (drawnWinner >= 0 && drawnWinner != now) {
			atX[now] = atX[drawnWinner];
			atY[now] = atY[drawnWinner];
		}
		drawnWinner = now;
	}

	/**
	 * Eases each surviving candidate toward its place in the arc. Exponential smoothing on
	 * real elapsed time rather than a per-frame fraction, so the glide does not run at twice
	 * the speed on a 120Hz monitor.
	 */
	private void glide(List<Integer> alive, long now) {
		float blend = 1f;
		if (placed && lastFrameMs > 0L) {
			long delta = Math.clamp(now - lastFrameMs, 0L, 250L);
			blend = 1f - (float) Math.exp(-delta / GLIDE_TAU_MS);
		}
		lastFrameMs = now;
		int count = alive.size();
		for (int i = 0; i < count; i++) {
			int column = alive.get(i);
			float targetX = count == 1
					? CENTRE_X
					: CENTRE_X - ARC_W / 2f + ARC_W * i / (count - 1f);
			float targetY = count == 1
					? ARC_Y
					: ARC_Y + ARC_SAG * (float) Math.sin(Math.PI * i / (count - 1f));
			if (!placed) {
				atX[column] = targetX;
				atY[column] = targetY;
			} else {
				atX[column] += (targetX - atX[column]) * blend;
				atY[column] += (targetY - atY[column]) * blend;
			}
		}
		if (count > 0) {
			placed = true;
		}
	}

	/** One white frame as the screen turns the winner's colour, then gone. */
	private void flash(DrawContext context, long now, CrateRarity won) {
		if (spin.phase() != CrateSpin.Phase.LANDED || won == null || spin.landedAtMs() == 0L) {
			return;
		}
		float age = (now - spin.landedAtMs()) / (float) FLASH_MS;
		if (age < 0f || age >= 1f) {
			return;
		}
		context.fill(0, CHAMBER_Y, PANEL_W, CHAMBER_Y + CHAMBER_H,
				CrateChamber.tint(won.color(), Math.round(120 * (1f - age))));
	}

	// --- the readout ---------------------------------------------------------

	private void status(DrawContext context, TextRenderer font, CrateRarity best) {
		int left = PAD + CrateChamber.ladderWidth() + 8;
		String text;
		int colour;
		switch (spin.phase()) {
			case FILLING -> {
				text = "loading the chamber";
				colour = TEXT_FAINT;
			}
			case HOLDING -> {
				text = CrateSpin.COLUMNS + " in play";
				colour = TEXT_DIM;
			}
			case CULLING -> {
				text = spin.aliveCount() + " left";
				colour = TEXT;
			}
			case SETTLING -> {
				text = "locking in";
				colour = accent();
			}
			case LANDED -> {
				text = "unboxed";
				colour = TEXT_DIM;
			}
			default -> {
				text = "";
				colour = TEXT_FAINT;
			}
		}
		context.drawText(font, text, left, STATUS_Y, colour, false);

		// The reading the vanilla screen makes you take off nine pane colours by eye.
		if (best != null && spin.phase() != CrateSpin.Phase.LANDED) {
			String label = "best " + best.label().toLowerCase(Locale.ROOT);
			context.drawText(font, label, PANEL_W - PAD - font.getWidth(label), STATUS_Y,
					best.color(), false);
		}
	}

	private void reward(DrawContext context, TextRenderer font, CrateSpin.Column winner) {
		if (winner == null) {
			CrateChamber.centred(context, font,
					"the winner is not on the wire until the last cull", CENTRE_X, SUB_Y,
					TEXT_FAINT);
			return;
		}
		CrateRarity rarity = winner.rarity();
		CrateChamber.centred(context, font, winner.name(), CENTRE_X, NAME_Y,
				rarity == null ? TEXT : rarity.color());
		String sub = rarity == null ? "" : rarity.label().toLowerCase(Locale.ROOT);
		if (rarity != null && rarity.isPitied()) {
			// Only Rare and above carry the server's pity, and it resets on a win.
			sub += " · pity reset";
		}
		CrateChamber.centred(context, font, sub, CENTRE_X, SUB_Y, TEXT_FAINT);
	}

	// --- cheap stack tests ---------------------------------------------------

	private static ItemStack stackOf(ScreenHandler handler, int slot) {
		if (slot < 0 || slot >= handler.slots.size()) {
			return ItemStack.EMPTY;
		}
		return handler.slots.get(slot).getStack();
	}

	/** A filler or a rarity frame rather than a candidate. */
	private static boolean isPane(ItemStack stack) {
		return !stack.isEmpty() && path(stack).endsWith("stained_glass_pane");
	}

	/** The registry path, so nothing here depends on the client's language. */
	private static String path(ItemStack stack) {
		if (stack.isEmpty()) {
			return "";
		}
		return Registries.ITEM.getId(stack.getItem()).getPath();
	}
}
