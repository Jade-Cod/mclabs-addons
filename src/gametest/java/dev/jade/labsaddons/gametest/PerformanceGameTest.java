package dev.jade.labsaddons.gametest;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.HelpScreen;
import dev.jade.labsaddons.hud.HudEditScreen;
import dev.jade.labsaddons.hud.HudObjects;
import dev.jade.labsaddons.hud.RunnerStatsScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntFunction;

import static dev.jade.labsaddons.gametest.HudEditorGameTest.check;

/**
 * The mod's cost to the game, and whether it gives memory back.
 *
 * <p>Budgets are per call on the render thread, where every microsecond comes out of the
 * frame: at 144 fps a whole frame is 6,944 us. The leak half runs the same heavy session three
 * times, each in a world joined and left, and fails any static collection that is still
 * growing on the third, which is what a leak looks like; a cache that fills up once and then
 * holds steady passes.
 */
public class PerformanceGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("labsaddons-perf");

	/*
	 * HUD budgets sit about 1.5x over what the pass costs today (~330 us, ~72 KB a frame with
	 * seven widgets up, against ~180 us and ~20 KB for the whole vanilla HUD), close enough to
	 * catch the kind of regression that matters: the per-pixel cooldown ring these replaced
	 * ran the pass to 1,060 us and 128 KB on its own.
	 */
	/** Mean cost of the whole mod HUD pass, all widgets live. */
	private static final double HUD_MEAN_BUDGET_US = 500;
	/** A frame the pass makes noticeably late. */
	private static final double HUD_P99_BUDGET_US = 1_500;
	/**
	 * Garbage per frame; at 144 fps 160 KB is ~23 MB/s for the collector. Raised from 96 KB
	 * when the Carnage widget joined the pass with every group pinned: nine widgets then made
	 * ~133 KB, most of it ordinary text drawing (~1.5 KB a line, like vanilla's own).
	 */
	private static final double HUD_BYTES_BUDGET = 160 * 1024;
	/** One chat line through every tracker, saves included. */
	private static final double CHAT_MEAN_BUDGET_US = 100;
	/** Every end-of-tick listener, which is the mod's one tick handler plus Fabric's own. */
	private static final double TICK_MEAN_BUDGET_US = 200;

	private static final int MEASURE_TICKS = 600;
	private static final int STRESS_LINES = 3_000;
	private static final int LEAK_ROUNDS = 3;
	/** Growth per round below this is churn, not a leak. */
	private static final int LEAK_SLACK = 16;
	/** Heap may drift this much across rounds before it counts. */
	private static final long HEAP_SLACK_BYTES = 24L * 1024 * 1024;

	private static final Identifier TICK_FIRST = Identifier.fromNamespaceAndPath("labsaddons-gametest", "first");
	private static final Identifier TICK_LAST = Identifier.fromNamespaceAndPath("labsaddons-gametest", "last");

	private final Random random = new Random(1177);
	/** Every budget missed, so one run reports them all rather than the first. */
	private final List<String> overBudget = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!enabled()) {
			LOG.info("[perf] skipped; run with -Pperf");
			return;
		}
		timeTicks();
		try (TestSingleplayerContext world = TestWorld.open(context)) {
			TestWorld.enterMcLabs(context, world);
			context.runOnClient(c -> LabsAddonsConfig.get().hasSeenWelcome = true);
			TestWorld.chat(context, liveState());
			carnageLive(context, world);
			context.waitTicks(20);
			int live = context.computeOnClient(c -> (int) HudObjects.all().stream()
					.filter(o -> o.settings().enabled && o.shouldRender()).count());
			LOG.info("[perf] {} of {} widgets live", live, HudObjects.all().size());
			context.takeScreenshot("perf-01-live-hud");

			measureRender(context);
			measureChat(context);
		}
		checkForLeaks(context);
		check(overBudget.isEmpty(), "over budget: " + overBudget);
	}

	/**
	 * Only with {@code -Pperf}. Its budgets are microseconds a frame, so it runs on its own:
	 * alongside two more game clients (the parallel e2e-all run) it would fail on the machine,
	 * not the mod. A {@code -Pperf} run is this test alone.
	 */
	public static boolean enabled() {
		return Boolean.getBoolean("labsaddons.perf");
	}

	// --- cost -------------------------------------------------------------------

	private void measureRender(ClientGameTestContext context) {
		// Uncapped, so runs compare: the game drops to a crawl in a background window, and a
		// slow frame costs more per call in everything, vanilla included.
		context.runOnClient(c -> {
			c.options.framerateLimit().set(260);
			c.options.enableVsync().set(false);
			c.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
		});
		context.waitTicks(100); // JIT warm-up
		context.runOnClient(c -> {
			Probe.HUD.reset();
			Probe.TICK.reset();
			Probe.VANILLA_HUD.reset();
			Probe.widgets().values().forEach(Probe::reset);
		});
		context.waitTicks(MEASURE_TICKS);
		String hud = context.computeOnClient(c -> Probe.HUD.summary());
		String tick = context.computeOnClient(c -> Probe.TICK.summary());
		LOG.info("[perf] {}", hud);
		LOG.info("[perf] {}", tick);
		String vanilla = context.computeOnClient(c -> Probe.VANILLA_HUD.summary());
		LOG.info("[perf] {}", vanilla);
		List<String> widgets = context.computeOnClient(c -> Probe.widgets().values().stream()
				.filter(p -> p.count() > 0).map(Probe::summary).toList());
		widgets.forEach(line -> LOG.info("[perf]   widget {}", line));
		check(context.computeOnClient(c -> Probe.HUD.count()) >= MEASURE_TICKS, "the HUD pass ran every frame");
		budget(context.computeOnClient(c -> Probe.HUD.meanMicros()), HUD_MEAN_BUDGET_US, "HUD pass mean us");
		budget(context.computeOnClient(c -> Probe.HUD.p99Micros()), HUD_P99_BUDGET_US, "HUD pass p99 us");
		budget(context.computeOnClient(c -> Probe.HUD.bytesPerCall()), HUD_BYTES_BUDGET, "HUD pass bytes/frame");
		budget(context.computeOnClient(c -> Probe.TICK.meanMicros()), TICK_MEAN_BUDGET_US, "end-of-tick mean us");
	}

	private void measureChat(ClientGameTestContext context) {
		TestWorld.chat(context, stressLines()); // warm-up
		context.runOnClient(c -> Probe.CHAT.reset());
		TestWorld.chat(context, stressLines());
		String chat = context.computeOnClient(c -> Probe.CHAT.summary());
		LOG.info("[perf] {}", chat);
		check(context.computeOnClient(c -> Probe.CHAT.count()) == STRESS_LINES, "every line reached the mod");
		budget(context.computeOnClient(c -> Probe.CHAT.meanMicros()), CHAT_MEAN_BUDGET_US, "chat line mean us");
	}

	private void budget(double measured, double limit, String what) {
		if (measured > limit) {
			String miss = String.format("%s %.1f > %.1f", what, measured, limit);
			LOG.error("[perf] OVER BUDGET: {}", miss);
			overBudget.add(miss);
		}
	}

	/**
	 * Brackets every end-of-tick listener in the default phase between two of our own. In a
	 * dev run that phase holds the mod's one tick handler and a few of Fabric's.
	 */
	private static void timeTicks() {
		ClientTickEvents.END_CLIENT_TICK.addPhaseOrdering(TICK_FIRST, Event.DEFAULT_PHASE);
		ClientTickEvents.END_CLIENT_TICK.addPhaseOrdering(Event.DEFAULT_PHASE, TICK_LAST);
		ClientTickEvents.END_CLIENT_TICK.register(TICK_FIRST, c -> Probe.TICK.begin());
		ClientTickEvents.END_CLIENT_TICK.register(TICK_LAST, c -> Probe.TICK.end());
	}

	// --- leaks ------------------------------------------------------------------

	private void checkForLeaks(ClientGameTestContext context) {
		List<Map<String, Integer>> sizes = new ArrayList<>();
		List<Long> heaps = new ArrayList<>();
		for (int round = 0; round < LEAK_ROUNDS; round++) {
			// A world each round: joining and leaving is where per-server state is reset, and
			// where it would pile up if it weren't.
			try (TestSingleplayerContext world = TestWorld.open(context)) {
				TestWorld.enterMcLabs(context, world);
				stressRound(context);
				// In the world, where per-session state is at its fullest.
				sizes.add(context.computeOnClient(c -> StaticSizes.snapshot()));
			}
			heaps.add(settledHeap(context));
			LOG.info("[perf] leak round {}: heap {} MB after GC, {} static collections",
					round + 1, heaps.get(round) / (1024 * 1024), sizes.get(round).size());
		}

		// A leak check over maps nothing ever reached would pass on nothing. These are the
		// ones the stress lines are aimed at; if they are empty the lines stopped matching.
		for (String field : List.of("CfChat.OPEN", "LabsAddonsConfig.instance.coinflipRecent")) {
			check(sizes.get(0).getOrDefault(field, 0) > 0, "the stress round never reached " + field);
		}

		List<String> growing = new ArrayList<>();
		Map<String, Integer> last = sizes.get(LEAK_ROUNDS - 1);
		for (Map.Entry<String, Integer> entry : last.entrySet()) {
			String field = entry.getKey();
			int first = sizes.get(0).getOrDefault(field, 0);
			int middle = sizes.get(1).getOrDefault(field, 0);
			int end = entry.getValue();
			if (end - middle > LEAK_SLACK && middle - first > LEAK_SLACK) {
				growing.add(field + " " + first + " -> " + middle + " -> " + end);
			}
		}
		for (Map.Entry<String, Integer> entry : last.entrySet()) {
			LOG.info("[perf] static {} = {}", entry.getKey(), entry.getValue());
		}
		for (String field : growing) {
			LOG.error("[perf] LEAK: {}", field);
			overBudget.add("leak " + field);
		}
		long drift = heaps.get(LEAK_ROUNDS - 1) - heaps.get(0);
		LOG.info("[perf] heap drift across rounds: {} KB", drift / 1024);
		if (drift > HEAP_SLACK_BYTES) {
			overBudget.add("heap grew " + drift / 1024 + " KB across rounds");
		}
	}

	/** One heavy session's worth: chat from thousands of strangers, screens opened and shut, frames. */
	private void stressRound(ClientGameTestContext context) {
		TestWorld.chat(context, stressLines());
		for (int i = 0; i < 5; i++) {
			context.setScreen(() -> new HudEditScreen(null));
			context.waitTicks(3);
			context.setScreen(() -> new HelpScreen(null, false));
			context.waitTicks(3);
			context.setScreen(() -> new RunnerStatsScreen(null));
			context.waitTicks(3);
			context.setScreen(() -> null);
		}
		context.waitTicks(200);
	}

	private static long settledHeap(ClientGameTestContext context) {
		for (int i = 0; i < 3; i++) {
			System.gc();
			context.waitTicks(10);
		}
		return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
	}

	// --- chat -------------------------------------------------------------------

	/** One of each thing the widgets show, so the measured pass draws a full HUD. */
	/**
	 * Every Carnage piece at once: the widget with all three groups pinned (so it draws every
	 * row, the worst case), and Kill missions open while 60 name-tagged mobs stand nearby, so
	 * the kill tracker scans and matches every tick.
	 */
	private static void carnageLive(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(c -> {
			long now = System.currentTimeMillis();
			dev.jade.labsaddons.carnage.CarnageTracker.onDashboard(new dev.jade.labsaddons.carnage.CarnageTracker.Daily(
					now + 30 * 3_600_000L, "Stage I", now + 8 * 86_400_000L, 1_840, 5_000, 3, 10, 500, 2, 16,
					List.of(new dev.jade.labsaddons.carnage.CarnageTracker.Mission("Kill 4x Poltergeist", "poltergeist", 1, 4, ""),
							new dev.jade.labsaddons.carnage.CarnageTracker.Mission("Kill 125x Geist", "geist", 61, 125, ""),
							new dev.jade.labsaddons.carnage.CarnageTracker.Mission("Kill 3x Scarecrow", "scarecrow", 0, 3, ""))));
			dev.jade.labsaddons.carnage.CarnageTracker.onHunt(new dev.jade.labsaddons.carnage.CarnageTracker.Hunt(12, 60, List.of()));
			LabsAddonsConfig.get().pinnedProgressRows.addAll(List.of("carnage:score", "carnage:missions"));
		});
		for (int i = 0; i < 60; i++) {
			world.getServer().runCommand("summon zombie ~" + (i % 10 - 5) + " ~ ~" + (i / 10 + 3)
					+ " {NoAI:1b,PersistenceRequired:1b,CustomNameVisible:1b,CustomName:\"Geist\"}");
		}
	}

	private static List<String> liveState() {
		return List.of(
				"Booster activated! Sugcarronide boosted 1.2x by Ophiliah for 30m",
				"MCLabs » The pit is currently open for another 30m:00s!",
				"Mini-Event will begin in 5 minutes!",
				"You purchased 20m of double fish",
				"The revenue rate of Chems has been boosted by 1.5x for the next 30 minutes by Ophiliah",
				"Coinflip » Ophiliah has just created a $1,200,000 Coinflip! "
						+ "Click this message or do /cf take 4164 to take it!",
				"Coinflip » Mallory has just created a $200 Coinflip! "
						+ "Click this message or do /cf take 4165 to take it!",
				"Double² » Market now open (/double). Drawing in 60 seconds. ",
				"Item Rental » You have paid $10,000 to extend your rent of Portable Raft",
				"Bounty » Bounty Hunt has started!",
				"**SUPER BREAKER ACTIVATED**",
				"Giga Drill Breaker - 50 seconds left",
				"\nCarnage » Ophiliah has just activated a Halloween Carnage Booster!\nAll Carnage Points and "
						+ "Soul of Fright drop rates are boosted by 1.5x for 60 minutes! Earn Souls of Fright by "
						+ "fighting spooky mobs at Spawn!\n");
	}

	/**
	 * Real MCLabs lines with fresh names and amounts each time, so anything keyed by a player,
	 * an item or an amount sees a stream of new keys, as it would across a long session.
	 */
	private List<String> stressLines() {
		List<IntFunction<String>> templates = List.of(
				n -> "Coinflip » " + name() + " has just created a $" + money()
						+ " Coinflip! Click this message or do /cf take " + n + " to take it!",
				n -> "Coinflip » " + name() + " has just won a $" + money() + " coinflip against " + name(),
				n -> "\nCoinflip » You have won the $" + money() + " coinflip against " + name()
						+ " and received $" + money() + "!\n",
				n -> "\nCoinflip » You have lost the $" + money() + " coinflip against " + name() + "\n",
				n -> "MCLPD » Earned " + n + " progress (x1.13) in confiscating contraband.",
				n -> "Arrest » " + name() + " was arrested with " + n + " contraband by " + name() + ".",
				n -> "MCLPD » No contraband found on " + name() + ".",
				n -> "Fishing Weekend » " + name() + " has found a sunken treasure! 1 left ",
				n -> "» " + name() + " typed the message in 17.805 seconds and won $7,500!",
				n -> "Booster activated! Sugcarronide boosted 1.2x by " + name() + " for 30m",
				n -> "\nCarnage » A Halloween Carnage Booster is active, sponsored by " + name()
						+ "! All Soul of Fright drop chances are boosted 1.5x for 0 minutes. Earn Souls of Fright"
						+ " by fighting spooky mobs in Spawn.\n",
				n -> "MCLabs » $" + money() + " has been added to your account.",
				n -> "Mines » You won $" + money() + "!",
				n -> "Double² » Your investment in cql profited you $" + money() + "!",
				n -> "BondJoules » You beat the competing lab and earned $" + money() + "!",
				n -> "Item Rental » You have paid $10,000 to extend your rent of Item " + n,
				n -> "Giga Drill Breaker - " + (n % 120) + " seconds left",
				n -> "MCLabs » " + n + "x Chorberrium-3-3-3 has been loaded into your Smuggler Satchel.",
				n -> "» You've sold " + n + " chems for $107k (5% company tax)");
		List<String> lines = new ArrayList<>(STRESS_LINES);
		for (int i = 0; i < STRESS_LINES; i++) {
			lines.add(templates.get(i % templates.size()).apply(random.nextInt(1, 100_000)));
		}
		return lines;
	}

	private String name() {
		return "Player" + random.nextInt(1_000_000);
	}

	private String money() {
		return String.format("%,d", random.nextInt(1, 5_000_000));
	}
}
