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
	void ungroupedWidgetsKeepTheirRegistrationOrder() {
		List<HudObject> widgets = List.of(new Fake("a", null), new Fake("b", null));
		assertEquals(List.of("a", "b"), labels(rows(widgets)));
	}

	/** The whole point: a pair costs the rail one row rather than two. */
	@Test
	void aGroupCollapsesToASingleRow() {
		List<HudObject> widgets = List.of(new Fake("a", null),
				new Fake("coinflips", GAMBLING), new Fake("coinflip_record", GAMBLING));
		assertEquals(List.of("a", "[Gambling]"), labels(rows(widgets)));
	}

	@Test
	void anOpenGroupListsItsWidgetsUnderTheHeader() {
		List<HudObject> widgets = List.of(new Fake("a", null),
				new Fake("coinflips", GAMBLING), new Fake("coinflip_record", GAMBLING));
		assertEquals(List.of("a", "[Gambling]", "coinflips", "coinflip_record"),
				labels(rows(widgets, GAMBLING)));
	}

	/**
	 * The header stands where the group's first member was registered. Shunting groups to
	 * the end would reshuffle a list people have learned the shape of.
	 */
	@Test
	void theHeaderStandsWhereTheGroupsFirstWidgetDid() {
		List<HudObject> widgets = List.of(new Fake("a", null),
				new Fake("coinflips", GAMBLING), new Fake("b", null),
				new Fake("coinflip_record", GAMBLING), new Fake("c", null));
		assertEquals(List.of("a", "[Gambling]", "b", "c"), labels(rows(widgets)));
		// Open, the members gather under the header rather than staying where they were.
		assertEquals(List.of("a", "[Gambling]", "coinflips", "coinflip_record", "b", "c"),
				labels(rows(widgets, GAMBLING)));
	}

	/** A header hiding one widget saves nothing and costs a click. */
	@Test
	void aGroupOfOneGetsAPlainRow() {
		List<HudObject> widgets = List.of(new Fake("coinflips", GAMBLING), new Fake("a", null));
		List<HudRail.Row> rows = rows(widgets);
		assertEquals(List.of("coinflips", "a"), labels(rows));
		assertFalse(rows.get(0).isHeader());
	}

	@Test
	void groupsAreFoldedIndependently() {
		List<HudObject> widgets = List.of(
				new Fake("coinflips", GAMBLING), new Fake("coinflip_record", GAMBLING),
				new Fake("x", OTHER), new Fake("y", OTHER));
		assertEquals(List.of("[Gambling]", "coinflips", "coinflip_record", "[Fishing]"),
				labels(rows(widgets, GAMBLING)));
	}

	/** A header carries the widgets it stands for, which is what its tick switches. */
	@Test
	void aHeaderCarriesEveryWidgetItStandsFor() {
		HudObject open = new Fake("coinflips", GAMBLING);
		HudObject record = new Fake("coinflip_record", GAMBLING);
		HudRail.Row header = rows(List.of(open, record)).get(0);
		assertTrue(header.isHeader());
		assertEquals(2, header.members().size());
		assertSame(open, header.members().get(0));
		assertSame(record, header.members().get(1));
	}
}
