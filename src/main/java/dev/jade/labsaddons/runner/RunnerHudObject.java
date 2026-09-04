package dev.jade.labsaddons.runner;

import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runner Jobs HUD: shows how many jobs are currently posted and session stats
 * (completed, failed, money earned). Visible once any runner event fires; hidden
 * until then. Reset Session in the editor inspector zeroes all counters.
 */
public class RunnerHudObject extends HudObject {
    public static final String ID = "runner_jobs";

    private static final int HEADER_COLOR = 0xFFFFFFFF;
    private static final int POSTED_COLOR = 0xFFCCCCCC;
    private static final int COMPLETED_COLOR = 0xFF55FF55;
    private static final int FAILED_COLOR = 0xFFFF5555;
    private static final int MONEY_COLOR = 0xFF80FF80;
    private static final int LINE_GAP = 3;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public HudObjectSettings defaultSettings() {
        HudObjectSettings defaults = new HudObjectSettings();
        defaults.x = 0.012f;
        defaults.y = 0.70f;
        return defaults;
    }

    @Override
    public boolean shouldRender() {
        return RunnerTracker.hasActivity();
    }

    @Override
    public EditorAction editorAction() {
        return new EditorAction(Component.translatable("labsaddons.hud.runner_jobs.reset"), RunnerTracker::resetSession);
    }

    private record Row(String text, int color) {
    }

    private List<Row> rows(boolean preview) {
        if (preview && !RunnerTracker.hasActivity()) {
            return sampleRows();
        }
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Runner Jobs", HEADER_COLOR));
        rows.add(new Row("Posted  " + RunnerTracker.postedJobs(), POSTED_COLOR));
        rows.add(new Row("Completed  " + RunnerTracker.completedJobs(), COMPLETED_COLOR));
        rows.add(new Row("Failed  " + RunnerTracker.failedJobs(), FAILED_COLOR));
        rows.add(new Row("Earned  $" + formatMoney(RunnerTracker.totalEarned()), MONEY_COLOR));
        return rows;
    }

    private List<Row> sampleRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Runner Jobs", HEADER_COLOR));
        rows.add(new Row("Posted  2", POSTED_COLOR));
        rows.add(new Row("Completed  5", COMPLETED_COLOR));
        rows.add(new Row("Failed  1", FAILED_COLOR));
        rows.add(new Row("Earned  $25.2k", MONEY_COLOR));
        return rows;
    }

    public static String formatMoney(double amount) {
        if (amount >= 1_000_000) {
            return String.format(Locale.US, "%.1fm", amount / 1_000_000);
        }
        if (amount >= 1_000) {
            return String.format(Locale.US, "%.1fk", amount / 1_000);
        }
        return String.format(Locale.US, "%.0f", amount);
    }

    @Override
    public int contentWidth(boolean preview) {
        Font font = Minecraft.getInstance().font;
        return rows(preview).stream()
                .mapToInt(row -> font.width(row.text()))
                .max().orElse(0);
    }

    @Override
    public int contentHeight(boolean preview) {
        List<Row> rows = rows(preview);
        int fontHeight = Minecraft.getInstance().font.lineHeight;
        return rows.size() * fontHeight + Math.max(0, rows.size() - 1) * LINE_GAP;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor context, boolean preview) {
        Font font = Minecraft.getInstance().font;
        int fontHeight = font.lineHeight;
        int y = 0;
        for (Row row : rows(preview)) {
            context.text(font, Component.literal(row.text()), 0, y, row.color(), true);
            y += fontHeight + LINE_GAP;
        }
    }
}
