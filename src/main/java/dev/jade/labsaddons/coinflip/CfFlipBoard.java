package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.PlayerSkinCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Locale;

/**
 * The toss: one coin, both faces real, and the money it is choosing between named before it
 * lands.
 *
 * <p>The server's screen is forty-four grey panes and a head that changes name. This draws
 * the coin that head is standing in for — see {@link CfToss} for why it runs on its own
 * clock and keeps the last half-turn back.
 *
 * <p>The wager is not on this screen at all, so it comes from {@link CfChat}: the row you
 * clicked in the lobby, or failing that the debit line that landed a moment before. With
 * neither, the board says so rather than showing a figure it guessed.
 */
public final class CfFlipBoard extends CasinoPanel {
	public static final CfFlipBoard INSTANCE = new CfFlipBoard();

	/** Coinflip's menus are forty-five slots, not a chest's fifty-four. */
	private static final int COINFLIP_SLOTS = 45;

	private static final int SEAT_HEAD = 18;
	private static final int SEAT_TEXT_GAP = 22;
	private static final int SEAT_Y = 52;
	private static final int COIN_CX = PANEL_W / 2;
	private static final int COIN_BASE_CY = 66;
	private static final int COIN_D = 62;
	/**
	 * How high the toss goes. The coin rises into the title bar, which is empty across the
	 * middle — the name sits hard left and the wager hard right, so there is nothing there
	 * for it to cover.
	 */
	private static final int LIFT_PX = 26;
	/** The ground. The resting coin overlaps it, which is what sitting on a table looks like. */
	private static final int SHADOW_Y = 100;
	private static final int STATE_Y = 108;
	private static final int DIVIDER_Y = 118;
	private static final int POT_Y = 121;
	private static final int TAX_Y = 131;
	private static final int ODDS_Y = 145;
	private static final int ODDS_ROW_H = 11;
	private static final int BANNER_Y = 141;
	private static final int BANNER_SCALE = 2;
	private static final int BANNER_SUB_Y = 161;
	/** How long the settling ring stays up after the coin lands. */
	private static final long RING_MS = 420L;
	/**
	 * How far the ring spreads. Modest on purpose: the panel has no clip of its own, so a
	 * generous one would spill over the header and off the edge of the board.
	 */
	private static final int RING_MAX = 14;
	/** The jolt the whole panel takes on impact. */
	private static final long SHAKE_MS = 110L;
	private static final long FLASH_MS = 130L;
	/** While the spin is this fast, it is drawn with a smear behind it. */
	private static final long SMEAR_UNTIL_MS = 3_200L;
	/** How long a result may wait for a clear screen before it stops being news. */
	private static final long PENDING_MS = 4_000L;
	/**
	 * How recently a flip must have landed for a close to be <em>its</em> close. The server
	 * closes a menu whenever you leave one, the lobby included, and a result from five
	 * minutes ago must not ride in on the back of that.
	 */
	private static final long FRESH_RESULT_MS = 8_000L;

	/** Our own face sits on side 0 of the coin, so the toss starts showing the player. */
	private static final int YOUR_SIDE = 0;
	private static final int THEIR_SIDE = 1;

	/**
	 * Everything the result is made of, kept so it outlives the container.
	 *
	 * <p>The server closes the chest a couple of seconds after a flip resolves, which is
	 * not long enough to read what just happened to several hundred thousand dollars. This
	 * is what {@link CfResultScreen} puts back up.
	 */
	public record Landed(boolean won, long wagerCents, int flipId, String you, String them,
			String yourFace, String theirFace) {
	}

	private long startedAtMs;
	private String you;
	private String them;
	private CfFlipReader.Result result = CfFlipReader.Result.RUNNING;
	private long resultAtMs;
	private float landFrom;
	private float landTo;
	private long wagerCents;
	private int flipId;
	private String yourFace = "";
	private String theirFace = "";
	private Landed landedSnapshot;
	private Landed pending;
	private long pendingAtMs;

