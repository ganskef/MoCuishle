package de.ganskef.mocuishle.modify;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import de.ganskef.mocuishle.modify.HtmlModifierTest.ModifyCacheableAdapter;

public class ReplaceAnchorsTest {

	private ModifyCacheableAdapter cached;

	private IModifier replacer;

	private String modify(String input) {
		return replacer.modify(new StringBuilder(input)).toString();
	}

	private void assertEqualsModified(String expected, String input) {
		String modified = modify(input);
		assertEquals(expected, modified);
	}

	@Before
	public void before() {
		cached = new ModifyCacheableAdapter();
		replacer = new ReplaceAnchors(cached, "title");
	}

	@Test
	public void testMissed() {
		String expected = "<a href=\"missed\"><font color=\"#B00000\">xxx</font></a>";
		assertEqualsModified(expected, "<a href=\"missed\">xxx</a>");
		assertEquals("missed", cached.getUrl());
	}

	@Test
	public void testRequested() {
		String expected = "<a href=\"requested\"><font color=\"#B0B000\">xxx</font></a>";
		assertEqualsModified(expected, "<a href=\"requested\">xxx</a>");
		assertEquals("requested", cached.getUrl());
	}

	@Test
	public void testCached() {
		String expected = "<a href=\"cached\">xxx</a>";
		assertEqualsModified(expected, expected);
		assertEquals("cached", cached.getUrl());
	}

	@Test
	public void testSimpleHrefOnceWillBeModified() {
		String expected = "<a href=\"xxx\"><font color=\"#B00000\">xxx</font></a>";
		assertEqualsModified(expected, "<a href=\"xxx\">xxx</a>");
		assertEquals("xxx", cached.getUrl());
	}

	@Test
	public void testSimpleHrefTwiceWillBeModified() {
		String expected = "<a href=\"xxx\"><font color=\"#B00000\">xxx</font></a><a href=\"xxx\"><font color=\"#B00000\">xxx</font></a>";
		assertEqualsModified(expected, "<a href=\"xxx\">xxx</a><a href=\"xxx\">xxx</a>");
		assertEquals("xxx", cached.getUrl());
	}

	@Test
	public void testSimpleHrefEmptyWillBeModified() {
		// XXX consider to let an empty text unmodified
		String expected = "<a href=\"xxx\"><font color=\"#B00000\"></font></a><a href=\"xxx\"><font color=\"#B00000\">xxx</font></a>";
		assertEqualsModified(expected, "<a href=\"xxx\"></a><a href=\"xxx\">xxx</a>");
		assertEquals("xxx", cached.getUrl());
	}

	@Test
	public void testMissingQuoteBeforeHrefWillBeModified() {
		// auch das gab's im Oracle Code

		// wenigstens unverändert lassen (alt)
		// String expected = "<a href=xxx\"></a><a href=\"xxx\">xxx</a>";
		// assertEquals(expected, replacerModify(expected));

		String expected = "<a href=xxx\"><font color=\"#B00000\">xxx</font></a><a href=\"xxx\"><font color=\"#B00000\">xxx</font></a>";
		assertEqualsModified(expected, "<a href=xxx\">xxx</a><a href=\"xxx\">xxx</a>");
		assertEquals("xxx", cached.getUrl());
	}

	@Test
	public void testMissingQuoteAfterHref() {
		// ??? wenigstens unverändert lassen
		String expected = "<a href=\"xxx>xxx</a>";
		assertEqualsModified(expected, expected);
		assertEquals(null, cached.getUrl());
	}

	@Test
	public void testMissingQuotesInHrefWillBeModified() {
		// // ??? wenigstens unverändert lassen (alt)
		// String expected = "<a href=xxx></a><a href=\"xxx\">xxx</a>";
		// assertEquals(expected, replacerModify(expected));

		String expected = "<a href=xxx><font color=\"#B00000\">xxx</font></a><a href=\"xxx\"><font color=\"#B00000\">xxx</font></a>";
		assertEqualsModified(expected, "<a href=xxx>xxx</a><a href=\"xxx\">xxx</a>");
		assertEquals("xxx", cached.getUrl());
	}

