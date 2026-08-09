package dev.jade.labsaddons.hud;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.hud.editor.EditorTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Welcome / Help guide shown over the HUD editor. On the player's very first
 * open of the editor it appears once (gated by {@link LabsAddonsConfig#hasSeenWelcome});
 * afterwards it only opens from the editor's "Help" button. A single card with a
 * short, scannable tour — restrained hierarchy, a subtle fade-in, one primary
 * action.
 *
 * <p>The {@link #SYNC_COMMANDS} table is the source of truth for the guide's
 * "sync commands" section. <b>When a new chat-command sync function is added to
 * the mod, add a row here</b> (see the rule in CLAUDE.md).
 */
public class HelpScreen extends Screen {
	private static final int CARD_W = 332;
	private static final int PAD = 14;
	private static final int LINE_H = 11;
	private static final int SECTION_GAP = 9;
	private static final int FADE_MS = 160;
	private static final int BUTTON_W = 120;
	private static final int BUTTON_H = 20;
	private static final int INDENT = 10;
	private static final int SHADOW = 0x33000000;

	/**
	 * Chat-command sync functions surfaced in the guide: {command, what it syncs}.
	 * KEEP IN SYNC with the mod's actual sync features (see CLAUDE.md).
	 */
	private static final String[][] SYNC_COMMANDS = {
			{"/checkboost", "Syncs your Personal boost timers (chem price + prestige)."},
			{"/mccooldown", "Run it any time to sync exact mcMMO ability cooldowns."},
			{"/lw rates", "Open it once to sync your Lab Wars revenue boosters."},
			{"/chems booster", "Open it once to sync server-wide booster rates."},
			{"/sm claim", "Auto-marks your daily SM claim the instant you send it."},
			{"Deposit key (B)", "Sends /ch qd and tracks the chems you banked on the HUD."},
			{"Withdraw key (N)", "Pulls back whichever chem you have the most of."},
			{"/ch", "Open it any time to re-sync exact Chemtainer contents."},
			{"/supplier", "Open it to re-sync your posted runner-job count."},
			{"/mastery", "Open it to re-sync your active Mastery challenge bars."},
			{"/prestige progress", "Run it to sync your chem prestige bars; sales then track live."},
			{"Chat reactions", "Wins and runner-ups advance your Mastery bars live."},
			{"Vote on all 7 sites", "Every ‘Vote registered!’ counts toward your daily 7."},
	};

	private final Screen parent;
	private final boolean firstRun;
	private final List<Line> lines = new ArrayList<>();
	private long openTimeMs;
	private int cardX;
	private int cardY;
	private int cardH;
	private int linesViewTop;
	private int linesViewBottom;
	private int linesContentH;
	private int scrollOffset;
	private int maxScroll;

	/** A single rendered line: its text, colour, left indent and the gap above it. */
	private record Line(OrderedText text, int color, int indent, int gapAbove) {
	}

	public HelpScreen(Screen parent, boolean firstRun) {
		super(Text.translatable("labsaddons.hud.help.title"));
		this.parent = parent;
		this.firstRun = firstRun;
		// Persist "seen" immediately so the welcome can never reappear, even if the
		// editor re-inits before this screen is dismissed.
		if (firstRun && !LabsAddonsConfig.get().hasSeenWelcome) {
			LabsAddonsConfig.get().hasSeenWelcome = true;
			LabsAddonsConfig.get().save();
		}
	}

	@Override
	protected void init() {
		this.openTimeMs = System.currentTimeMillis();
		buildLines();

		this.linesContentH = 0;
		for (Line line : lines) {
			linesContentH += line.gapAbove() + LINE_H;
		}
		int desiredCardH = PAD + linesContentH + SECTION_GAP + BUTTON_H + PAD;
		// Never let the card exceed the window — at small window sizes (e.g. GUI
		// scale "Auto" picking a low resolution) the full content wouldn't fit and
		// the dismiss button would render below the visible screen.
		this.cardH = Math.min(desiredCardH, Math.max(8, this.height - 16));
		this.cardX = (this.width - CARD_W) / 2;
		this.cardY = Math.max(8, (this.height - cardH) / 2);

		int buttonY = cardY + cardH - PAD - BUTTON_H;
		this.linesViewTop = cardY + PAD;
		this.linesViewBottom = buttonY - SECTION_GAP;
		this.maxScroll = Math.max(0, linesContentH - (linesViewBottom - linesViewTop));
		this.scrollOffset = 0;

		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("labsaddons.hud.help.dismiss"), b -> this.close())
				.dimensions(this.width / 2 - BUTTON_W / 2, buttonY, BUTTON_W, BUTTON_H).build());
	}

	private void buildLines() {
		lines.clear();
		int innerW = CARD_W - 2 * PAD;

		add(bold("MCLabs Addons"), EditorTheme.TITLE, 0, 0, innerW);
		add(Text.literal(firstRun
						? "Thanks for downloading — here’s the quick tour."
						: "Quick reference for syncing and your HUD."),
				EditorTheme.TEXT_DIM, 0, 2, innerW);

		add(bold("SYNC COMMANDS"), EditorTheme.TEXT_ACCENT, 0, SECTION_GAP, innerW);
		add(Text.literal("Run these so the HUD matches the server:"),
				EditorTheme.TEXT_DIM, 0, 2, innerW);
		for (String[] command : SYNC_COMMANDS) {
			add(Text.literal(command[0]), EditorTheme.TEXT_ACCENT, 0, 4, innerW);
			add(Text.literal(command[1]), EditorTheme.TEXT_DIM, INDENT, 1, innerW - INDENT);
		}

		add(bold("AUTOMATIC"), EditorTheme.TEXT_ACCENT, 0, SECTION_GAP, innerW);
		add(Text.literal("Boosters, mini-events, the Pit, bounties, rental mounts and the "
						+ "Chum timer track themselves from chat — just play."),
				EditorTheme.TEXT_DIM, 0, 2, innerW);

		add(bold("YOUR HUD"), EditorTheme.TEXT_ACCENT, 0, SECTION_GAP, innerW);
		add(Text.literal("Drag to move, pull the handles to resize, click a widget for colours."),
				EditorTheme.TEXT_DIM, 0, 2, innerW);
		add(Text.literal("Reopen this guide anytime with the Help button."),
				EditorTheme.TEXT_DIM, 0, 1, innerW);
	}

	/** Wraps {@code text} to {@code wrapW} and appends each visual line; only the
	 *  first wrapped line carries {@code gapAbove}. */
	private void add(Text text, int color, int indent, int gapAbove, int wrapW) {
		int gap = gapAbove;
		for (OrderedText ordered : this.textRenderer.wrapLines(text, wrapW)) {
			lines.add(new Line(ordered, color, indent, gap));
			gap = 0;
		}
	}

	private static Text bold(String text) {
		return Text.literal(text).styled(style -> style.withBold(true));
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		float fade = fade();

		// Layered, transparent drop shadow so the card reads above the dimmed editor.
		HudObject.drawRoundedRect(context, cardX - 1, cardY + 2, CARD_W + 2, cardH, mul(SHADOW, fade));
		HudObject.drawRoundedRect(context, cardX, cardY + 4, CARD_W, cardH, mul(SHADOW, fade));

		HudObject.drawRoundedRect(context, cardX, cardY, CARD_W, cardH, mul(EditorTheme.PANEL_BG, fade));
		EditorPainter.outline(context, cardX, cardY, CARD_W, cardH, mul(EditorTheme.PANEL_BORDER, fade));

		int x = cardX + PAD;
		int y = linesViewTop - scrollOffset;
		context.enableScissor(cardX, linesViewTop, cardX + CARD_W, linesViewBottom);
		for (Line line : lines) {
			y += line.gapAbove();
			if (y + LINE_H >= linesViewTop && y <= linesViewBottom) {
				context.drawText(this.textRenderer, line.text(), x + line.indent(), y, mul(line.color(), fade), false);
			}
			y += LINE_H;
		}
		context.disableScissor();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scrollOffset = MathHelper.clamp(scrollOffset - (int) Math.round(verticalAmount) * LINE_H * 2, 0, maxScroll);
		return true;
	}

	/** Smoothstep 0..1 over the first {@link #FADE_MS} after the screen opens. */
	private float fade() {
		float t = MathHelper.clamp((System.currentTimeMillis() - openTimeMs) / (float) FADE_MS, 0f, 1f);
		return t * t * (3 - 2 * t);
	}

	/** Scales a colour's alpha channel by {@code factor} (for the fade-in). */
	private static int mul(int argb, float factor) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * factor);
		return (alpha << 24) | (argb & 0xFFFFFF);
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}
}