	private CfFlipBoard() {
	}

	@Override
	protected boolean enabled() {
		return LabsAddonsConfig.get().coinflipOverlay;
	}

	@Override
	protected int containerSlots() {
		return COINFLIP_SLOTS;
	}

	@Override
	protected boolean looksLike(ScreenHandler handler) {
		return CfFlipReader.looksLikeFlip(isHead(handler, CfFlipReader.COIN_SLOT),
				isBlankPane(handler, CfFlipReader.LEFT_PANE_SLOT),
				isBlankPane(handler, CfFlipReader.RIGHT_PANE_SLOT));
	}

	/**
	 * A head between two blank panes turns up in menus that are nothing to do with us, so
	 * this board never claims one the title has not named — not even for the frame in which
	 * the chest texture is suppressed.
	 */
	@Override
	protected boolean titleAllows(String title) {
		return CfFlipReader.isFlipTitle(title);
	}

	/** Slot names alone cannot tell this menu from a chest; the title can. */
	@Override
	protected boolean parses(List<SlotView> slots) {
		return false;
	}

	@Override
	protected boolean parses(List<SlotView> slots, String title) {
		return CfFlipReader.isFlip(slots, title);
	}

	@Override
	protected void onContainerChange() {
		startedAtMs = 0L;
		you = null;
		them = null;
		result = CfFlipReader.Result.RUNNING;
		resultAtMs = 0L;
		landFrom = 0f;
		landTo = 0f;
		wagerCents = 0L;
		flipId = 0;
		yourFace = "";
		theirFace = "";
		landedSnapshot = null;
	}

	/**
	 * The server has closed the chest. If a flip had just resolved in it, hand the result
	 * on so it can be put back up rather than blinking out mid-read.
	 */
	public void serverClosedScreen() {
		long now = Util.getMeasuringTimeMs();
		if (landedSnapshot != null && now - resultAtMs <= FRESH_RESULT_MS) {
			pending = landedSnapshot;
			pendingAtMs = now;
		}
	}

	/**
	 * The result waiting to be shown, taken exactly once — and only while it is still the
	 * thing that just happened. If the player was busy in another screen when the chest
	 * closed, this should quietly expire rather than ambush them a minute later.
	 */
	public Landed takePendingResult() {
		if (pending == null) {
			return null;
		}
		Landed result = Util.getMeasuringTimeMs() - pendingAtMs <= PENDING_MS ? pending : null;
		pending = null;
		return result;
	}

	@Override
	protected void draw(DrawContext context, TextRenderer font, List<SlotView> slots,
			String title, float deviceScale) {
		long now = Util.getMeasuringTimeMs();
		CfFlipReader.Flip flip = CfFlipReader.read(slots);
		if (startedAtMs == 0L) {
			startedAtMs = now;
			takeDetails(now);
		}
		learnNames(flip);

		float turns = spin(flip, now);
		int faceUp = CfToss.faceUp(turns);
		boolean landed = result != CfFlipReader.Result.RUNNING
				&& now - resultAtMs >= CfToss.LAND_MS;

		int jolt = shake(now, landed);
		if (jolt != 0) {
			context.getMatrices().pushMatrix();
			context.getMatrices().translate(0f, jolt);
		}

		header(context, font, "COINFLIP", status(), headerColor());
		seats(context, font, faceUp);
		coin(context, font, turns, faceUp, landed, now);
		state(context, font, now);

		context.fill(PAD, DIVIDER_Y, PANEL_W - PAD, DIVIDER_Y + 1, DIVIDER);
		stakes(context, font);
		if (landed) {
			banner(context, font);
		} else {
			outcomes(context, font);
		}

		if (jolt != 0) {
			context.getMatrices().popMatrix();
		}
	}

