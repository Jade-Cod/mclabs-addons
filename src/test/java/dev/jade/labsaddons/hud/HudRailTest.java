package dev.jade.labsaddons.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rail's folding rule. Worth pinning because the editor screen cannot be built without
 * a running client, so nothing else here can see it.
 */
class HudRailTest {
	// Literals, not translation keys: no language file is loaded in a plain test JVM, so a
	// translatable would come back as its own key and say nothing about the folding.
	private static final Text GAMBLING = Text.literal("Gambling");
	private static final Text OTHER = Text.literal("Fishing");

	/** Enough of a widget to be listed; none of the drawing is reached. */
	private static final class Fake extends HudObject {
		private final String id;
		private final Text group;

		Fake(String id, Text group) {
			this.id = id;
			this.group = group;
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public Text group() {
			return group;
		}

		@Override
		public Text displayName() {
			return Text.literal(id);
		}

		@Override
		public HudObjectSettings defaultSettings() {
			return new HudObjectSettings();
		}

		@Override
		public int contentWidth(boolean preview) {
			return 0;
		}

		@Override
		public int contentHeight(boolean preview) {
			return 0;
		}

		@Override
		protected void renderContent(DrawContext context, boolean preview) {
		}

		@Override
		public boolean shouldRender() {
			return false;
		}
	}

	private static List<String> labels(List<HudRail.Row> rows) {
		return rows.stream()
				.map(row -> row.isHeader() ? "[" + row.group().getString() + "]" : row.widget().id())
				.toList();
	}

	private static List<HudRail.Row> rows(List<HudObject> widgets, Text... expanded) {
		Set<String> open = Set.of(java.util.Arrays.stream(expanded)
				.map(Text::getString).toArray(String[]::new));
		return HudRail.rows(widgets, group -> open.contains(group.getString()));
	}

	@Test
	void ungroupedWidgetsAreListedAlphabetically() {
		List<HudObject> widgets = List.of(new Fake("Runner Jobs", null),
				new Fake("Ability Cooldowns", null), new Fake("Chemtainer", null));
		assertEquals(List.of("Ability Cooldowns", "Chemtainer", "Runner Jobs"),
				labels(rows(widgets)));
	}

	/** The whole point: a pair costs the rail one row rather than two. */
	@Test
	void aGroupCollapsesToASingleRow() {
		List<HudObject> widgets = List.of(new Fake("Chemtainer", null),
				new Fake("Open Coinflips", GAMBLING), new Fake("Coinflip Record", GAMBLING));
		assertEquals(List.of("Chemtainer", "[Gambling]"), labels(rows(widgets)));
	}

	@Test
	void anOpenGroupListsItsWidgetsUnderTheHeaderAlphabetically() {
		List<HudObject> widgets = List.of(new Fake("Chemtainer", null),
				new Fake("Open Coinflips", GAMBLING), new Fake("Coinflip Record", GAMBLING));
		assertEquals(List.of("Chemtainer", "[Gambling]", "Coinflip Record", "Open Coinflips"),
				labels(rows(widgets, GAMBLING)));
	}

	/**
	 * Groups sink below every loose widget, whatever order they were registered in, so the
	 * rows you can act on directly are all together at the top.
	 */
	@Test
	void groupsSitBelowEveryUngroupedWidget() {
		List<HudObject> widgets = List.of(
				new Fake("Open Coinflips", GAMBLING),
				new Fake("Runner Jobs", null),
				new Fake("Coinflip Record", GAMBLING),
				new Fake("Ability Cooldowns", null));
		assertEquals(List.of("Ability Cooldowns", "Runner Jobs", "[Gambling]"),
				labels(rows(widgets)));
	}

	@Test
	void groupHeadersAreThemselvesAlphabetical() {
		// Gambling's widgets are registered first, and Fishing still heads the list.
		List<HudObject> widgets = List.of(
				new Fake("Open Coinflips", GAMBLING), new Fake("Coinflip Record", GAMBLING),
				new Fake("Bait", OTHER), new Fake("Catch", OTHER));
		assertEquals(List.of("[Fishing]", "[Gambling]"), labels(rows(widgets)));
	}

	/** Opening one group leaves the other shut, and neither moves. */
	@Test
	void groupsAreFoldedIndependently() {
		List<HudObject> widgets = List.of(
				new Fake("Open Coinflips", GAMBLING), new Fake("Coinflip Record", GAMBLING),
				new Fake("Bait", OTHER), new Fake("Catch", OTHER));
		assertEquals(List.of("[Fishing]", "[Gambling]", "Coinflip Record", "Open Coinflips"),
				labels(rows(widgets, GAMBLING)));
		assertEquals(List.of("[Fishing]", "Bait", "Catch", "[Gambling]"),
				labels(rows(widgets, OTHER)));
	}

	/** A header hiding one widget saves nothing and costs a click. */
	@Test
	void aGroupOfOneIsListedPlainlyAndNotIndented() {
		List<HudObject> widgets = List.of(new Fake("Open Coinflips", GAMBLING),
				new Fake("Chemtainer", null));
		List<HudRail.Row> rows = rows(widgets);
		assertEquals(List.of("Chemtainer", "Open Coinflips"), labels(rows));
		assertFalse(rows.get(1).isHeader());
		// Naming a group is not the same as sitting under a header, which is what the
		// editor steps the label in for.
		assertFalse(rows.get(1).indented());
	}

	@Test
	void onlyAGroupsMembersAreIndented() {
		List<HudObject> widgets = List.of(new Fake("Chemtainer", null),
				new Fake("Open Coinflips", GAMBLING), new Fake("Coinflip Record", GAMBLING));
		List<HudRail.Row> rows = rows(widgets, GAMBLING);
		assertFalse(rows.get(0).indented(), "a loose widget");
		assertFalse(rows.get(1).indented(), "the header");
		assertTrue(rows.get(2).indented());
		assertTrue(rows.get(3).indented());
	}

	/** A header carries the widgets it stands for, which is what its tick switches. */
	@Test
	void aHeaderCarriesEveryWidgetItStandsFor() {
		HudObject open = new Fake("Open Coinflips", GAMBLING);
		HudObject record = new Fake("Coinflip Record", GAMBLING);
		HudRail.Row header = rows(List.of(open, record)).get(0);
		assertTrue(header.isHeader());
		assertEquals(2, header.members().size());
		// Alphabetical here too, not registration order.
		assertSame(record, header.members().get(0));
		assertSame(open, header.members().get(1));
	}
}
