package dev.jade.fishbite.chem;

import dev.jade.fishbite.config.FishBiteConfig;
import dev.jade.fishbite.hud.HudObject;
import dev.jade.fishbite.hud.HudObjectSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Chemtainer contents widget: a "Chemtainer" header, an estimate of how many
 * inventories the chems would fill, then each chem (icon + count + name) ordered
 * by quantity. The inventory estimate divides the total chem count by a per-stack
 * capacity that depends on whether the player uses a satchel (toggled in the
 * editor): {@value #SATCHEL_DIVISOR} with a satchel, {@value #BASE_DIVISOR} without.
 */
public class ChemtainerHudObject extends HudObject {
	public static final String ID = "chemtainer";
	private static final int DEFAULT_TEXT_COLOR = 0xFFB388FF;
	private static final int HEADER_COLOR = 0xFFFFFFFF;
	private static final int DIM_COLOR = 0xFF9AA3AD;
	private static final int ICON_SIZE = 16;
	private static final int ICON_GAP = 4;
	private static final int LINE_GAP = 3;
	private static final int MAX_ROWS = 8;
	private static final int SATCHEL_DIVISOR = 3392;
	private static final int BASE_DIVISOR = 2240;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.012f;
		defaults.y = 0.55f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return ChemtainerTracker.hasSnapshot();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("fishbite.hud.chemtainer.clear"), ChemtainerTracker::clear);
	}

	@Override
	public List<ToggleOption> toggleOptions() {
		return List.of(new ToggleOption(
				Text.translatable("fishbite.hud.chemtainer.satchel"),
				() -> FishBiteConfig.get().chemtainerSatchel,
				value -> {
					FishBiteConfig.get().chemtainerSatchel = value;
					FishBiteConfig.get().save();
				}));
	}

	private record Row(ItemStack icon, String text, int color) {
	}

	private List<Row> rows(boolean preview) {
		if (preview && !ChemtainerTracker.hasSnapshot()) {
			return sampleRows();
		}

		List<ChemtainerEntry> entries = new ArrayList<>(ChemtainerTracker.entries());
		entries.sort(Comparator.comparingLong((ChemtainerEntry entry) -> entry.count).reversed());
		long total = entries.stream().mapToLong(entry -> entry.count).sum();

		List<Row> rows = new ArrayList<>();
		rows.add(new Row(null, "Chemtainer", HEADER_COLOR));
		rows.add(new Row(null, inventoriesLine(total), DIM_COLOR));

		int chemColor = settings().textColor | 0xFF000000;
		int shown = Math.min(entries.size(), MAX_ROWS);
		for (int i = 0; i < shown; i++) {
			ChemtainerEntry entry = entries.get(i);
			rows.add(new Row(ChemIcons.iconFor(entry.chem), formatCount(entry.count) + "  " + name(entry), chemColor));
		}
		if (entries.size() > MAX_ROWS) {
			rows.add(new Row(null, "+" + (entries.size() - MAX_ROWS) + " more", DIM_COLOR));
		} else if (entries.isEmpty()) {
			rows.add(new Row(null, "empty", DIM_COLOR));
		}
		return rows;
	}

	private List<Row> sampleRows() {
		int chemColor = settings().textColor | 0xFF000000;
		List<Row> rows = new ArrayList<>();
		rows.add(new Row(null, "Chemtainer", HEADER_COLOR));
		rows.add(new Row(null, inventoriesLine(11_696), DIM_COLOR));
		rows.add(new Row(ChemIcons.iconFor("chowartusite"), "8,704  Chowartusite", chemColor));
		rows.add(new Row(ChemIcons.iconFor("pumpwartinide"), "2,992  Pumpwartinide", chemColor));
		return rows;
	}

	/** Chem name without the purity suffix (e.g. "Chowartusite"). */
	private static String name(ChemtainerEntry entry) {
		return ChemItems.displayLabel(new ChemItems.ChemKey(entry.chem, ""));
	}

	private static String inventoriesLine(long total) {
		int divisor = FishBiteConfig.get().chemtainerSatchel ? SATCHEL_DIVISOR : BASE_DIVISOR;
		return String.format(Locale.US, "%.1f Inventories", total / (double) divisor);
	}

	private static String formatCount(long count) {
		return String.format(Locale.US, "%,d", count);
	}

	private static int rowHeight(Row row) {
		int fontHeight = MinecraftClient.getInstance().textRenderer.fontHeight;
		return row.icon() != null ? Math.max(ICON_SIZE, fontHeight) : fontHeight;
	}

	@Override
	public int contentWidth(boolean preview) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		return rows(preview).stream()
				.mapToInt(row -> (row.icon() != null ? ICON_SIZE + ICON_GAP : 0) + font.getWidth(row.text()))
				.max().orElse(0);
	}

	@Override
	public int contentHeight(boolean preview) {
		List<Row> rows = rows(preview);
		int height = 0;
		for (Row row : rows) {
			height += rowHeight(row);
		}
		return height + Math.max(0, rows.size() - 1) * LINE_GAP;
	}

	@Override
	protected void renderContent(DrawContext context, boolean preview) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int y = 0;
		for (Row row : rows(preview)) {
			int height = rowHeight(row);
			int textX = 0;
			if (row.icon() != null) {
				context.drawItem(row.icon(), 0, y + (height - ICON_SIZE) / 2);
				textX = ICON_SIZE + ICON_GAP;
			}
			context.drawText(font, Text.literal(row.text()), textX, y + (height - font.fontHeight) / 2 + 1,
					row.color(), true);
			y += height + LINE_GAP;
		}
	}
}
