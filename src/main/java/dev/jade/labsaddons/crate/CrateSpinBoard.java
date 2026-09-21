package dev.jade.labsaddons.crate;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.hud.editor.EditorTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The crate opening, drawn as a chamber the candidates are vented out of.
 *
 * <p>The server's own screen is three rows of nine stained glass panes that go grey one at a
 * time. Everything a player wants to know is already in it — each column's pane is coloured
 * for its candidate's rarity — but reading it means matching nine colours by eye while they
 * disappear. This draws the same eight eliminations as light: each candidate carries its own
 * rarity's glow, and they all brighten as the field narrows — so a field that still holds an
 * Exceedingly Rare has an orange light in it, and the moment that one dies you watch it go
 * out while everything left gets brighter.
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

	/** The pity window that hangs beside the panel; see {@link #pity}. */
	/**
	 * Wide enough for "Exceedingly Rare" spelled out (87 pixels) and for the line that asks you
	 * to open the odds menu (96), with the padding either side. Narrower and the longest rarity
	 * in the game is the one that gets trimmed.
	 */
	private static final int PITY_W = 112;
	private static final int PITY_GAP = 6;
	/** Two lines a rarity: its name, then its chance and which roll that is. */
	private static final int PITY_ROW_H = 21;
	private static final int PITY_TOP = HEADER_H + 6;
	private static final int PITY_LINE = 10;
	/** What a rarity the odds menu has never been opened for shows instead of a figure. */
	private static final String PITY_UNKNOWN = "—";

	/** How long a vented candidate takes to fade and fall out of the chamber. */
	private static final long VENT_MS = 340L;
	/** How far it falls while it does. */
	private static final int VENT_DROP = 26;
	/** How long the winner takes to swell once it is the only thing left. */
	private static final long SWELL_MS = 300L;
	private static final float WINNER_SCALE = 1.75f;
	/** The tint the whole chamber takes for a moment as the screen turns the winner's colour. */
	private static final long FLASH_MS = 180L;
	private static final int FLASH_ALPHA = 60;
	/**
	 * How far a candidate's glow reaches past its own box. Kept under one so the winner's,
	 * which is nearly twice the size, still sits inside the chamber.
	 */
	private static final float GLOW_SPREAD = 0.9f;
	/** The dimmest a candidate glows, with all nine still up. */
	private static final float GLOW_FLOOR = 0.45f;
	/** A short brightening as the winner lands, on top of everything else. */
	private static final long PULSE_MS = 260L;
	private static final float PULSE_LIFT = 0.5f;
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
	/** Whether this spin's result has already moved the pity counters. One roll, one count. */
	private boolean counted;

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
		counted = false;
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
		float tightness = tightness();
		count(winner, now);

		// The crate's own name and nothing else. Which rarity is still in play is what the
		// light is for; saying it in words as well only gave the eye somewhere else to go.
		header(context, font, CrateTitles.crateName(title), null, TEXT_DIM);

		// The light belongs to the candidates, not to the middle of the panel: with two left at
		// opposite ends of the arc, a glow in the centre was lighting nothing at all.
		CrateChamber.vignette(context, 0, CHAMBER_Y, PANEL_W, CHAMBER_H, tightness * 0.8f);
		candidates(context, now);
		flash(context, now, winner == null ? null : winner.rarity());
		name(context, font, now);
		pity(context, font);
		hoveredTooltip(context, font, now);
	}

	// --- the pity ladder -----------------------------------------------------

	/**
	 * Counts this spin against the pity ladder, once, the moment it lands.
	 *
	 * <p>Done here rather than off the unbox chat line because the chat line never names the
	 * rarity and the board already knows it: the winner's own pane said so. The server's
	 * odds-went-up line arrives about half a second later and is suppressed by
	 * {@link CratePity#counted}, so the same roll is not counted twice.
	 */
	private void count(CrateSpin.Column winner, long now) {
		if (counted || winner == null || winner.rarity() == null
				|| spin.phase() != CrateSpin.Phase.LANDED) {
			return;
		}
		counted = true;
		CratePity.counted(now);
		LabsAddonsConfig config = LabsAddonsConfig.get();
		Map<String, Integer> rolled = CratePity.rolled(config.cratePityRolls, winner.rarity());
		if (rolled != null) {
			config.cratePityRolls = rolled;
			config.save();
		}
	}

	/**
	 * The three rarities worth waiting for, in a window hanging off the panel's left edge:
	 * what each one's chance is on the roll about to happen, and which roll that is.
	 *
	 * <p>Beside the panel rather than inside it because the chamber uses its whole width, and
	 * because the numbers are not part of the spin — they are true before it starts and after it
	 * ends. Dropped entirely when the window is too narrow to hold it, which is the same
	 * condition under which the panel itself has already been scaled down.
	 *
	 * <p>"Dry" is the roll about to happen, which is the same thing as how long it has been
	 * since that rarity last landed — the server's own numbering, read exactly off its odds
	 * menu rather than estimated. See {@link CratePity#currentRoll}.
	 */
	private void pity(DrawContext context, TextRenderer font) {
		if (roomLeft() < PITY_W + PITY_GAP + 2) {
			return;
		}
		Map<String, Integer> rolls = LabsAddonsConfig.get().cratePityRolls;
		boolean anyUnknown = false;
		for (CrateRarity rarity : CratePity.TRACKED) {
			if (CratePity.roll(rolls, rarity) <= 0) {
				anyUnknown = true;
			}
		}
		int x = -(PITY_W + PITY_GAP);
		int height = PITY_TOP + CratePity.TRACKED.size() * PITY_ROW_H + 2
				+ (anyUnknown ? PITY_LINE + 2 : 0);

		HudObject.drawRoundedRect(context, x, 0, PITY_W, height, EditorTheme.PANEL_BG);
		EditorPainter.outline(context, x, 0, PITY_W, height, EditorTheme.PANEL_BORDER);
		context.drawText(font, "YOUR ODDS", x + PAD, 5, TEXT_DIM, false);
		context.fill(x + PAD, HEADER_H, x + PITY_W - PAD, HEADER_H + 1, DIVIDER);

		int y = PITY_TOP;
		int width = PITY_W - PAD * 2;
		for (CrateRarity rarity : CratePity.TRACKED) {
			int roll = CratePity.roll(rolls, rarity);
			// Trimmed rather than trusted to fit: the widths above are measured, but a resource
			// pack's font is not the one they were measured in.
			context.drawText(font, font.trimToWidth(rarity.label(), width), x + PAD, y,
					rarity.color(), false);
			if (roll <= 0) {
				context.drawText(font, PITY_UNKNOWN, x + PAD, y + PITY_LINE, TEXT_FAINT, false);
			} else {
				row(context, font, x + PAD, y + PITY_LINE, width,
						CratePity.format(CratePity.chance(rarity, roll)), "Dry: " + roll,
						TEXT, TEXT_DIM);
			}
			y += PITY_ROW_H;
		}
		if (anyUnknown) {
			caption(context, font, x + PAD, y,
					font.trimToWidth("open its odds once", width));
		}
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

	/**
	 * How narrow the field has become: 0 until the first candidate dies, 1 once one is left.
	 *
	 * <p>Counted off the eliminations rather than off how many are alive. Alive starts at nought
	 * too — for the second before the first candidate lands — so reading it that way had the
	 * chamber at its very darkest over an empty screen, then brightening as the nine arrived.
	 * Exactly backwards, and the first thing a player sees.
	 */
	private float tightness() {
		return spin.culls() / (float) CrateSpin.TOTAL_CULLS;
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

		// As the field narrows the survivors brighten, so the same light says both what is
		// still in play and how little of it is left.
		float ambient = GLOW_FLOOR + (1f - GLOW_FLOOR) * tightness();

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
			float fade = 1f - age;
			candidate(context, column, atX[column], atY[column] + VENT_DROP * age,
					1f - age * 0.4f, fade, ambient * fade);
		}

		boolean winning = spin.winner() != null;
		for (int column : alive) {
			float lift = winning && column == spin.winnerColumn() ? pulse(now) : 0f;
			candidate(context, column, atX[column], atY[column], scaleOf(column, now), 1f,
					ambient + lift);
		}
	}

	/** How big a live candidate is drawn: its own size, or the winner's as it swells. */
	private float scaleOf(int column, long now) {
		if (spin.winner() == null || column != spin.winnerColumn()) {
			return 1f;
		}
		float grown = Math.clamp(
				(now - spin.column(column).changedAtMs()) / (float) SWELL_MS, 0f, 1f);
		return 1f + (WINNER_SCALE - 1f) * grown;
	}

	/**
	 * The hovered candidate's own tooltip, exactly as the chest menu this replaces would have
	 * shown it — the server's stack handed straight to the game, with nothing added.
	 *
	 * <p>Live candidates only. One the server has already killed is fading out of the chamber,
	 * and a tooltip standing over it would outlast the thing it describes.
	 */
	private void hoveredTooltip(DrawContext context, TextRenderer font, long now) {
		for (int column = 0; column < CrateSpin.COLUMNS; column++) {
			if (!spin.column(column).alive()) {
				continue;
			}
			int box = Math.round(CrateChamber.MOTE * scaleOf(column, now));
			if (hovered(Math.round(atX[column] - box / 2f), Math.round(atY[column] - box / 2f),
					box, box)) {
				tooltip(context, font, held[column]);
				return;
			}
		}
	}

	/** One candidate: its own light, then the server's own icon on top of it. */
	private void candidate(DrawContext context, int column, float x, float y, float scale,
			float alpha, float glow) {
		CrateSpin.Column state = spin.column(column);
		int colour = CrateChamber.colourOf(state.rarity());
		CrateChamber.glow(context, x, y, CrateChamber.MOTE * scale * GLOW_SPREAD, colour, glow);
		CrateChamber.mote(context, held[column], colour, x, y, scale, alpha);
	}

	/** The winner's light swells for a moment as it lands, then settles back. */
	private float pulse(long now) {
		if (spin.landedAtMs() == 0L) {
			return 0f;
		}
		float age = (now - spin.landedAtMs()) / (float) PULSE_MS;
		return age < 0f || age >= 1f ? 0f : PULSE_LIFT * (1f - age);
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
				CrateChamber.tint(won.color(), Math.round(FLASH_ALPHA * (1f - age))));
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
