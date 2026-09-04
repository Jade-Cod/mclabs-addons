package dev.jade.labsaddons.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ModrinthLinkTest {
	@Test
	public void linksTheVersionPageById() {
		// Modrinth version ids are short alphanumeric strings, not version numbers:
		// the same number is published once per Minecraft line, so only the id
		// identifies one specific jar.
		assertEquals("https://modrinth.com/mod/mclabs-addons/version/sjcFGe03",
				ModrinthLink.downloadUri("sjcFGe03").toString());
	}

	@Test
	public void distinctIdsGiveDistinctLinks() {
		// The whole point of linking by id: two entries sharing version number
		// "1.15.1" (one per Minecraft line) still get their own URLs.
		assertEquals("https://modrinth.com/mod/mclabs-addons/version/aaaa1111",
				ModrinthLink.downloadUri("aaaa1111").toString());
		assertEquals("https://modrinth.com/mod/mclabs-addons/version/bbbb2222",
				ModrinthLink.downloadUri("bbbb2222").toString());
	}

	@Test
	public void fallsBackToProjectPageOnUnsafeId() {
		// A space would blow up URI.create; a slash would escape the version path.
		assertEquals(ModrinthLink.PROJECT_URL, ModrinthLink.downloadUri("1.0 evil").toString());
		assertEquals(ModrinthLink.PROJECT_URL, ModrinthLink.downloadUri("../../evil").toString());
		assertEquals(ModrinthLink.PROJECT_URL, ModrinthLink.downloadUri("").toString());
	}

	@Test
	public void fallsBackToProjectPageOnMissingId() {
		assertEquals(ModrinthLink.PROJECT_URL, ModrinthLink.downloadUri(null).toString());
	}

	@Test
	public void alwaysStaysOnModrinth() {
		for (String id : new String[] {"sjcFGe03", "1.0 evil", "../../evil", "", "@evil.com"}) {
			assertEquals("modrinth.com", ModrinthLink.downloadUri(id).getHost(),
					"id '" + id + "' must not point off modrinth.com");
		}
	}
}
