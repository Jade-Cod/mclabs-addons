package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjects;
import dev.jade.labsaddons.hud.HudObjectSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Coinflips posted and not yet taken, newest first, with the id each one answers to.
 *
 * <p>Nothing has to be opened for this to fill: every post is announced to the whole server,
 * so the widget is built entirely out of chat the mod already reads. A row leaves when that
 * flip resolves, when you take it, or when its twenty-four hours are up.
 *
 * <p>Read-only, because a HUD widget cannot be clicked while you are playing. Take one from
 * the server's own chat message, or from the lobby.
 */
public class CfOpenHudObject extends HudObject {
	public static final String ID = "coinflips";
	private static final int DEFAULT_TEXT_COLOR = 0xFFF0A02A;
	private static final int HEADER_COLOR = 0xFFFFFFFF;
	private static final int DIM_COLOR = 0xFF9AA3AD;
	private static final int LINE_GAP = 2;
	private static final int MAX_ROWS = 6;
	private static final int ID_GAP = 6;

	private record Row(String text, String id, int color) {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component group() {
		return HudObjects.GAMBLING;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.012f;
		defaults.y = 0.30f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return !CfChat.openFlips(Util.getMillis()).isEmpty();
	}

	private List<Row> rows(boolean preview) {
		List<CfChat.OpenFlip> flips = CfChat.openFlips(Util.getMillis());
		List<Row> rows = new ArrayList<>();
		rows.add(new Row("Coinflips", flips.isEmpty() ? "" : String.valueOf(flips.size()),
				HEADER_COLOR));
		if (flips.isEmpty()) {
			// Only ever seen in the editor: shouldRender keeps it off the HUD otherwise.
			rows.add(new Row(preview ? "nobody has posted one" : "none open", "", DIM_COLOR));
			return rows;
		}
		int color = settings().textColor | 0xFF000000;
		int shown = Math.min(flips.size(), MAX_ROWS);
		for (int i = 0; i < shown; i++) {
			CfChat.OpenFlip flip = flips.get(i);
			rows.add(new Row(flip.player() + "  " + Money.compact(flip.wagerCents()),
					"#" + flip.id(), color));
		}
		if (flips.size() > MAX_ROWS) {
			rows.add(new Row("+" + (flips.size() - MAX_ROWS) + " more", "", DIM_COLOR));
		}
		return rows;
	}

	@Override
	public int contentWidth(boolean preview) {
		Font font = Minecraft.getInstance().font;
		return rows(preview).stream()
				.mapToInt(row -> font.width(row.text())
						+ (row.id().isEmpty() ? 0 : ID_GAP + font.width(row.id())))
				.max().orElse(0);
	}

	@Override
	public int contentHeight(boolean preview) {
		int fontHeight = Minecraft.getInstance().font.lineHeight;
		List<Row> rows = rows(preview);
		return rows.size() * fontHeight + Math.max(0, rows.size() - 1) * LINE_GAP;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor context, boolean preview) {
		Font font = Minecraft.getInstance().font;
		int width = contentWidth(preview);
		int y = 0;
		for (Row row : rows(preview)) {
			context.text(font, Component.literal(row.text()), 0, y, row.color(), true);
			if (!row.id().isEmpty()) {
				context.text(font, Component.literal(row.id()),
						width - font.width(row.id()), y, DIM_COLOR, true);
			}
			y += font.lineHeight + LINE_GAP;
		}
	}
}
