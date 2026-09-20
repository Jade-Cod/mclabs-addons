package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.PlayerSkinCache;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.hud.editor.EditorTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;
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
	private static final int PANEL_H = 108;
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
		super(Text.translatable("labsaddons.coinflip.result"));
		this.landed = landed;
	}

	@Override
	protected void init() {
		this.openedAtMs = Util.getMeasuringTimeMs();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/**
	 * No dim. This screen opens by itself, so it has to sit over the world like a HUD
	 * widget rather than drawing a curtain across it.
	 */
	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		long age = Util.getMeasuringTimeMs() - openedAtMs;
		if (age > LINGER_MS) {
			close();
			return;
		}
		super.render(context, mouseX, mouseY, deltaTicks);

		int x = (this.width - PANEL_W) / 2;
		int y = (this.height - PANEL_H) / 2;
		HudObject.drawRoundedRect(context, x, y, PANEL_W, PANEL_H, EditorTheme.PANEL_BG);
		EditorPainter.outline(context, x, y, PANEL_W, PANEL_H,
				landed.won() ? WIN : EditorTheme.PANEL_BORDER);

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(x, y);
		panel(context, age);
		context.getMatrices().popMatrix();
	}

	private void panel(DrawContext context, long age) {
		int colour = landed.won() ? WIN : LOSS;
		String winner = landed.won() ? landed.you() : landed.them();
		String face = landed.won() ? landed.yourFace() : landed.theirFace();

		int coinCy = PAD + COIN_D / 2;
		CoinPainter.shadow(context, PAD + COIN_D / 2 + 2, coinCy + COIN_D / 2 + 4, COIN_D, 0f);
		CoinPainter.draw(context, this.textRenderer, PAD + COIN_D / 2 + 2, coinCy, COIN_D, 1f,
				winner == null ? null : PlayerSkinCache.skin(winner),
				face == null ? "" : face.toUpperCase(Locale.ROOT), 0f);

		int textX = PAD + COIN_D + 12;
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(textX, PAD + 4);
		context.getMatrices().scale(BANNER_SCALE, BANNER_SCALE);
		context.drawText(this.textRenderer, landed.won() ? "YOU WON" : "YOU LOST", 0, 0,
				colour, false);
		context.getMatrices().popMatrix();

		if (landed.wagerCents() > 0L) {
			long net = landed.won()
					? CfOdds.winProfitCents(landed.wagerCents())
					: -landed.wagerCents();
			String amount = (net >= 0 ? "+" : "") + Money.format(net);
			context.drawText(this.textRenderer, amount, textX, PAD + 26, colour, false);
			String detail = landed.won()
					? "collected " + Money.format(CfOdds.winReturnCents(landed.wagerCents()))
					: "staked " + Money.format(landed.wagerCents());
			context.drawText(this.textRenderer, detail, textX, PAD + 38, TEXT_DIM, false);
		}

		int rowY = PAD + COIN_D + 12;
		context.fill(PAD, rowY, PANEL_W - PAD, rowY + 1, 0xFF23262E);
		rowY += 6;
		seat(context, PAD, rowY, landed.you(), landed.yourFace(), landed.won());
		seat(context, PANEL_W - PAD - SEAT_HEAD, rowY, landed.them(), landed.theirFace(),
				!landed.won());

		rowY += SEAT_HEAD + 5;
		CfStats.Record record = CfStats.current();
		if (!record.isEmpty()) {
			String line = record.won() + " / " + record.played() + "  " + record.winRateText();
			context.drawText(this.textRenderer, line, PAD, rowY, TEXT_DIM, false);
			String net = Money.compact(record.profitCents());
			context.drawText(this.textRenderer, net,
					PANEL_W - PAD - this.textRenderer.getWidth(net), rowY,
					record.profitCents() >= 0 ? WIN : LOSS, false);
		}

		// The bar that says how long this is staying. Without it an unasked screen reads
		// as something that has gone wrong.
		int barW = Math.round(
				(PANEL_W - PAD * 2) * (1f - Math.clamp(age / (float) LINGER_MS, 0f, 1f)));
		context.fill(PAD, PANEL_H - PAD, PAD + barW, PANEL_H - PAD + 1, TEXT_FAINT);
	}

	private void seat(DrawContext context, int x, int y, String name, String face,
			boolean winner) {
		if (name == null) {
			return;
		}
		PlayerSkinDrawer.draw(context, PlayerSkinCache.skin(name), x, y, SEAT_HEAD);
		boolean rightAligned = x > PANEL_W / 2;
		String label = this.textRenderer.trimToWidth(name, 58);
		int textX = rightAligned ? x - 4 - this.textRenderer.getWidth(label) : x + SEAT_HEAD + 4;
		context.drawText(this.textRenderer, label, textX, y + 2, winner ? TEXT : TEXT_FAINT,
				false);
		if (face != null && !face.isEmpty()) {
			String side = face.toUpperCase(Locale.ROOT);
			int sideX = rightAligned ? x - 4 - this.textRenderer.getWidth(side)
					: x + SEAT_HEAD + 4;
			context.drawText(this.textRenderer, side, sideX, y + 2 + this.textRenderer.fontHeight,
					winner ? (landed.won() ? WIN : LOSS) : TEXT_FAINT, false);
		}
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		close();
		return true;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		close();
		return true;
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(null);
		}
	}
}
