package dev.jade.labsaddons.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ModrinthLinkTest {
	@Test
	public void linksTheVersionPageForANormalRelease() {
		assertEquals("https://modrinth.com/mod/mclabs-addons/version/1.15.0",
				ModrinthLink.downloadUri("1.15.0").toString());
	}

	@Test
	public void keepsPreReleaseAndBuildSuffixes() {
		assertEquals("https://modrinth.com/mod/mclabs-addons/version/1.15.0-beta.1",
				ModrinthLink.downloadUri("1.15.0-beta.1").toString());
	}

	@Test
	public void fallsBackToProjectPageOnUnsafeVersion() {
		// A space would blow up URI.create; a slash would escape the version path.
		assertEquals(ModrinthLink.PROJECT_URL, ModrinthLink.downloadUri("1.0 evil").toString());
		assertEquals(ModrinthLink.PROJECT_URL, ModrinthLink.downloadUri("../../evil").toString());
		assertEquals(ModrinthLink.PROJECT_URL, ModrinthLink.downloadUri("").toString());
	}

	@Test
	public void fallsBackToProjectPageOnMissingVersion() {
		assertEquals(ModrinthLink.PROJECT_URL, ModrinthLink.downloadUri(null).toString());
	}

	@Test
	public void alwaysStaysOnModrinth() {
		for (String v : new String[] {"1.15.0", "1.0 evil", "../../evil", "", "@evil.com"}) {
			assertEquals("modrinth.com", ModrinthLink.downloadUri(v).getHost(),
					"version '" + v + "' must not point off modrinth.com");
		}
	}
}
