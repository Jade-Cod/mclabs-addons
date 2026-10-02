package dev.jade.labsaddons.carnage;

import dev.jade.labsaddons.carnage.CarnageMenu.Page;
import dev.jade.labsaddons.carnage.CarnageTracker.Daily;
import dev.jade.labsaddons.carnage.CarnageTracker.HuntSet;
import dev.jade.labsaddons.carnage.CarnageTracker.Mission;
import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.Durations;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.PlayerSkinCache;
import dev.jade.labsaddons.hud.TimeFormat;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Locale;

/**
 * The {@code /carnage} menus, redrawn: one panel with a tab per page, standing in for all six
 * of the server's chests and clicking through to their own nav slots.
 *
 * <p>Motion is kept to what explains something, and none of it waits on the player. The tab
 * underline slides to the page you picked, so the strip says where you went. A new page's
 * content rises 4px into place with a short stagger, and its bars fill from empty. Hover is
 * instant, because it happens constantly. A press flashes for {@link #PRESS_MS} because the
 * server takes a moment to answer and the click needs to show it was heard.
 *
 * <p>The server reopens the chest on every page change. {@link #holdKey} gives every Carnage
 * page the same key, so the board holds across that reopen instead of letting the real chest
 * flash through for a frame.
 */
public final class CarnageBoard extends CasinoPanel {
	public static final CarnageBoard INSTANCE = new CarnageBoard();

	// --- palette: the server's own blood red and pumpkin, on the shared dark panel ---
	private static final int PUMPKIN = 0xFFFF8C1A;
	private static final int BLOOD = 0xFFE0455A;
	private static final int GHOST = 0xFFB69CFF;
	private static final int GOLD = 0xFFFFD45E;
	private static final int SILVER = 0xFFC9D1D9;
	private static final int BRONZE = 0xFFE0915A;
	private static final int CARD = 0x12FFFFFF;
	private static final int CARD_HOT = 0x22FFFFFF;
	private static final String SOUL_NAME = "Soul of Fright";
	/** Lighter than the casino track: an empty bar has to read against the panel, not vanish into it. */
	private static final int RAIL = 0xFF333947;

	// --- layout ---
	private static final int H = 220;
	private static final int TAB_Y = 20;
	private static final int TAB_H = 12;
	private static final int BODY_Y = 40;
	private static final int BODY_W = PANEL_W - PAD * 2;
	private static final int FOOT_Y = H - PAD - 13;
	private static final int BAR_H = 4;
	private static final String[] TAB_LABELS = {"Today", "Hunt", "Shop", "Leaders", "Bestiary", "Tags"};

	// --- motion ---
	private static final long TAB_SLIDE_MS = 200;
	private static final long ENTER_MS = 220;
	private static final long STAGGER_MS = 30;
	private static final long BAR_MS = 320;
	private static final long PRESS_MS = 140;
	private static final float RISE_PX = 4f;
	/** A gap this long between frames means the menu was closed; the next frame is a fresh open. */
	private static final long REOPEN_GAP_MS = 250;
	/** Items and skins can't be drawn translucent, so they wait until their element is mostly in. */
	private static final float ICON_CUTOFF = 0.5f;

	private long now;
	private long lastFrameMs;
	private String shownTitle = "";
	private long enterMs;
	private float underlineX = -1;
	private float underlineW;
	private float slideFromX;
	private float slideFromW;
	private long slideMs;
	private int pressedSlot = -1;
	private long pressedMs;
	/** The current element's entrance, 0..1, applied to every colour drawn inside it. */
	private float alpha = 1f;

	private CarnageBoard() {
	}

	// --- what the casino panel asks ---

	@Override
	protected boolean enabled() {
		return LabsAddonsConfig.get().carnageOverlay;
	}

	@Override
	protected int panelHeight() {
		return H;
	}

	@Override
	protected boolean looksLike(ScreenHandler handler) {
		return CarnageMenu.looksLike(nameOf(handler, CarnageMenu.NAV_DASHBOARD), nameOf(handler, CarnageMenu.COUNTDOWN));
	}

	@Override
	protected boolean titleAllows(String title) {
		return Page.of(title) != null;
	}

	@Override
	protected boolean parses(List<SlotView> slots) {
		return CarnageMenu.looksLike(SlotView.nameAt(slots, CarnageMenu.NAV_DASHBOARD),
				SlotView.nameAt(slots, CarnageMenu.COUNTDOWN));
	}

	@Override
	protected boolean parses(List<SlotView> slots, String title) {
		return Page.of(title) != null && parses(slots);
	}

	/** Every page the same, so the board holds across the reopen a tab click causes. */
	@Override
	protected String holdKey(String title) {
		return Page.of(title) == null ? title : "carnage";
	}

	@Override
	protected void onClick(int slot) {
		pressedSlot = slot;
		pressedMs = Util.getMeasuringTimeMs();
	}

	// --- frame ---

