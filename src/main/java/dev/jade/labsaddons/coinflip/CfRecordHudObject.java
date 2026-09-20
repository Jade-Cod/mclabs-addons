package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Your coinflip record: what you have won, what you have lost, and what it came to.
 *
 * <p>Seeded once from {@code /cf stats} and maintained from each result after that, so it
 * costs no command to keep current.
 */
public class CfRecordHudObject extends HudObject {
	public static final String ID = "coinflip_record";
	private static final int DEFAULT_TEXT_COLOR = 0xFFF0A02A;
	private static final int HEADER_COLOR = 0xFFFFFFFF;
	private static final int DIM_COLOR = 0xFF9AA3AD;
	private static final int WIN_COLOR = 0xFF96E03F;
	private static final int LOSS_COLOR = 0xFFFF8080;
	private static final int LINE_GAP = 2;
	private static final int VALUE_GAP = 8;

	private record Row(String label, String value, int labelColor, int valueColor) {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.012f;
		defaults.y = 0.44f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return !CfStats.current().isEmpty() || CfChat.sessionPlayed() > 0;
	}

	private List<Row> rows() {
		CfStats.Record record = CfStats.current();
		List<Row> rows = new ArrayList<>();
		rows.add(new Row("Coinflip", "", HEADER_COLOR, HEADER_COLOR));
		if (record.isEmpty()) {
			rows.add(new Row("record", CfStats.seeded() ? "none yet" : "run /cf stats",
					DIM_COLOR, DIM_COLOR));
		} else {
			rows.add(new Row("wins", String.valueOf(record.won()), DIM_COLOR, WIN_COLOR));
			rows.add(new Row("losses", String.valueOf(record.lost()), DIM_COLOR, LOSS_COLOR));
			rows.add(new Row("rate", record.winRateText(), DIM_COLOR,
					settings().textColor | 0xFF000000));
		}
		if (CfChat.sessionPlayed() > 0) {
			long net = CfChat.sessionNetCents();
			rows.add(new Row("session", signed(net), DIM_COLOR,
					net >= 0 ? WIN_COLOR : LOSS_COLOR));
		}
		if (!record.isEmpty()) {
			rows.add(new Row("profit", signed(record.profitCents()), DIM_COLOR,
					record.profitCents() >= 0 ? WIN_COLOR : LOSS_COLOR));
		}
		return rows;
	}

	/** Money already signs a negative; a positive is worth marking as one. */
	private static String signed(long cents) {
		return (cents > 0 ? "+" : "") + Money.compact(cents);
	}

	@Override
	public int contentWidth(boolean preview) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		return rows().stream()
				.mapToInt(row -> font.getWidth(row.label())
						+ (row.value().isEmpty() ? 0 : VALUE_GAP + font.getWidth(row.value())))
				.max().orElse(0);
	}

	@Override
	public int contentHeight(boolean preview) {
		int fontHeight = MinecraftClient.getInstance().textRenderer.fontHeight;
		List<Row> rows = rows();
		return rows.size() * fontHeight + Math.max(0, rows.size() - 1) * LINE_GAP;
	}

	@Override
	protected void renderContent(DrawContext context, boolean preview) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int width = contentWidth(preview);
		int y = 0;
		for (Row row : rows()) {
			if (!row.label().isEmpty()) {
				context.drawText(font, Text.literal(row.label()), 0, y, row.labelColor(), true);
			}
			if (!row.value().isEmpty()) {
				context.drawText(font, Text.literal(row.value()),
						width - font.getWidth(row.value()), y, row.valueColor(), true);
			}
			y += font.fontHeight + LINE_GAP;
		}
	}
}