	@Test(timeout = 1000)
	public void testBugEndlessLoopJosef() throws Exception {
		// Fehlermeldung von Josef Lehnert
		// Der URL:
		// http://www.oracle.com/technetwork/java/javase/downloads/sb2download-2177776.html
		// von der Seite:
		// http://www.oracle.com/us/dm/315911-index-oem-2214646.html
		// enthält:
		// http://consent-pref.truste.com/defaultpreferencemanager/8E0D8C975853BCEAD2A5AA744A323E4C.cache.html
		// und dessen Inhalt verursacht eine Endlosschleife:
		//
		InputStream is = ClassLoader.getSystemResourceAsStream("BugEndlessLoopJosef.txt");
		String html = IOUtils.toString(is);
		modify(html);

		// String line =
		// "function _nb(a){a[0]==dOb?(Vnb=dOb):(Vnb=\"<a
		// href='https://twitter.com/TRUSTe'
		// target='_blank'>\"+a[0]+\"<\\/a><br/><hr/><a
		// href='https://twitter.com/TRUSTe'
		// target='_blank'>\"+a[1]+'<\\/a>');ij(Xnb,new w6(Vnb))}";
		// replacerModify(line);
		//
		// // Ursache war ein Backslash vor dem Slash: <\/a>
		// String expected =
		// "<a href=\"xxx\"><font color=\"#B00000\">xxx</font><\\/a>";
		// assertEquals(expected, replacerModify("<a href=\"xxx\">xxx<\\/a>"));
		//
		// // und damit viele öffnende <a ohne Ende -> Rekursion
		//
		// Kommentare im HTML und damit Skripte werden jetzt ignoriert
	}

	@Test
	public void testTagInAnchorTextWillBeModified() {
		String expected = "<a href=\"xxx\"><font color=\"#B00000\"><b>xxx</b></font></a>";
		assertEqualsModified(expected, "<a href=\"xxx\"><b>xxx</b></a>");
		assertEquals("xxx", cached.getUrl());
	}

	@Test
	public void testAnchorWithoutHrefStaysUnmodified() {
		// // wenigstens unverändert lassen (alt)
		// String expected =
		// "<a id=\"xxx\"></a>xxx<a href=\"xxx\"><font
		// color=\"#B00000\">xxx</font></a>";
		// assertEquals(expected, replacerModify(expected));

		String expected = "<a id=\"xxx\"></a>xxx<a href=\"xxx\"><font color=\"#B00000\">xxx</font></a>";
		assertEqualsModified(expected, "<a id=\"xxx\"></a>xxx<a href=\"xxx\">xxx</a>");
		assertEquals("xxx", cached.getUrl());
	}

	@Test
	public void testAnchorWithClassBeforeHrefWillBeModified() {
		String expected = "<a class=\"yyy\" href=\"xxx\"><font color=\"#B00000\">xxx</font></a>";
		assertEqualsModified(expected, "<a class=\"yyy\" href=\"xxx\">xxx</a>");
		assertEquals("xxx", cached.getUrl());
	}

	@Test
	public void testAnchorWithClassAfterHrefWillBeModified() {
		String expected = "<a href=\"xxx\" class=\"yyy\" target=\"_top\"><font color=\"#B00000\">xxx</font></a>";
		assertEqualsModified(expected, "<a href=\"xxx\" class=\"yyy\" target=\"_top\">xxx</a>");
		assertEquals("xxx", cached.getUrl());
	}

	@Test
	public void testAnchorWithBlankInHrefWillBeModified() {
		// found at martinfowler.com
		String expected = "<a href=\"x x x\"><font color=\"#B00000\">xxx</font></a>";
		assertEqualsModified(expected, "<a href=\"x x x\">xxx</a>");
		assertEquals("x x x", cached.getUrl());
	}

	@Test
	public void testAnchorOnSamePageReferencesThePage() {
		String expected = "<a href=\"xxx#yyy\"><font color=\"#B00000\">xxx</font></a>";
		assertEqualsModified(expected, "<a href=\"xxx#yyy\">xxx</a>");
		assertEquals("xxx", cached.getUrl());
	}

	@Test
	public void testAnchorWithAcronymTagInTextWillBeModified() {
		// FreeBSD Handbook
		// http://www.freebsd.org/doc/en_US.ISO8859-1/books/handbook/bsdinstall-install-trouble.html
		//
		String expected = "<a href=\"xxx\"><font color=\"#B00000\">x<acronym class=\"acronym\">x</acronym>x</font></a>";
		assertEqualsModified(expected, "<a href=\"xxx\">x<acronym class=\"acronym\">x</acronym>x</a>");
		assertEquals("xxx", cached.getUrl());
	}

	@Test
	public void testAnchorWithQuotedEntitiesReferencesUnquoted() {
		// FreeBSD Handbook, linked Man Pages
		// http://www.freebsd.org/cgi/man.cgi?query=bsdinstall&sektion=8
		//
		String expected = "<a href=\"xxx?a=a&amp;b=&quot;&lt;b&gt;&quot;\"><font color=\"#B00000\">xxx</font></a>";
		assertEqualsModified(expected, "<a href=\"xxx?a=a&amp;b=&quot;&lt;b&gt;&quot;\">xxx</a>");
		assertEquals("xxx?a=a&b=\"<b>\"", cached.getUrl());
	}

	@Test(timeout = 10000)
	public void testEndlessLoopTitleAtWebDe() throws Exception {
		InputStream is = ClassLoader.getSystemResourceAsStream("EndlessLoopTitleAtWebDe.txt");
		String html = IOUtils.toString(is);
		modify(html);
	}
}