	@Override
	protected void draw(DrawContext context, TextRenderer font, List<SlotView> slots, String title, float deviceScale) {
		now = Util.getMeasuringTimeMs();
		Page page = Page.of(title);
		boolean freshOpen = now - lastFrameMs > REOPEN_GAP_MS;
		lastFrameMs = now;
		if (freshOpen || !title.equals(shownTitle)) {
			enterMs = now;
			shownTitle = title;
			moveUnderline(font, page, freshOpen);
		}

		alpha = 1f;
		header(context, font, slots);
		tabs(context, font, page);
		switch (page) {
			case DASHBOARD -> dashboard(context, font, slots);
			case HUNT -> hunt(context, font, slots);
			case SHOP -> shop(context, font, slots);
			case LEADERBOARD -> leaderboard(context, font, slots, title);
			case BESTIARY -> bestiary(context, font, slots);
			case TAGS -> tags(context, font, slots, title);
		}
		alpha = 1f;
	}

	private void header(DrawContext context, TextRenderer font, List<SlotView> slots) {
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(PAD, 2);
		context.getMatrices().scale(0.75f, 0.75f);
		context.drawItem(new ItemStack(Items.JACK_O_LANTERN), 0, 0);
		context.getMatrices().popMatrix();
		text(context, font, "HALLOWEEN CARNAGE", PAD + 16, 5, PUMPKIN);

		String[] stage = CarnageMenu.stageLine(slots);
		if (stage != null) {
			String left = TimeFormat.hms(Durations.parseMs(stage[1])) + " left";
			int x = PANEL_W - PAD - font.getWidth(left);
			text(context, font, left, x, 5, TEXT_DIM);
			String name = stage[0].toUpperCase(Locale.ROOT) + "  ";
			text(context, font, name, x - font.getWidth(name), 5, BLOOD);
		}
		context.fill(PAD, 16, PANEL_W - PAD, 17, shade(PUMPKIN, 0x40));
	}

	// --- tabs ---

	/** {x, w} of tab {@code i}: each as wide as its label, the spare width shared out evenly. */
	private static int[] tab(TextRenderer font, int i) {
		int labels = 0;
		for (String label : TAB_LABELS) {
			labels += font.getWidth(label);
		}
		int spare = (BODY_W - labels) / TAB_LABELS.length;
		int x = PAD;
		for (int j = 0; j < i; j++) {
			x += font.getWidth(TAB_LABELS[j]) + spare;
		}
		return new int[]{x, font.getWidth(TAB_LABELS[i]) + spare};
	}

	/**
	 * Where tab {@code i}'s label starts, centred on its ink. The underline and hover box are
	 * drawn out from this, so all three share one centre.
	 */
	private static int labelX(TextRenderer font, int i) {
		int[] bounds = tab(font, i);
		return bounds[0] + (bounds[1] - ink(font, TAB_LABELS[i])) / 2;
	}

	/** A label's drawn width: the measured width ends with the gap after the last letter. */
	private static int ink(TextRenderer font, String label) {
		return font.getWidth(label) - 1;
	}

	private void tabs(DrawContext context, TextRenderer font, Page active) {
		Page[] pages = Page.values();
		for (int i = 0; i < pages.length; i++) {
			int[] bounds = tab(font, i);
			int x = bounds[0];
			int w = bounds[1];
			boolean isActive = pages[i] == active;
			boolean hot = !isActive && hovered(x, TAB_Y, w, TAB_H + 2);
			float press = pressAmount(pages[i].navSlot);
			String label = TAB_LABELS[i];
			int labelX = labelX(font, i);
			int pad = labelX - x - 1;
			int boxW = ink(font, label) + 2 * pad;
			if (press > 0) {
				HudObject.drawRoundedRect(context, labelX - pad, TAB_Y - 1, boxW, TAB_H + 2, shade(PUMPKIN, (int) (0x40 * press)));
			} else if (hot) {
				HudObject.drawRoundedRect(context, labelX - pad, TAB_Y - 1, boxW, TAB_H + 2, ROW_HOVER);
			}
			text(context, font, label, labelX, TAB_Y + 2,
					isActive ? 0xFFFFFFFF : hot ? TEXT : TEXT_DIM);
			if (!isActive) {
				clickable(x, TAB_Y - 1, w, TAB_H + 2, pages[i].navSlot);
			}
		}
		context.fill(PAD, TAB_Y + TAB_H + 2, PANEL_W - PAD, TAB_Y + TAB_H + 3, DIVIDER);
		float t = ease(progress(slideMs, TAB_SLIDE_MS));
		float x = lerp(slideFromX, underlineX, t);
		float w = lerp(slideFromW, underlineW, t);
		EditorPainter.pill(context, Math.round(x), TAB_Y + TAB_H + 1, Math.round(w), 2, PUMPKIN);
	}

	/** Slides from wherever the underline is drawn right now, so a quick second click retargets smoothly. */
	private void moveUnderline(TextRenderer font, Page page, boolean jump) {
		int i = page.ordinal();
		float toW = ink(font, TAB_LABELS[i]) + 6;
		float toX = labelX(font, i) - 3;
		if (jump || underlineX < 0) {
			slideFromX = toX;
			slideFromW = toW;
		} else {
			float t = ease(progress(slideMs, TAB_SLIDE_MS));
			slideFromX = lerp(slideFromX, underlineX, t);
			slideFromW = lerp(slideFromW, underlineW, t);
		}
		underlineX = toX;
		underlineW = toW;
		slideMs = now;
	}

