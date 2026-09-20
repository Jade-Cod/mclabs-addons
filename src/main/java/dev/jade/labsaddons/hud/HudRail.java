package dev.jade.labsaddons.hud;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * How the editor's Widgets rail lists itself: the widgets that belong to no group first, in
 * alphabetical order, and then the groups, also alphabetical, each one collapsible.
 *
 * <p>Seventeen widgets made a rail tall enough to cover the part of the screen you were
 * trying to place things on. Widgets naming the same {@link HudObject#group()} share a row,
 * which is what shortens it. Only the listing folds — each widget keeps its own position,
 * scale, colours and on/off state.
 *
 * <p>Ordering is by the name on the row rather than by widget id or registration order,
 * because the name is the thing being scanned for. Groups sink below the loose widgets so
 * that the rows you can act on directly are all together at the top, rather than being
 * separated by headers that only open something.
 *
 * <p>Kept out of {@link HudEditScreen} so the rule can be exercised on its own: the screen
 * cannot be constructed without a running client.
 */
public final class HudRail {
	/** By the label on the row, which is what someone reading the rail is scanning. */
	private static final Comparator<HudObject> BY_NAME = Comparator.comparing(
			widget -> widget.displayName().getString(), String.CASE_INSENSITIVE_ORDER);
	private static final Comparator<Text> BY_LABEL = Comparator.comparing(
			Text::getString, String.CASE_INSENSITIVE_ORDER);

	/**
	 * One row of the rail: either a widget, or the header of the group its widgets are
	 * folded under. A header carries its {@code members}; a widget row carries neither.
	 *
	 * @param indented whether this row sits under a group header, and is drawn stepped in
	 */
	public record Row(HudObject widget, Text group, List<HudObject> members, boolean indented) {
		static Row loose(HudObject widget) {
			return new Row(widget, null, List.of(), false);
		}

		static Row member(HudObject widget) {
			return new Row(widget, null, List.of(), true);
		}

		static Row header(Text group, List<HudObject> members) {
			return new Row(null, group, List.copyOf(members), false);
		}

		public boolean isHeader() {
			return widget == null;
		}
	}

	private HudRail() {
	}

	/**
	 * @param widgets  every registered widget, in any order
	 * @param expanded whether a given group is currently rolled open
	 */
	public static List<Row> rows(List<HudObject> widgets, Predicate<Text> expanded) {
		Map<String, List<HudObject>> byGroup = new LinkedHashMap<>();
		Map<String, Text> labels = new LinkedHashMap<>();
		List<HudObject> loose = new ArrayList<>();
		for (HudObject widget : widgets) {
			Text group = widget.group();
			if (group == null) {
				loose.add(widget);
				continue;
			}
			labels.putIfAbsent(group.getString(), group);
			byGroup.computeIfAbsent(group.getString(), key -> new ArrayList<>()).add(widget);
		}

		// A group of one hides nothing and costs a click, so its widget is listed plainly.
		List<String> folded = new ArrayList<>();
		for (Map.Entry<String, List<HudObject>> entry : byGroup.entrySet()) {
			if (entry.getValue().size() < 2) {
				loose.addAll(entry.getValue());
			} else {
				folded.add(entry.getKey());
			}
		}

		List<Row> rows = new ArrayList<>();
		loose.sort(BY_NAME);
		for (HudObject widget : loose) {
			rows.add(Row.loose(widget));
		}

		for (Text group : folded.stream().map(labels::get).sorted(BY_LABEL).toList()) {
			List<HudObject> members = new ArrayList<>(byGroup.get(group.getString()));
			members.sort(BY_NAME);
			rows.add(Row.header(group, members));
			if (expanded.test(group)) {
				for (HudObject member : members) {
					rows.add(Row.member(member));
				}
			}
		}
		return rows;
	}
}
