package dev.jade.labsaddons.hud;

import dev.jade.labsaddons.config.RunnerJob;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.hud.editor.EditorTheme;
import dev.jade.labsaddons.runner.RunnerHudObject;
import dev.jade.labsaddons.runner.RunnerTracker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Per-runner "recent completed jobs" screen, opened by clicking a row on the
 * {@link RunnerStatsScreen} leaderboard. Shows up to {@link #PER_PAGE} jobs a
 * page (newest first) with drug×qty, value, time-taken and date, plus
 * {@code <}/{@code >} page turners. Hand-drawn, reusing the leaderboard's panel
 * and row idiom.
 */
public class RunnerJobsScreen extends Screen {
	private static final int PER_PAGE = 10;
	private static final int PAD = 12;
	private static final int TITLE_H = 22;
	private static final int COL_HEADER_H = 14;
	private static final int ROW_H = 14;
	private static final int BUTTON_H = 20;
	private static final int TOP_MARGIN = 24;
	private static final int MAX_CONTENT_W = 372;
	private static final int HEAD_SIZE = 16;

	private static final int COL_JOB = 0;
	private static final int COL_VALUE = 150;
	private static final int COL_TIME = 212;
	private static final int COL_DATE = 274;

	private static final int C_JOB = EditorTheme.TEXT;
	private static final int C_VALUE = 0xFF80FF80;
	private static final int C_TIME = EditorTheme.TEXT_DIM;
	private static final int C_DATE = EditorTheme.TEXT_DIM;

	private static final DateTimeFormatter DATE_FMT =
			DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US).withZone(ZoneId.systemDefault());

	private final Screen parent;
	private final String runner;
	private List<RunnerJob> jobs = List.of();
	private int pageIndex = 0;
	private ButtonWidget prevButton;
	private ButtonWidget nextButton;

	public RunnerJobsScreen(Screen parent, String runner) {
		super(Text.translatable("labsaddons.hud.jobs.title", runner));
		this.parent = parent;
		this.runner = runner;
	}

	@Override
	protected void init() {
		this.jobs = RunnerTracker.recentJobs(runner);
		this.pageIndex = Math.min(this.pageIndex, Math.max(0, pageCount() - 1));

		int cardW = cardW();
		int cardX = (this.width - cardW) / 2;
		int cardH = this.height - TOP_MARGIN * 2;
		int contentX = cardX + PAD;
		int contentW = cardW - 2 * PAD;
		int buttonsY = TOP_MARGIN + cardH - PAD - BUTTON_H;

		this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> this.close())
				.dimensions(contentX, buttonsY, 60, BUTTON_H).build());
		this.prevButton = ButtonWidget.builder(Text.translatable("labsaddons.hud.jobs.prev"), b -> turnPage(-1))
				.dimensions(contentX + contentW - 20 - 4 - 20, buttonsY, 20, BUTTON_H).build();
		this.nextButton = ButtonWidget.builder(Text.translatable("labsaddons.hud.jobs.next"), b -> turnPage(1))
				.dimensions(contentX + contentW - 20, buttonsY, 20, BUTTON_H).build();
		this.addDrawableChild(this.prevButton);
		this.addDrawableChild(this.nextButton);
		updatePageButtons();
	}

	private int cardW() {
		return Math.min(MAX_CONTENT_W + 2 * PAD, this.width - 20);
	}

	private int pageCount() {
		return Math.max(1, (jobs.size() + PER_PAGE - 1) / PER_PAGE);
	}

	private void turnPage(int delta) {
		pageIndex = Math.max(0, Math.min(pageIndex + delta, pageCount() - 1));
		updatePageButtons();
	}

	private void updatePageButtons() {
		prevButton.active = pageIndex > 0;
		nextButton.active = pageIndex < pageCount() - 1;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);

		int cardW = cardW();
		int cardX = (this.width - cardW) / 2;
		int cardY = TOP_MARGIN;
		int cardH = this.height - TOP_MARGIN * 2;
		int contentX = cardX + PAD;
		int contentW = cardW - 2 * PAD;
		int headerY = cardY + PAD + TITLE_H;
		int rowsTop = headerY + COL_HEADER_H;

		EditorPainter.panel(context, new int[] {cardX, cardY, cardW, cardH},
				EditorTheme.PANEL_BG, EditorTheme.PANEL_BORDER);

		// Title: head icon + "Recent jobs — <name>".
		PlayerSkinDrawer.draw(context, PlayerSkinCache.skin(runner), contentX, cardY + PAD - 3, HEAD_SIZE);
		context.drawTextWithShadow(this.textRenderer, this.title, contentX + HEAD_SIZE + 6, cardY + PAD + 1, EditorTheme.TITLE);

		if (jobs.isEmpty()) {
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.translatable("labsaddons.hud.jobs.empty"),
					this.width / 2, cardY + cardH / 2 - 4, EditorTheme.TEXT_DIM);
			return;
		}

		// Column header.
		cell(context, contentX + COL_JOB, headerY, "labsaddons.hud.jobs.col.job");
		cell(context, contentX + COL_VALUE, headerY, "labsaddons.hud.jobs.col.value");
		cell(context, contentX + COL_TIME, headerY, "labsaddons.hud.jobs.col.time");
		cell(context, contentX + COL_DATE, headerY, "labsaddons.hud.jobs.col.date");
		context.fill(contentX, rowsTop - 2, contentX + contentW, rowsTop - 1, EditorTheme.PANEL_BORDER);

		int start = pageIndex * PER_PAGE;
		int end = Math.min(jobs.size(), start + PER_PAGE);
		int y = rowsTop + 2;
		for (int i = start; i < end; i++) {
			drawJobRow(context, contentX, y, contentW, (i - start) % 2 == 1, jobs.get(i));
			y += ROW_H;
		}

		// Page indicator, centred between the < > buttons.
		int buttonsY = cardY + cardH - PAD - BUTTON_H;
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("labsaddons.hud.jobs.page", pageIndex + 1, pageCount()),
				contentX + contentW - 66, buttonsY + 6, EditorTheme.TEXT_DIM);
	}

	private void cell(DrawContext context, int x, int y, String langKey) {
		context.drawText(this.textRenderer, Text.translatable(langKey), x, y, EditorTheme.TEXT_DIM, false);
	}

	private void drawJobRow(DrawContext context, int x, int y, int contentW, boolean alt, RunnerJob job) {
		if (alt) {
			context.fill(x, y - 2, x + contentW, y + ROW_H - 2, 0x11FFFFFF);
		}
		String jobLabel = job.qty > 0 && !job.drug.isEmpty()
				? job.qty + "x " + job.drug
				: (job.drug.isEmpty() ? "—" : job.drug);
		jobLabel = this.textRenderer.trimToWidth(jobLabel, COL_VALUE - COL_JOB - 4);
		String time = job.durationMs > 0 ? formatDuration(job.durationMs) : "—";
		String date = job.completedMs > 0 ? DATE_FMT.format(Instant.ofEpochMilli(job.completedMs)) : "—";

		text(context, x + COL_JOB, y, jobLabel, C_JOB);
		text(context, x + COL_VALUE, y, "$" + RunnerHudObject.formatMoney(job.value), C_VALUE);
		text(context, x + COL_TIME, y, time, C_TIME);
		text(context, x + COL_DATE, y, date, C_DATE);
	}

	private void text(DrawContext context, int x, int y, String s, int color) {
		context.drawText(this.textRenderer, Text.literal(s), x, y, color, true);
	}

	/** mm:ss up to an hour, then h/m. */
	private static String formatDuration(long ms) {
		long totalSec = ms / 1000;
		long m = totalSec / 60;
		long s = totalSec % 60;
		if (m >= 60) {
			return String.format(Locale.US, "%dh %02dm", m / 60, m % 60);
		}
		return String.format(Locale.US, "%dm %02ds", m, s);
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}
}
