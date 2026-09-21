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
	/**
	 * Shorter than a chest board, because there is nothing under the chamber but the reward's
	 * name. What used to sit there — a count, the best rarity in words, a line explaining the
	 * wait — was the picture's own content restated, and five seconds of animation is not long
	 * enough to read any of it.
	 */
	private static final int PANEL_HEIGHT = 128;
	private static final int CHAMBER_Y = 20;
	private static final int CHAMBER_H = 78;
	/** Where the candidates hang. Above centre, to leave the name room underneath. */
	private static final int ARC_Y = CHAMBER_Y + 38;
	private static final int ARC_W = 214;
	/** How far the arc sags in the middle. Enough to read as a curve, not as a bowl. */
	private static final int ARC_SAG = 13;
	private static final int NAME_Y = 106;
	/** The name fades up rather than appearing, so it does not fight the landing flash. */
	private static final long NAME_FADE_MS = 240L;

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
	protected int panelHeight() {
		return PANEL_HEIGHT;
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

		CrateSpin.Column winner = spin.winner();
		CrateRarity shown = winner != null ? winner.rarity() : spin.bestAlive();
		float tightness = tightness();

		// The crate's own name and nothing else. Which rarity is still in play is what the
		// light is for; saying it in words as well only gave the eye somewhere else to go.
		header(context, font, CrateTitles.crateName(title), null, TEXT_DIM);

		CrateChamber.glow(context, CENTRE_X, ARC_Y, shown, tightness);
		CrateChamber.vignette(context, 0, CHAMBER_Y, PANEL_W, CHAMBER_H, tightness * 0.8f);
		candidates(context, now);
		flash(context, now, shown);
		name(context, font, now);
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

	/**
	 * The reward's name, under where it landed, in its own rarity's colour.
	 *
	 * <p>The colour is the only thing naming the rarity now, and that is deliberate: it used to
	 * be printed over a spelled-out rarity as well, which is the same fact twice. Held back
	 * until the screen has landed, so the animation plays out with nothing to read.
	 */
	private void name(DrawContext context, TextRenderer font, long now) {
		CrateSpin.Column winner = spin.winner();
		if (winner == null || spin.phase() != CrateSpin.Phase.LANDED
				|| spin.landedAtMs() == 0L) {
			return;
		}
		float age = Math.clamp((now - spin.landedAtMs()) / (float) NAME_FADE_MS, 0f, 1f);
		CrateChamber.centred(context, font, winner.name(), CENTRE_X, NAME_Y,
				CrateChamber.tint(CrateChamber.colourOf(winner.rarity()),
						Math.round(255 * age)));
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