	// --- Today ---

	/**
	 * One stack, most important first: the score as the hero, then the missions as full-width
	 * rows, then everything else as a single footer line.
	 *
	 * <p>The raffle has no bar of its own because it is the same number: a ticket every
	 * rising cost in today's score. Its thresholds are notched into the score bar, so
	 * filling the bar is visibly earning tickets on the way to the goal.
	 *
	 * <p>Anything with more to say than fits (raffle prizes, the "all three" bonus, the streak
	 * bonus) shows the server's own tooltip on hover rather than being squeezed in.
	 */
	private void dashboard(DrawContext context, TextRenderer font, List<SlotView> slots) {
		Daily daily = CarnageReader.dashboard(slots, System.currentTimeMillis());
		if (daily == null) {
			return;
		}
		hero(context, font, slots, daily);
		raffle(context, font, slots, daily);
		context.fill(PAD, BODY_Y + 50, PANEL_W - PAD, BODY_Y + 51, DIVIDER);
		missions(context, font, slots, daily.missions());
		footer(context, font, slots, daily);
	}

	private void hero(DrawContext context, TextRenderer font, List<SlotView> slots, Daily daily) {
		int y = BODY_Y + 2;
		enter(context, 0);
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(PAD, y);
		context.getMatrices().scale(2f, 2f);
		// After the daily goal the menu tracks the repeatable goal it turned into, so this does too.
		boolean repeat = daily.goalDone();
		double current = repeat ? daily.repeatProgress() : daily.score();
		int target = repeat ? daily.repeatGoal() : daily.scoreGoal();
		String score = number(current);
		text(context, font, score, 0, 0, 0xFFFFFFFF);
		context.getMatrices().popMatrix();
		String ofGoal = "/ " + number(target);
		int ofGoalX = PAD + font.getWidth(score) * 2 + 5;
		text(context, font, ofGoal, ofGoalX, y + 7, TEXT_FAINT);

		// The reward takes what is left of the line; a five-figure score leaves less, so the
		// "Goal →" label goes first and then the reward is trimmed, rather than overlapping.
		boolean goalMet = target > 0 && current >= target;
		String reward = loreAfter(slots, "Daily Score Goal", "Reward:");
		if (!reward.isEmpty()) {
			int right = PANEL_W - PAD;
			int room = right - (ofGoalX + font.getWidth(ofGoal) + 8);
			String label = goalMet ? "✔ " : repeat ? "Repeat → " : "Goal → ";
			if (font.getWidth(label + reward) > room) {
				label = goalMet ? "✔ " : "";
			}
			String shown = fit(font, reward, room - font.getWidth(label));
			int rewardW = font.getWidth(shown);
			text(context, font, shown, right - rewardW, y + 7, goalMet ? WIN : TEXT);
			text(context, font, label, right - rewardW - font.getWidth(label), y + 7, goalMet ? WIN : TEXT_FAINT);
		}
		int barY = y + 20;
		int barH = 6;
		bar(context, PAD, barY, BODY_W, barH, fraction(current, target), goalMet ? WIN : PUMPKIN, 0);
		if (!repeat) {
			notches(context, barY, barH, daily);
		}
		leave(context);
		int goalSlot = slotNamed(slots, "Daily Score Goal");
		if (goalSlot >= 0 && hovered(PAD, y, BODY_W, 28)) {
			tooltip(context, font, stackAt(goalSlot));
		}
	}

	/** A 1px cut in the score bar at every raffle ticket threshold; each ticket costs more. */
	private void notches(DrawContext context, int y, int h, Daily daily) {
		if (daily.ticketBase() <= 0 || daily.scoreGoal() <= 0) {
			return;
		}
		for (int n = 1; n <= daily.maxTickets(); n++) {
			double points = CarnageTracker.ticketThreshold(daily, n);
			if (points >= daily.scoreGoal()) {
				return;
			}
			int x = PAD + (int) Math.round(BODY_W * points / daily.scoreGoal());
			context.fill(x, y, x + 1, y + h, c(0xFF1A1D24));
		}
	}

	private void raffle(DrawContext context, TextRenderer font, List<SlotView> slots, Daily daily) {
		int y = BODY_Y + 34;
		enter(context, 1);
		int x = PAD;
		if (daily.maxTickets() > 0 && daily.tickets() >= daily.maxTickets()) {
			text(context, font, "✔ all " + daily.maxTickets() + " raffle tickets earned", x, y, WIN);
		} else if (daily.maxTickets() > 0) {
			String have = String.valueOf(daily.tickets());
			text(context, font, have, x, y, PUMPKIN);
			x += font.getWidth(have);
			String rest = " of " + daily.maxTickets() + " raffle tickets";
			text(context, font, rest, x, y, TEXT_DIM);
			x += font.getWidth(rest);
			// The menu's own "Next Ticket: 1,448.09/1,525.79", so this needs no cost schedule.
			double[] next = parseFraction(loreValue(slots, "Daily Raffle", "Next Ticket:"));
			if (next != null && next[1] > next[0]) {
				text(context, font, " · next in " + number(Math.ceil(next[1] - next[0])), x, y, TEXT_FAINT);
			}
		}
		String draws = loreValue(slots, "Daily Raffle", "Drawing in:");
		if (!draws.isEmpty()) {
			String when = "draws in " + TimeFormat.hms(Durations.parseMs(draws));
			text(context, font, when, PANEL_W - PAD - font.getWidth(when), y, TEXT_FAINT);
		}
		leave(context);
		int raffleSlot = slotNamed(slots, "Daily Raffle");
		if (raffleSlot >= 0 && hovered(PAD, y - 2, BODY_W, 12)) {
			tooltip(context, font, stackAt(raffleSlot));
		}
	}

