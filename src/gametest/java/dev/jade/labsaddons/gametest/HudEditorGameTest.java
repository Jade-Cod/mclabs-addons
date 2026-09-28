package dev.jade.labsaddons.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.HelpScreen;
import dev.jade.labsaddons.hud.HudEditScreen;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjects;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The HUD editor, driven the way a player drives it: the {@code ;} key, the mouse and the
 * arrow keys. Every step checks where the widget actually ended up, not just that nothing
 * threw.
 */
public class HudEditorGameTest implements FabricClientGameTest {
	private static final int LEFT = 0;
	private static final int DRAG_X = 40;
	private static final int DRAG_Y = 25;
	/** Inside the editor's 6px snap reach. */
	private static final int SNAP_NUDGE = 3;
	private static final int NO_MODIFIERS = 0;
	/** Pixels a drag is chopped into, so the screen sees a drag rather than a teleport. */
	private static final int DRAG_STEPS = 8;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorld.open(context)) {
			TestInput input = context.getInput();

			// First open: the welcome guide comes up over the editor, once.
			input.pressKey(InputConstants.KEY_SEMICOLON);
			context.waitForScreen(HelpScreen.class);
			context.takeScreenshot("editor-01-welcome");
			input.pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(HudEditScreen.class);
			check(context.computeOnClient(c -> LabsAddonsConfig.get().hasSeenWelcome),
					"closing the welcome guide marks it seen");

			// The topmost widget, parked mid-screen, clear of the rail and the toolbar.
			HudObject widget = context.computeOnClient(c -> {
				List<HudObject> all = HudObjects.all();
				HudObject top = all.get(all.size() - 1);
				top.settings().enabled = true;
				int w = c.gui.screen().width;
				int h = c.gui.screen().height;
				int[] b = top.screenBounds(w, h, true);
				top.setScreenBoxPosition(w / 2 - b[2] / 2, h / 2 - b[3] / 2, w, h);
				return top;
			});
			context.waitTicks(2);
			context.takeScreenshot("editor-02-parked");

			// Snapping: a widget centred on the screen, dragged a few pixels, springs back to
			// the centre line. Holding Alt turns that off.
			int[] parked = bounds(context, widget);
			drag(context, parked, SNAP_NUDGE, 0, NO_MODIFIERS);
			int[] snapped = bounds(context, widget);
			checkNear(snapped[0], parked[0], "a small drag snaps back to the centre line");
			drag(context, snapped, SNAP_NUDGE, 0, InputConstants.MOD_ALT);
			int[] unsnapped = bounds(context, widget);
			checkNear(unsnapped[0], snapped[0] + SNAP_NUDGE, "with Alt the same drag doesn't snap");

			// A long Alt-drag lands exactly where the mouse took it.
			int[] before = bounds(context, widget);
			drag(context, before, DRAG_X, DRAG_Y, InputConstants.MOD_ALT);
			int[] dragged = bounds(context, widget);
			checkNear(dragged[0], before[0] + DRAG_X, "alt-drag moves x by exactly the drag");
			checkNear(dragged[1], before[1] + DRAG_Y, "alt-drag moves y by exactly the drag");
			context.takeScreenshot("editor-03-dragged");

			// Arrow keys nudge the selection 1px, Shift makes it 10.
			TestWorld.key(context, InputConstants.KEY_RIGHT, NO_MODIFIERS);
			TestWorld.key(context, InputConstants.KEY_DOWN, InputConstants.MOD_SHIFT);
			context.waitTick();
			int[] nudged = bounds(context, widget);
			checkNear(nudged[0], dragged[0] + 1, "right arrow nudges 1px");
			checkNear(nudged[1], dragged[1] + 10, "shift+down nudges 10px");

			// Resize from the bottom-right handle: the top-left stays put, the scale snaps
			// to the nearest quarter.
			float scaleBefore = context.computeOnClient(c -> widget.settings().scale);
			double targetScale = scaleBefore + 0.5;
			drag(context, nudged[0] + nudged[2], nudged[1] + nudged[3],
					(int) Math.round(nudged[2] * (targetScale / scaleBefore - 1)),
					(int) Math.round(nudged[3] * (targetScale / scaleBefore - 1)), NO_MODIFIERS);
			float scaleAfter = context.computeOnClient(c -> widget.settings().scale);
			int[] resized = bounds(context, widget);
			check(Math.abs(scaleAfter - targetScale) < 0.01,
					"resize snaps to " + targetScale + " (got " + scaleAfter + ")");
			checkNear(resized[0], nudged[0], "resize keeps the left edge");
			checkNear(resized[1], nudged[1], "resize keeps the top edge");
			context.takeScreenshot("editor-04-resized");

			// Leaving the editor writes the layout to disk there and then.
			Path config = FabricLoader.getInstance().getConfigDir().resolve("labsaddons");
			long writtenBefore = lastModified(config);
			input.pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(c -> c.gui.screen() == null);
			check(lastModified(config) > writtenBefore, "closing the editor saves the layout");

			// And the live HUD draws the widget where the editor left it.
			context.waitTicks(2);
			context.takeScreenshot("editor-05-live");

			// The deposit key, in the world rather than a screen, sends /ch qd.
			List<String> sent = new CopyOnWriteArrayList<>();
			ClientSendMessageEvents.COMMAND.register(sent::add);
			input.pressKey(InputConstants.KEY_B);
			context.waitTicks(2);
			check(sent.contains("ch qd"), "the deposit key sends /ch qd (sent " + sent + ")");
		}
	}

	/** Drags a widget by its middle. */
	private static void drag(ClientGameTestContext context, int[] bounds, int dx, int dy, int modifiers) {
		drag(context, bounds[0] + bounds[2] / 2.0, bounds[1] + bounds[3] / 2.0, dx, dy, modifiers);
	}

	/** Press, move in steps, release — the way a hand does it. */
	private static void drag(ClientGameTestContext context, double fromX, double fromY, int dx, int dy,
			int modifiers) {
		TestWorld.cursorAt(context, fromX, fromY);
		context.waitTick();
		TestWorld.mouse(context, LEFT, InputConstants.PRESS, modifiers);
		context.waitTick();
		for (int step = 1; step <= DRAG_STEPS; step++) {
			TestWorld.cursorAt(context, fromX + (double) dx * step / DRAG_STEPS,
					fromY + (double) dy * step / DRAG_STEPS);
			context.waitTick();
		}
		TestWorld.mouse(context, LEFT, InputConstants.RELEASE, modifiers);
		context.waitTick();
	}

	private static int[] bounds(ClientGameTestContext context, HudObject widget) {
		return context.computeOnClient(c ->
				widget.screenBounds(c.gui.screen() != null ? c.gui.screen().width : c.getWindow().getGuiScaledWidth(),
						c.gui.screen() != null ? c.gui.screen().height : c.getWindow().getGuiScaledHeight(), true));
	}

	/** Newest write anywhere under the mod's config folder, which holds a file per profile. */
	private static long lastModified(Path folder) {
		if (!Files.isDirectory(folder)) {
			return 0;
		}
		try (var files = Files.walk(folder)) {
			return files.filter(Files::isRegularFile).mapToLong(p -> p.toFile().lastModified()).max().orElse(0);
		} catch (java.io.IOException e) {
			throw new AssertionError("can't read " + folder, e);
		}
	}

	/** Within a pixel: positions round-trip through fractions of the screen. */
	private static void checkNear(int actual, int expected, String what) {
		check(Math.abs(actual - expected) <= 1, what + " (expected " + expected + ", got " + actual + ")");
	}

	static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError(what);
		}
	}
}