	/** A pixel of kick at the moment it lands, damped out over a tenth of a second. */
	private int shake(long now, boolean landed) {
		if (!landed) {
			return 0;
		}
		long since = now - resultAtMs - CfToss.LAND_MS;
		if (since < 0L || since > SHAKE_MS) {
			return 0;
		}
		float progress = since / (float) SHAKE_MS;
		return Math.round((1f - progress) * 2f * (float) Math.sin(progress * 22f));
	}

	// --- the spin ------------------------------------------------------------

	/**
	 * Half-turns to draw this frame: the curve while the flip is live, then a fall onto the
	 * winner's face once the server has named one.
	 */
	private float spin(CfFlipReader.Flip flip, long now) {
		if (flip.running()) {
			return CfToss.halfTurns(now - startedAtMs);
		}
		if (result == CfFlipReader.Result.RUNNING) {
			// The result frame. Whichever way the coin happens to be facing, it now has
			// somewhere to be.
			result = flip.result();
			resultAtMs = now;
			landFrom = CfToss.halfTurns(now - startedAtMs);
			landTo = CfToss.landingTarget(landFrom,
					result == CfFlipReader.Result.WON ? YOUR_SIDE : THEIR_SIDE);
			landedSnapshot = new Landed(result == CfFlipReader.Result.WON, wagerCents, flipId,
					you, them, yourFace, theirFace);
		}
		float progress = (now - resultAtMs) / (float) CfToss.LAND_MS;
		if (progress >= 1f) {
			return landTo;
		}
		return landFrom + (landTo - landFrom) * CfToss.ease(progress);
	}

	private void coin(DrawContext context, TextRenderer font, float turns, int faceUp,
			boolean landed, long now) {
		long elapsed = now - startedAtMs;
		float lift = CfToss.lift(elapsed);
		if (result != CfFlipReader.Result.RUNNING) {
			// Normally the coin is already down by the time the result lands — the curve
			// puts it on the table at the hold. This only matters when the server answers
			// early, and then it falls rather than blinking to the ground.
			lift *= 1f - CfToss.ease((now - resultAtMs) / (float) CfToss.LAND_MS);
		}
		int cy = COIN_BASE_CY - Math.round(lift * LIFT_PX);
		long sinceLanding = now - resultAtMs - CfToss.LAND_MS;

		CoinPainter.shadow(context, COIN_CX, SHADOW_Y, COIN_D, lift);

		// Two faint copies a fraction of a turn behind. Eight half-turns in the first
		// second is faster than a frame can show, and without a smear it just aliases.
		if (result == CfFlipReader.Result.RUNNING && elapsed < SMEAR_UNTIL_MS) {
			CoinPainter.ghost(context, COIN_CX, cy, COIN_D, CfToss.squeeze(turns - 0.10f), 46);
			CoinPainter.ghost(context, COIN_CX, cy, COIN_D, CfToss.squeeze(turns - 0.20f), 24);
		}

		float squeeze = landed ? CfToss.settle(sinceLanding) : CfToss.squeeze(turns);
		float flash = landed && sinceLanding < FLASH_MS
				? 1f - sinceLanding / (float) FLASH_MS
				: 0f;
		boolean yours = faceUp == YOUR_SIDE;
		String name = yours ? you : them;
		CoinPainter.draw(context, font, COIN_CX, cy, COIN_D, squeeze,
				name == null ? null : PlayerSkinCache.skin(name),
				(yours ? yourFace : theirFace).toUpperCase(Locale.ROOT), flash);

		if (landed) {
			// Two rings, out of the coin and gone. Long enough to register the result,
			// short enough not to sit on the screen nagging.
			float age = sinceLanding / (float) RING_MS;
			if (age <= 1f) {
				float spread = CfToss.ease(age);
				CoinPainter.ring(context, COIN_CX, cy,
						COIN_D / 2 + Math.round(spread * RING_MAX), ringColor(age));
				CoinPainter.ring(context, COIN_CX, cy,
						COIN_D / 2 + Math.round(spread * RING_MAX * 0.55f), ringColor(age));
			}
		}
	}