	private void missions(DrawContext context, TextRenderer font, List<SlotView> slots, List<Mission> missions) {
		int y = BODY_Y + 57;
		long done = missions.stream().filter(Mission::done).count();
		enter(context, 2);
		text(context, font, "MISSIONS", PAD, y, TEXT_FAINT);
		String count = done + "/" + missions.size();
		int x = PAD + font.getWidth("MISSIONS ");
		text(context, font, count, x, y, done == missions.size() && !missions.isEmpty() ? WIN : TEXT_DIM);
		leave(context);

		int missionSlot = slotNamed(slots, "Daily Missions");
		int rowH = 24;
		y += 11;
		for (int i = 0; i < missions.size(); i++) {
			Mission mission = missions.get(i);
			int rowY = y + i * (rowH + 2);
			enter(context, 3 + i);
			boolean hot = hovered(PAD, rowY, BODY_W, rowH);
			HudObject.drawRoundedRect(context, PAD, rowY, BODY_W, rowH, c(hot ? CARD_HOT : CARD));
			int accent = mission.done() ? WIN : PUMPKIN;
			String name = (mission.done() ? "✔ " : "") + mission.name();
			text(context, font, name, PAD + 6, rowY + 4, mission.done() ? WIN : TEXT);
			if (!mission.reward().isEmpty()) {
				String reward = fit(font, mission.reward(), BODY_W - 22 - font.getWidth(name));
				text(context, font, reward, PANEL_W - PAD - 6 - font.getWidth(reward), rowY + 4, TEXT_FAINT);
			}
			String progress = mission.current() + " / " + mission.target();
			int countX = PANEL_W - PAD - 6 - font.getWidth(progress);
			text(context, font, progress, countX, rowY + 14, mission.done() ? WIN : TEXT_DIM);
			bar(context, PAD + 6, rowY + 16, countX - PAD - 12, BAR_H,
					fraction(mission.current(), mission.target()), accent, 3 + i);
			leave(context);
			if (hot && missionSlot >= 0) {
				tooltip(context, font, stackAt(missionSlot));
			}
		}
		String bonus = allMissionsBonus(slots);
		if (!bonus.isEmpty()) {
			enter(context, 3 + missions.size());
			int bonusY = y + missions.size() * (rowH + 2) + 3;
			String label = "Finish all three → ";
			text(context, font, label, PAD, bonusY, TEXT_FAINT);
			text(context, font, fit(font, bonus, BODY_W - font.getWidth(label)), PAD + font.getWidth(label), bonusY, TEXT_DIM);
			leave(context);
		}
	}

	private void footer(DrawContext context, TextRenderer font, List<SlotView> slots, Daily daily) {
		enter(context, 6);
		int x = PAD;
		int y = FOOT_Y + 3;
		x = pair(context, font, x, y, "Goal streak ", daily.goalsDone() + "/" + daily.goalsTotal());
		SlotView clock = SlotView.at(slots, CarnageMenu.COUNTDOWN);
		String dayEnds = clock == null ? "" : valueAfter(clock.lore(), "Current day ends in:");
		if (!dayEnds.isEmpty()) {
			text(context, font, "  ·  ", x, y, TEXT_FAINT);
			x += font.getWidth("  ·  ");
			pair(context, font, x, y, "resets in ", TimeFormat.hms(Durations.parseMs(dayEnds)));
		}
		claimButton(context, font, CarnageMenu.CLAIM_SOULS);
		leave(context);
		int bonusSlot = slotNamed(slots, "Daily Score Goal Bonus");
		if (bonusSlot >= 0 && hovered(PAD, FOOT_Y, 110, 13)) {
			tooltip(context, font, stackAt(bonusSlot));
		}
	}

	/** A faint label and its value, returning where the next piece goes. */
	private int pair(DrawContext context, TextRenderer font, int x, int y, String label, String value) {
		text(context, font, label, x, y, TEXT_FAINT);
		x += font.getWidth(label);
		text(context, font, value, x, y, TEXT_DIM);
		return x + font.getWidth(value);
	}

	/** "0.2 Event Points + 100 Store Points": the bullets under the missions' "Complete all" line. */
	private static String allMissionsBonus(List<SlotView> slots) {
		int slot = slotNamed(slots, "Daily Missions");
		if (slot < 0) {
			return "";
		}
		StringBuilder bonus = new StringBuilder();
		boolean inSummary = false;
		for (String raw : slots.get(slot).lore()) {
			String line = raw.trim();
			if (line.toLowerCase(Locale.ROOT).contains("complete all")) {
				inSummary = true;
			} else if (inSummary && line.startsWith("•")) {
				bonus.append(bonus.isEmpty() ? "" : " + ").append(line.substring(1).trim());
			} else if (inSummary) {
				break;
			}
		}
		return bonus.toString();
	}

