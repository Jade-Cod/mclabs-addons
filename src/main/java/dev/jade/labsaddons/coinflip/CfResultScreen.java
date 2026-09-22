package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.PlayerSkinCache;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.hud.editor.EditorTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.Locale;

/**
 * The result, put back up after the server has taken the chest away.
 *
 * <p>A coinflip resolves and about two seconds later the server closes the menu, which is
 * not long enough to read what just happened to half a million dollars. This is the same
 * result without the container behind it: the coin as it landed, the figure, and where the
 * record stands now.
 *
 * <p>It goes away on its own after {@value #LINGER_MS} ms, and on any key or click before
 * that — it has appeared unasked, so it must never be something to fight with.
 */
public class CfResultScreen extends Screen {
	private static final int PANEL_W = 176;
	/** Tall enough for the seats' two lines to clear the record row underneath. */
	private static final int PANEL_H = 122;
	private static final int PAD = 8;
	private static final int COIN_D = 46;
	private static final int SEAT_HEAD = 12;
	private static final long LINGER_MS = 6_000L;
	private static final long FADE_MS = 220L;
	private static final int TEXT = 0xFFEAEEF3;
	private static final int TEXT_DIM = 0xFF9AA3AD;
	private static final int TEXT_FAINT = 0xFF6E7681;
	private static final int WIN = 0xFF96E03F;
	private static final int LOSS = 0xFFFF8080;
	private static final int BANNER_SCALE = 2;

	private final CfFlipBoard.Landed landed;
	private long openedAtMs;

	public CfResultScreen(CfFlipBoard.Landed landed) {
		super(Component.translatable("labsaddons.coinflip.result"));
		this.landed = landed;
	}

	@Override
	protected void init() {
		this.openedAtMs = Util.getMillis();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/**
	 * No dim. This screen opens by itself, so it has to sit over the world like a HUD
	 * widget rather than drawing a curtain across it.
	 */
	@Override
	public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		long age = Util.getMillis() - openedAtMs;
		if (age > LINGER_MS) {
			onClose();
			return;
		}
		super.extractRenderState(context, mouseX, mouseY, deltaTicks);

		int x = (this.width - PANEL_W) / 2;
		int y = (this.height - PANEL_H) / 2;
		HudObject.drawRoundedRect(context, x, y, PANEL_W, PANEL_H, EditorTheme.PANEL_BG);
		EditorPainter.outline(context, x, y, PANEL_W, PANEL_H,
				landed.won() ? WIN : EditorTheme.PANEL_BORDER);

		context.pose().pushMatrix();
		context.pose().translate(x, y);
		panel(context, age);
		context.pose().popMatrix();
	}

	private void panel(GuiGraphicsExtractor context, long age) {
		int colour = landed.won() ? WIN : LOSS;
		String winner = landed.won() ? landed.you() : landed.them();

		int coinCy = PAD + COIN_D / 2;
		CoinPainter.shadow(context, PAD + COIN_D / 2 + 2, coinCy + COIN_D / 2 + 4, COIN_D, 0f);
		CoinPainter.draw(context, PAD + COIN_D / 2 + 2, coinCy, COIN_D, 1f,
				winner == null ? null : PlayerSkinCache.skin(winner), 0f);

		int textX = PAD + COIN_D + 12;
		context.pose().pushMatrix();
		context.pose().translate(textX, PAD + 4);
		context.pose().scale(BANNER_SCALE, BANNER_SCALE);
		context.text(this.font, landed.won() ? "YOU WON" : "YOU LOST", 0, 0,
				colour, false);
		context.pose().popMatrix();

		if (landed.wagerCents() > 0L) {
			long net = landed.won()
					? CfOdds.winProfitCents(landed.wagerCents())
					: -landed.wagerCents();
			// Compact, like every other figure in the coinflip UI: a win worth
			// "+$1,872,327.28" spelled out is unreadable at a glance and overruns the panel.
			String amount = (net >= 0 ? "+" : "") + Money.compact(net);
			context.text(this.font, amount, textX, PAD + 26, colour, false);
		}

		int rowY = PAD + COIN_D + 12;
		context.fill(PAD, rowY, PANEL_W - PAD, rowY + 1, 0xFF23262E);
		rowY += 6;
		seat(context, PAD, rowY, landed.you(), landed.yourFace(), landed.won());
		seat(context, PANEL_W - PAD - SEAT_HEAD, rowY, landed.them(), landed.theirFace(),
				!landed.won());

		rowY += SEAT_HEAD + 12;
		CfStats.Record record = CfStats.current();
		if (!record.isEmpty()) {
			// Same shape as the lobby's stats block: wins in green, losses in red.
			int x = PAD;
			String wins = String.valueOf(record.won());
			context.text(this.font, wins, x, rowY, WIN, false);
			x += this.font.width(wins);
			context.text(this.font, "/", x, rowY, TEXT_FAINT, false);
			x += this.font.width("/");
			String losses = String.valueOf(record.lost());
			context.text(this.font, losses, x, rowY, LOSS, false);
			x += this.font.width(losses) + 6;
			context.text(this.font, record.winRateText(), x, rowY, TEXT_DIM, false);
			String profit = Money.compact(record.profitCents());
			context.text(this.font, profit,
					PANEL_W - PAD - this.font.width(profit), rowY,
					record.profitCents() >= 0 ? WIN : LOSS, false);
		}

		// The bar that says how long this is staying. Without it an unasked screen reads
		// as something that has gone wrong.
		int barW = Math.round(
				(PANEL_W - PAD * 2) * (1f - Math.clamp(age / (float) LINGER_MS, 0f, 1f)));
		context.fill(PAD, PANEL_H - PAD, PAD + barW, PANEL_H - PAD + 1, TEXT_FAINT);
	}

	private void seat(GuiGraphicsExtractor context, int x, int y, String name, String face,
			boolean winner) {
		if (name == null) {
			return;
		}
		PlayerFaceExtractor.extractRenderState(context, PlayerSkinCache.skin(name), x, y, SEAT_HEAD);
		boolean rightAligned = x > PANEL_W / 2;
		String label = this.font.plainSubstrByWidth(name, 58);
		int textX = rightAligned ? x - 4 - this.font.width(label) : x + SEAT_HEAD + 4;
		context.text(this.font, label, textX, y + 2, winner ? TEXT : TEXT_FAINT,
				false);
		if (face != null && !face.isEmpty()) {
			String side = face.toLowerCase(Locale.ROOT);
			int sideX = rightAligned ? x - 4 - this.font.width(side)
					: x + SEAT_HEAD + 4;
			context.text(this.font, side, sideX, y + 2 + this.font.lineHeight,
					winner ? (landed.won() ? WIN : LOSS) : TEXT_FAINT, false);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		onClose();
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		onClose();
		return true;
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(null);
		}
	}
}
