package dev.jade.labsaddons.hud;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A widget's rows are asked for three or four times a frame — measure the width, measure the
 * height, draw, and for the progress board a {@code shouldRender} before any of that — and
 * never change in between. Each ask used to rebuild the list from scratch.
 *
 * <p>Counts builds rather than milliseconds, because the build count is the thing that changed
 * and it does not vary with the machine.
 */
class FrameValueTest {
	/** What {@link HudObject} asks for in one frame: width, height, draw, plus shouldRender. */
	private static final int ASKS_PER_FRAME = 4;

	@Test
	void everyAskInOneFrameSharesOneBuild() {
		FrameValue<List<String>> rows = new FrameValue<>();
		AtomicInteger built = new AtomicInteger();
		for (int ask = 0; ask < ASKS_PER_FRAME; ask++) {
			rows.get(1_000L, false, () -> {
				built.incrementAndGet();
				return List.of("a", "b");
			});
		}
		assertEquals(1, built.get(), "four asks in one frame is one build");
		assertEquals(1, rows.builds());
	}

	@Test
	void theSameListInstanceComesBackSoNothingIsReallocated() {
		FrameValue<List<String>> rows = new FrameValue<>();
		List<String> first = rows.get(1_000L, false, () -> List.of("a"));
		List<String> again = rows.get(1_000L, false, () -> List.of("a"));
		assertSame(first, again);
	}

	@Test
	void theNextFrameBuildsAgain() {
		FrameValue<List<String>> rows = new FrameValue<>();
		AtomicInteger built = new AtomicInteger();
		for (long frame = 0; frame < 60; frame++) {
			for (int ask = 0; ask < ASKS_PER_FRAME; ask++) {
				rows.get(frame * 16L, false, () -> {
					built.incrementAndGet();
					return List.of("row");
				});
			}
		}
		// 240 asks a second became 60 builds, which is once per frame and no more.
		assertEquals(60, built.get());
	}

	@Test
	void aStaleValueIsNeverServedAcrossAFrame() {
		// The progress rows carry a fade alpha that moves every frame, so a value held past
		// its own millisecond would freeze the animation.
		FrameValue<String> value = new FrameValue<>();
		assertEquals("t=0", value.get(0L, false, () -> "t=0"));
		assertEquals("t=16", value.get(16L, false, () -> "t=16"));
		assertEquals("t=32", value.get(32L, false, () -> "t=32"));
		assertEquals(3, value.builds());
	}

	@Test
	void theEditorPreviewIsNotConfusedWithTheLiveWidget() {
		// Both can be asked for inside one millisecond, and they hold different contents: a
		// widget that measured the preview and drew the real thing would lay out wrong.
		FrameValue<String> value = new FrameValue<>();
		assertEquals("live", value.get(1_000L, false, () -> "live"));
		assertEquals("preview", value.get(1_000L, true, () -> "preview"));
		assertEquals("live", value.get(1_000L, false, () -> "live"));
		assertEquals(3, value.builds());
	}

	@Test
	void aBuilderReturningNothingIsNotCachedAsAnAnswer() {
		// Guards the null check: caching null would make an empty answer permanent.
		FrameValue<String> value = new FrameValue<>();
		assertEquals(null, value.get(1_000L, false, () -> null));
		assertEquals("now there is one", value.get(1_000L, false, () -> "now there is one"));
		assertEquals(2, value.builds());
	}
}