	private static int slotNamed(List<SlotView> slots, String name) {
		for (SlotView slot : slots) {
			if (slot.name().trim().equals(name)) {
				return slot.index();
			}
		}
		return -1;
	}

	// --- Hunt ---

	private void hunt(DrawContext context, TextRenderer font, List<SlotView> slots) {
		CarnageTracker.Hunt hunt = CarnageReader.hunt(slots);
		int y = BODY_Y;
		if (hunt != null) {
			enter(context, 0);
			caption(context, font, PAD, y, BODY_W, "MASTER HUNTER", hunt.found() + " / " + hunt.total());
			bar(context, PAD, y + 11, BODY_W, fraction(hunt.found(), hunt.total()), hunt.found() >= hunt.total() ? WIN : GHOST, 0);
			leave(context);
		}
		y += 22;
		int cardW = (BODY_W - 6) / 2;
		int cardH = 24;
		int index = 0;
		for (SlotView slot : slots) {
			HuntSet set = CarnageReader.huntSet(slot.name());
			if (set == null || set.name().equalsIgnoreCase("Master Hunter")) {
				continue;
			}
			int cx = PAD + (index % 2) * (cardW + 6);
			int cy = y + (index / 2) * (cardH + 3);
			enter(context, 1 + index / 2);
			boolean complete = set.found() >= set.total();
			boolean hot = hovered(cx, cy, cardW, cardH);
			HudObject.drawRoundedRect(context, cx, cy, cardW, cardH, c(hot ? CARD_HOT : CARD));
			icon(context, stackAt(slot.index()), cx + 3, cy + 3);
			String count = set.found() + "/" + set.total();
			text(context, font, fit(font, set.name(), cardW - 36 - font.getWidth(count)), cx + 23, cy + 4,
					complete ? WIN : TEXT);
			text(context, font, count, cx + cardW - 5 - font.getWidth(count), cy + 4, complete ? WIN : TEXT_DIM);
			bar(context, cx + 23, cy + 15, cardW - 28, fraction(set.found(), set.total()), complete ? WIN : GHOST, 1 + index / 2);
			leave(context);
			if (hot) {
				tooltip(context, font, stackAt(slot.index()));
			}
			index++;
		}
		enter(context, 7);
		text(context, font, "hidden around Spawn · turn in at the purple pumpkin", PAD, FOOT_Y + 3, TEXT_FAINT);
		leave(context);
	}

	// --- Shop ---

	private void shop(DrawContext context, TextRenderer font, List<SlotView> slots) {
		List<CarnageMenu.ShopItem> items = CarnageMenu.shop(slots);
		int souls = soulsHeld();
		int cols = 2;
		int cardW = (BODY_W - 6) / cols;
		int cardH = 23;
		for (int i = 0; i < items.size(); i++) {
			CarnageMenu.ShopItem item = items.get(i);
			int x = PAD + (i % cols) * (cardW + 6);
			int y = BODY_Y + (i / cols) * (cardH + 3);
			if (y + cardH > FOOT_Y - 3) {
				break;
			}
			enter(context, i / cols + i % cols);
			boolean hot = hovered(x, y, cardW, cardH);
			float press = pressAmount(item.slot());
			HudObject.drawRoundedRect(context, x, y, cardW, cardH,
					press > 0 ? shade(PUMPKIN, (int) (0x50 * press)) : c(hot ? CARD_HOT : CARD));
			if (hot) {
				EditorPainter.outline(context, x, y, cardW, cardH, shade(PUMPKIN, 0x90));
			}
			icon(context, stackAt(item.slot()), x + 4, y + 4);
			String cost = item.cost() + (item.cost() == 1 ? " soul" : " souls");
			boolean affordable = souls >= item.cost();
			text(context, font, fit(font, item.name(), cardW - 30), x + 24, y + 3,
					hot ? 0xFFFFFFFF : affordable ? TEXT : TEXT_FAINT);
			text(context, font, cost, x + 24, y + 13, affordable ? PUMPKIN : LOSS);
			clickable(x, y, cardW, cardH, item.slot());
			leave(context);
			if (hot) {
				tooltip(context, font, stackAt(item.slot()));
			}
		}
		enter(context, 5);
		String held = souls + (souls == 1 ? " soul" : " souls");
		text(context, font, "You have ", PAD, FOOT_Y + 3, TEXT_FAINT);
		text(context, font, held, PAD + font.getWidth("You have "), FOOT_Y + 3, PUMPKIN);
		claimButton(context, font, CarnageMenu.has(slots, CarnageMenu.SHOP_CLAIM, "Carnage Shop")
				? CarnageMenu.SHOP_CLAIM : CarnageMenu.CLAIM_SOULS);
		leave(context);
	}

	// --- Leaders ---