	private int ringColor(float age) {
		int alpha = Math.round(160 * (1f - Math.clamp(age, 0f, 1f)));
		return shade(result == CfFlipReader.Result.WON ? WIN : LOSS, alpha);
	}

	// --- the two players ----------------------------------------------------

	/**
	 * Learns who is playing. Only one head is up at a time, so the far side of the coin is
	 * blank for the first swap — about 150 ms — unless the lobby already named them.
	 */
	private void learnNames(CfFlipReader.Flip flip) {
		if (you == null) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null) {
				you = client.player.getName().getString();
			}
		}
		String facing = flip.facing();
		if (facing == null || facing.isEmpty()) {
			return;
		}
		if (you == null || facing.equalsIgnoreCase(you)) {
			return;
		}
		them = facing;
	}

	/** The wager, the id and the two sides, from whatever chat knew about the take. */
	private void takeDetails(long now) {
		CfChat.Taken take = CfChat.taken(now);
		if (take == null) {
			return;
		}
		wagerCents = take.wagerCents();
		flipId = take.id();
		if (take.opponent() != null) {
			them = take.opponent();
		}
		String creatorFace = take.creatorFace();
		if (creatorFace != null && !creatorFace.isEmpty()) {
			theirFace = creatorFace;
			yourFace = "heads".equalsIgnoreCase(creatorFace) ? "tails" : "heads";
		}
	}

	private void seats(DrawContext context, TextRenderer font, int faceUp) {
		seat(context, font, PAD + 2, you, yourFace, faceUp == YOUR_SIDE, false);
		seat(context, font, PANEL_W - PAD - SEAT_HEAD - 2, them, theirFace,
				faceUp == THEIR_SIDE, true);
	}

	private void seat(DrawContext context, TextRenderer font, int x, String name, String face,
			boolean up, boolean rightAligned) {
		if (name != null) {
			PlayerSkinDrawer.draw(context, PlayerSkinCache.skin(name), x, SEAT_Y, SEAT_HEAD);
		} else {
			context.fill(x, SEAT_Y, x + SEAT_HEAD, SEAT_Y + SEAT_HEAD, TRACK);
		}
		String label = name == null ? "…" : font.trimToWidth(name, 58);
		String side = face.toLowerCase(Locale.ROOT);
		int textX = rightAligned ? x - SEAT_TEXT_GAP : x + SEAT_HEAD + 4;
		drawAligned(context, font, label, textX, SEAT_Y + 1, up ? TEXT : TEXT_DIM, rightAligned);
		drawAligned(context, font, side, textX, SEAT_Y + 11, up ? accent() : TEXT_FAINT,
				rightAligned);
	}

	private static void drawAligned(DrawContext context, TextRenderer font, String text, int x,
			int y, int color, boolean rightAligned) {
		if (text.isEmpty()) {
			return;
		}
		context.drawText(font, text, rightAligned ? x - font.getWidth(text) : x, y, color,
				false);
	}

	// --- the money ----------------------------------------------------------

	private void stakes(DrawContext context, TextRenderer font) {
		int width = PANEL_W - PAD * 2;
		if (wagerCents <= 0L) {
			row(context, font, PAD, POT_Y, width, "pot", "—", TEXT_FAINT, TEXT_FAINT);
			context.drawText(font, "this screen never states the wager", PAD, TAX_Y,
					TEXT_FAINT, false);
			return;
		}
		row(context, font, PAD, POT_Y, width, "pot", Money.format(CfOdds.potCents(wagerCents)),
				TEXT_FAINT, TEXT);
		row(context, font, PAD, TAX_Y, width, "tax",
				"−" + Money.format(CfOdds.taxCents(wagerCents)) + " · 5%", TEXT_FAINT,
				TEXT_DIM);
	}

	private void outcomes(DrawContext context, TextRenderer font) {
		if (wagerCents <= 0L) {
			return;
		}
		int width = PANEL_W - PAD * 2;
		row(context, font, PAD, ODDS_Y, width, landsLabel(yourFace, "you win"),
				"+" + Money.format(CfOdds.winProfitCents(wagerCents)), TEXT_FAINT, WIN);
		row(context, font, PAD, ODDS_Y + ODDS_ROW_H, width, landsLabel(theirFace, "they win"),
				"−" + Money.format(wagerCents), TEXT_FAINT, LOSS);
	}

	/** Lowercase, like every other data label: CAPS is for titles, buttons and the result. */
	private static String landsLabel(String face, String fallback) {
		return face.isEmpty() ? fallback : "lands " + face.toLowerCase(Locale.ROOT);
	}

	private void banner(DrawContext context, TextRenderer font) {
		boolean won = result == CfFlipReader.Result.WON;
		String headline = won ? "YOU WON" : "YOU LOST";
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(PAD, BANNER_Y);
		context.getMatrices().scale(BANNER_SCALE, BANNER_SCALE);
		context.drawText(font, headline, 0, 0, won ? WIN : LOSS, false);
		context.getMatrices().popMatrix();

		if (wagerCents <= 0L) {
			return;
		}
		long net = won ? CfOdds.winProfitCents(wagerCents) : -wagerCents;
		String amount = (net >= 0 ? "+" : "−") + Money.format(Math.abs(net));
		context.drawText(font, amount, PANEL_W - PAD - font.getWidth(amount),
				BANNER_Y + font.fontHeight, won ? WIN : LOSS, false);
		String detail = won
				? "collected " + Money.format(CfOdds.winReturnCents(wagerCents))
				: "the stake is gone";
		context.drawText(font, detail, PAD, BANNER_SUB_Y, TEXT_DIM, false);
	}

	/** One line under the coin saying what it is doing. Centred, because the coin is. */
	private void state(DrawContext context, TextRenderer font, long now) {
		long elapsed = now - startedAtMs;
		String text;
		int color;
		if (result != CfFlipReader.Result.RUNNING) {
			text = wagerCents > 0L
					? "+" + CfOdds.investorPointsText(wagerCents) + " investor points"
					: "";
			color = TEXT_FAINT;
		} else if (elapsed > CfToss.STALLED_MS) {
			text = "still waiting on the server";
			color = WARN;
		} else if (elapsed >= CfToss.HOLD_MS) {
			text = "on the edge…";
			color = accent();
		} else {
			text = "in the air";
			color = TEXT_FAINT;
		}
		if (!text.isEmpty()) {
			context.drawText(font, text, COIN_CX - font.getWidth(text) / 2, STATE_Y, color,
					false);
		}
	}

	private String status() {
		String wager = wagerCents > 0L ? Money.format(wagerCents) : "";
		String id = flipId > 0 ? "#" + flipId : "";
		if (wager.isEmpty()) {
			return id;
		}
		return id.isEmpty() ? wager : wager + " · " + id;
	}

	private int headerColor() {
		return switch (result) {
			case WON -> WIN;
			case LOST -> LOSS;
			case RUNNING -> TEXT_DIM;
		};
	}

	// --- cheap probes off the handler ---------------------------------------

	private static boolean isHead(ScreenHandler handler, int slot) {
		ItemStack stack = stackAt(handler, slot);
		return stack != null && stack.isOf(Items.PLAYER_HEAD);
	}

	private static boolean isBlankPane(ScreenHandler handler, int slot) {
		ItemStack stack = stackAt(handler, slot);
		return stack != null && stack.getName().getString().isBlank();
	}

	private static ItemStack stackAt(ScreenHandler handler, int slot) {
		if (slot < 0 || slot >= handler.slots.size()) {
			return null;
		}
		ItemStack stack = handler.slots.get(slot).getStack();
		return stack.isEmpty() ? null : stack;
	}
}
