package dev.jade.labsaddons.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every loaded setting must survive {@code sanitized()}.
 *
 * <p>That method builds a fresh config and copies fields across one at a time, so a field
 * nobody adds a line for is silently reset to its default on every launch — the config is
 * written correctly, read correctly, and then thrown away. It had happened to twelve
 * fields before anyone noticed, including the Mines, blackjack and Double² board toggles,
 * which meant turning a board off never stuck.
 *
 * <p>Nothing about that fails loudly, which is exactly the kind of bug this codebase has
 * been bitten by before. So rather than trusting the next person to remember, this walks
 * the fields: set each one away from its default, sanitize, and check it came back.
 */
class SanitizedKeepsEveryFieldTest {
	/**
	 * Fields this walk cannot check, with the reason. Everything else must survive.
	 */
	private static final Set<String> SKIPPED = Set.of(
			// Pre-rename fields, kept only so an old config still deserializes. They are
			// read into the migration and must not be written back out.
			"chumTimerEnabled", "chumHudX", "chumHudY", "chumHudScale", "chumTextColor",
			"chumBackgroundEnabled", "chumBackgroundColor",
			// Carried across, but validated against a fixed set of names on the way. A
			// nudged value is not one of them, so it correctly falls back to the default
			// and looks dropped to this test.
			"runnerAlarmSound", "itemUsesCorner");

	@Test
	void everyFieldSurvivesSanitizing() throws Exception {
		LabsAddonsConfig loaded = new LabsAddonsConfig();
		List<Field> checked = new ArrayList<>();
		for (Field field : LabsAddonsConfig.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()) || !Modifier.isPublic(field.getModifiers())
					|| SKIPPED.contains(field.getName())
					|| !isSimple(field.getType())) {
				continue;
			}
			field.setAccessible(true);
			field.set(loaded, moved(field.get(loaded), field.getType()));
			checked.add(field);
		}
		assertTrue(checked.size() > 20, "the walk should cover most of the config");

		Method sanitized = LabsAddonsConfig.class.getDeclaredMethod("sanitized");
		sanitized.setAccessible(true);
		Object clean = sanitized.invoke(loaded);

		LabsAddonsConfig defaults = new LabsAddonsConfig();
		List<String> lost = new ArrayList<>();
		for (Field field : checked) {
			if (field.get(clean).equals(field.get(defaults))) {
				lost.add(field.getName());
			}
		}
		assertTrue(lost.isEmpty(),
				"sanitized() drops these back to their defaults, so they reset on every "
						+ "launch: " + lost);
	}

	/** Types a value can be nudged for without tripping a clamp. */
	private static boolean isSimple(Class<?> type) {
		return type == boolean.class || type == int.class || type == long.class
				|| type == float.class || type == String.class;
	}

	/** The same value, moved somewhere else that is still valid. */
	private static Object moved(Object current, Class<?> type) {
		if (type == boolean.class) {
			return !((Boolean) current);
		}
		if (type == int.class) {
			return ((Integer) current) + 1;
		}
		if (type == long.class) {
			return ((Long) current) + 1L;
		}
		if (type == float.class) {
			// A small step: several of these clamp to a narrow band.
			return ((Float) current) + 0.01f;
		}
		return current == null || ((String) current).isEmpty() ? "x" : current + "x";
	}
}