	private void leaderboard(DrawContext context, TextRenderer font, List<SlotView> slots, String title) {
		List<CarnageMenu.Leader> leaders = CarnageMenu.leaders(slots);
		String scope = SlotView.nameAt(slots, CarnageMenu.LEADERBOARD_SCOPE).trim();
		enter(context, 0);
		caption(context, font, PAD, BODY_Y, BODY_W, scope.isEmpty() ? "LEADERBOARD" : scope.toUpperCase(Locale.ROOT),
				"top 3 win daily");
		leave(context);
		String self = MinecraftClient.getInstance().getSession().getUsername();
		int rowH = 13;
		for (int i = 0; i < leaders.size(); i++) {
			CarnageMenu.Leader leader = leaders.get(i);
			int y = BODY_Y + 11 + i * rowH;
			enter(context, 1 + i);
			boolean hot = hovered(PAD, y, BODY_W, rowH);
			boolean isSelf = leader.player().equalsIgnoreCase(self);
			if (isSelf || hot) {
				HudObject.drawRoundedRect(context, PAD, y, BODY_W, rowH - 1, isSelf ? c(shade(PUMPKIN, 0x30)) : c(CARD_HOT));
			}
			int rankColor = switch (leader.rank()) {
				case 1 -> GOLD;
				case 2 -> SILVER;
				case 3 -> BRONZE;
				default -> TEXT_FAINT;
			};
			String rank = "#" + leader.rank();
			text(context, font, rank, PAD + 4, y + 2, rankColor);
			if (alpha > ICON_CUTOFF) {
				PlayerSkinDrawer.draw(context, PlayerSkinCache.skin(leader.player()), PAD + 26, y + 1, 9);
			}
			text(context, font, leader.player(), PAD + 40, y + 2, leader.rank() <= 3 ? TEXT : TEXT_DIM);
			String points = leader.points() + " pts";
			text(context, font, points, PANEL_W - PAD - 4 - font.getWidth(points), y + 2, leader.rank() <= 3 ? rankColor : TEXT_DIM);
			leave(context);
			if (hot) {
				tooltip(context, font, stackAt(leader.slot()));
			}
		}
		enter(context, 10);
		int[] pages = CarnageMenu.pageOf(title);
		pager(context, font, slots, pages);
		String toggle = CarnageMenu.nextScope(SlotView.at(slots, CarnageMenu.LEADERBOARD_SCOPE));
		button(context, font, PAD, FOOT_Y, 64, 13, toggle,
				SlotView.at(slots, CarnageMenu.LEADERBOARD_SCOPE) == null ? -1 : CarnageMenu.LEADERBOARD_SCOPE, PUMPKIN);
		leave(context);
	}

	// --- Bestiary ---

	private void bestiary(DrawContext context, TextRenderer font, List<SlotView> slots) {
		List<CarnageMenu.Monster> monsters = CarnageMenu.bestiary(slots);
		long found = monsters.stream().filter(CarnageMenu.Monster::discovered).count();
		enter(context, 0);
		caption(context, font, PAD, BODY_Y, BODY_W, "DISCOVERED", found + " / " + monsters.size());
		leave(context);
		int tile = 26;
		int gap = 4;
		int cols = 9;
		int left = PAD + (BODY_W - (cols * tile + (cols - 1) * gap)) / 2;
		for (int i = 0; i < monsters.size(); i++) {
			CarnageMenu.Monster monster = monsters.get(i);
			int x = left + (i % cols) * (tile + gap);
			int y = BODY_Y + 12 + (i / cols) * (tile + gap);
			enter(context, 1 + i / cols + i % cols / 3);
			boolean hot = hovered(x, y, tile, tile);
			boolean locked = !monster.stage().isEmpty();
			int bg = monster.discovered() ? shade(BLOOD, 0x38) : locked ? 0x0AFFFFFF : CARD;
			HudObject.drawRoundedRect(context, x, y, tile, tile, c(hot ? CARD_HOT : bg));
			if (hot) {
				EditorPainter.outline(context, x, y, tile, tile, shade(BLOOD, 0xB0));
			}
			if (monster.discovered()) {
				icon(context, stackAt(monster.slot()), x + 5, y + 5);
			} else {
				String mark = locked ? monster.stage() : "?";
				text(context, font, mark, x + (tile - font.getWidth(mark)) / 2, y + 9, locked ? TEXT_FAINT : TEXT_DIM);
			}
			leave(context);
			if (hot) {
				tooltip(context, font, stackAt(monster.slot()));
			}
		}
		enter(context, 6);
		text(context, font, "? can be hunted now · II, III, IV arrive in later stages", PAD, FOOT_Y + 3, TEXT_FAINT);
		leave(context);
	}

	// --- Tags ---

