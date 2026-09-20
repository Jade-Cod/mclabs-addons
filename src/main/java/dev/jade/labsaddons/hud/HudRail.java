package dev.jade.labsaddons.hud;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * How the editor's Widgets rail lists itself: a row per widget, except that widgets naming
 * the same {@link HudObject#group()} share one collapsible row.
 *
 * <p>Sixteen widgets is a long rail, and a pair that is only ever touched together does not
 * need two lines of it. Only the listing folds — each widget keeps its own position, scale,
 * colours and on/off state.
 *
 * <p>Kept out of {@link HudEditScreen} so the ordering rule can be exercised on its own: the
 * screen cannot be constructed without a running client.
 */
public final class HudRail {
	/**
	 * One row of the rail: either a widget, or the header of the group its widgets are
	 * folded under. A header carries its {@code members}; a widget row carries neither.
	 */
	public record Row(HudObject widget, Text group, List<HudObject> members) {
		public boolean isHeader() {
			return widget == null;
		}
	}

	private HudRail() {
	}

	/**
	 * @param widgets  every registered widget, in the order they were registered
	 * @param expanded whether a given group is currently rolled open
	 */
	public static List<Row> rows(List<HudObject> widgets, Predicate<Text> expanded) {
		Map<String, List<HudObject>> members = new LinkedHashMap<>();
		for (HudObject widget : widgets) {
			Text group = widget.group();
			if (group != null) {
				members.computeIfAbsent(group.getString(), key -> new ArrayList<>()).add(widget);
			}
		}

		List<Row> rows = new ArrayList<>();
		Set<String> placed = new LinkedHashSet<>();
		for (HudObject widget : widgets) {
			Text group = widget.group();
			List<HudObject> inGroup = group == null ? null : members.get(group.getString());
			// A group of one is not worth a header to hide the one behind.
			if (inGroup == null || inGroup.size() < 2) {
				rows.add(new Row(widget, null, List.of()));
				continue;
			}
			// The header stands where the group's first member was registered, so folding a
			// pair together leaves the order of everything else alone.
			if (!placed.add(group.getString())) {
				continue;
			}
			rows.add(new Row(null, group, List.copyOf(inGroup)));
			if (expanded.test(group)) {
				for (HudObject member : inGroup) {
					rows.add(new Row(member, null, List.of()));
				}
			}
		}
		return rows;
	}
}