	private void tags(DrawContext context, TextRenderer font, List<SlotView> slots, String title) {
		List<CarnageMenu.Tag> tags = CarnageMenu.tags(slots);
		enter(context, 0);
		caption(context, font, PAD, BODY_Y, BODY_W, "HALLOWEEN TAGS", "from daily goals and missions");
		leave(context);
		int cols = 3;
		int cardW = (BODY_W - (cols - 1) * 4) / cols;
		int cardH = 34;
		for (int i = 0; i < tags.size(); i++) {
			CarnageMenu.Tag tag = tags.get(i);
			int x = PAD + (i % cols) * (cardW + 4);
			int y = BODY_Y + 12 + (i / cols) * (cardH + 4);
			enter(context, 1 + i / cols + i % cols);
			boolean hot = hovered(x, y, cardW, cardH);
			HudObject.drawRoundedRect(context, x, y, cardW, cardH,
					c(tag.unlocked() ? shade(WIN, 0x22) : tag.next() ? shade(PUMPKIN, 0x26) : hot ? CARD_HOT : CARD));
			if (tag.next()) {
				EditorPainter.outline(context, x, y, cardW, cardH, c(shade(PUMPKIN, 0x90)));
			}
			Text name = tagLabel(stackAt(tag.slot()), tag.name());
			int nameW = font.getWidth(name);
			if (nameW <= cardW - 8) {
				styled(context, font, name, x + (cardW - nameW) / 2, y + 8);
			} else {
				String plain = fit(font, tag.name(), cardW - 8);
				text(context, font, plain, x + (cardW - font.getWidth(plain)) / 2, y + 8, TEXT);
			}
			String status = tag.unlocked() ? "✔ unlocked" : tag.status();
			text(context, font, status, x + (cardW - font.getWidth(status)) / 2, y + 20,
					tag.unlocked() ? WIN : tag.next() ? PUMPKIN : TEXT_FAINT);
			leave(context);
			if (hot) {
				tooltip(context, font, stackAt(tag.slot()));
			}
		}
		enter(context, 5);
		pager(context, font, slots, CarnageMenu.pageOf(title));
		leave(context);
	}

	// --- shared pieces ---

	/** ◀ page n/m ▶, centred in the footer; a missing arrow is drawn dead rather than hidden. */
	private void pager(DrawContext context, TextRenderer font, List<SlotView> slots, int[] pages) {
		int arrowW = 18;
		String label = pages == null ? "" : pages[0] + " / " + pages[1];
		int labelW = Math.max(36, font.getWidth(label) + 10);
		int x = (PANEL_W - labelW - arrowW * 2) / 2;
		boolean hasPrev = CarnageMenu.has(slots, CarnageMenu.PREVIOUS_PAGE, "Previous Page");
		boolean hasNext = CarnageMenu.has(slots, CarnageMenu.NEXT_PAGE, "Next Page");
		button(context, font, x, FOOT_Y, arrowW, 13, "◀", hasPrev ? CarnageMenu.PREVIOUS_PAGE : -1, PUMPKIN);
		text(context, font, label, x + arrowW + (labelW - font.getWidth(label)) / 2, FOOT_Y + 3, TEXT_DIM);
		button(context, font, x + arrowW + labelW, FOOT_Y, arrowW, 13, "▶", hasNext ? CarnageMenu.NEXT_PAGE : -1, PUMPKIN);
	}

	/**
	 * Souls of Fright in your inventory: fermented spider eyes the server names "Soul of Fright"
	 * (a plain spider eye doesn't count). The menu never states the balance, so it's counted here.
	 */
	// ponytail: matched by name; the stack also carries mythicmobs:type=SoulOfFright in its
	// custom data, the sturdier key if MCLabs ever renames it.
	private static int soulsHeld() {
		var player = MinecraftClient.getInstance().player;
		if (player == null) {
			return 0;
		}
		var inventory = player.getInventory();
		int souls = 0;
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.isOf(Items.FERMENTED_SPIDER_EYE) && stack.getName().getString().equals(SOUL_NAME)) {
				souls += stack.getCount();
			}
		}
		return souls;
	}

	/** Hidden once you've claimed: the menu offers it forever, but it's once per event. */
	private void claimButton(DrawContext context, TextRenderer font, int slot) {
		if (CarnageTracker.freeSoulsClaimed()) {
			return;
		}
		int w = 78;
		button(context, font, PANEL_W - PAD - w, FOOT_Y, w, 13, "FREE SOULS", slot, PUMPKIN);
	}

	/** A small section label with a note right-aligned at {@code width}. */
	private void caption(DrawContext context, TextRenderer font, int x, int y, int width, String label, String note) {
		text(context, font, label, x, y, TEXT_FAINT);
		if (note != null && !note.isEmpty()) {
			text(context, font, note, x + width - font.getWidth(note), y, TEXT_DIM);
		}
	}

	/** A bar whose fill grows in with its element, {@code order} staggered like the rest. */
	private void bar(DrawContext context, int x, int y, int w, double fraction, int color, int order) {
		bar(context, x, y, w, BAR_H, fraction, color, order);
	}

	private void bar(DrawContext context, int x, int y, int w, int h, double fraction, int color, int order) {
		EditorPainter.pill(context, x, y, w, h, c(RAIL));
		float grow = ease(progress(enterMs + order * STAGGER_MS, BAR_MS));
		int filled = (int) Math.round(w * Math.clamp(fraction, 0.0, 1.0) * grow);
		if (filled >= h) {
			EditorPainter.pill(context, x, y, filled, h, c(color));
		} else if (filled > 0) {
			context.fill(x, y, x + filled, y + h, c(color));
		}
	}

	/** Trimmed to {@code width} with an ellipsis, so a cut name reads as cut on purpose. */
	private static String fit(TextRenderer font, String value, int width) {
		if (font.getWidth(value) <= width) {
			return value;
		}
		return font.trimToWidth(value, width - font.getWidth("…")).stripTrailing() + "…";
	}

	private void icon(DrawContext context, ItemStack stack, int x, int y) {
		if (alpha > ICON_CUTOFF && stack != null && !stack.isEmpty()) {
			context.drawItem(stack, x, y);
		}
	}

	/**
	 * A tag exactly as chat shows it: the server's own colours, weights and glyphs, minus the
	 * trailing " Tag" its menu adds. Falls back to the plain name if the parts aren't the
	 * usual "[", name, "] ", "Tag".
	 */
	static Text tagLabel(ItemStack stack, String plain) {
		List<Text> parts = stack == null || stack.isEmpty() ? List.of() : stack.getName().getSiblings();
		if (parts.size() < 2 || !parts.getLast().getString().trim().equals("Tag")) {
			return Text.literal(plain);
		}
		MutableText label = Text.empty().setStyle(stack.getName().getStyle());
		for (int i = 0; i < parts.size() - 1; i++) {
			Text part = parts.get(i);
			label.append(i == parts.size() - 2
					? Text.literal(part.getString().stripTrailing()).setStyle(part.getStyle())
					: part);
		}
		return label;
	}

	/** Styled text keeps its own colours; only the entrance fade is applied. */
	private void styled(DrawContext context, TextRenderer font, Text value, int x, int y) {
		int faded = c(0xFFFFFFFF);
		if ((faded >>> 24) >= 8) {
			context.drawText(font, value, x, y, faded, false);
		}
	}

	private void text(DrawContext context, TextRenderer font, String value, int x, int y, int color) {
		int faded = c(color);
		if ((faded >>> 24) >= 8) {
			context.drawText(font, value, x, y, faded, false);
		}
	}

	// --- motion ---

	/** Starts element {@code order}: it rises into place and fades in, a stagger step behind the one before. */
	private void enter(DrawContext context, int order) {
		alpha = ease(progress(enterMs + order * STAGGER_MS, ENTER_MS));
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(0, (1f - alpha) * RISE_PX);
	}

	private void leave(DrawContext context) {
		context.getMatrices().popMatrix();
		alpha = 1f;
	}

	/** A colour at the current element's entrance. */
	private int c(int argb) {
		int a = Math.round((argb >>> 24) * alpha);
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	private float pressAmount(int slot) {
		return slot == pressedSlot ? 1f - progress(pressedMs, PRESS_MS) : 0f;
	}

	private float progress(long startMs, long durationMs) {
		return Math.clamp((now - startMs) / (float) durationMs, 0f, 1f);
	}

	/** Strong ease-out (quart): moves at once, settles gently. */
	static float ease(float t) {
		float inverse = 1f - t;
		return 1f - inverse * inverse * inverse * inverse;
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	// --- reading ---

	private static String loreAfter(List<SlotView> slots, String itemName, String marker) {
		for (SlotView slot : slots) {
			if (!slot.name().trim().equals(itemName)) {
				continue;
			}
			List<String> lore = slot.lore();
			for (int i = 0; i < lore.size() - 1; i++) {
				if (lore.get(i).trim().equals(marker)) {
					return lore.get(i + 1).trim().replaceFirst("^•\\s*", "");
				}
			}
		}
		return "";
	}

	private static String loreValue(List<SlotView> slots, String itemName, String prefix) {
		for (SlotView slot : slots) {
			if (slot.name().trim().equals(itemName)) {
				return valueAfter(slot.lore(), prefix);
			}
		}
		return "";
	}

	private static String valueAfter(List<String> lore, String prefix) {
		for (String line : lore) {
			String trimmed = line.trim();
			if (trimmed.startsWith(prefix)) {
				return trimmed.substring(prefix.length()).trim();
			}
		}
		return "";
	}

	/** "1,448.09/1,525.79" as {have, of}, or null. */
	private static double[] parseFraction(String text) {
		int slash = text.indexOf('/');
		if (slash < 0) {
			return null;
		}
		Double have = CarnageTracker.parseNumber(text.substring(0, slash).trim());
		Double of = CarnageTracker.parseNumber(text.substring(slash + 1).trim());
		return have == null || of == null ? null : new double[]{have, of};
	}

	private static double fraction(double have, double of) {
		return of <= 0 ? 0 : have / of;
	}

	private static String percent(double have, double of) {
		return of <= 0 ? "" : Math.min(100, (int) Math.floor(have * 100 / of)) + "%";
	}

	private static String number(double value) {
		return String.format(Locale.ROOT, "%,d", (long) Math.floor(value));
	}

	private static String nameOf(ScreenHandler handler, int slot) {
		if (slot < 0 || slot >= handler.slots.size()) {
			return "";
		}
		ItemStack stack = handler.slots.get(slot).getStack();
		return stack.isEmpty() ? "" : stack.getName().getString();
	}
}
