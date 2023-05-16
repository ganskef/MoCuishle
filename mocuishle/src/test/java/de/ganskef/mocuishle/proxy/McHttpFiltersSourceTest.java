package de.ganskef.mocuishle.proxy;

import static org.junit.Assert.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class McHttpFiltersSourceTest {

	@Test
	public void testEvaluateUrlPattern() {
		Pattern pattern = Pattern.compile("^http://(?:fips.uimserv.net|adclient.uimserv.net)/.*$");
		Matcher m = pattern.matcher("http://fips.uimserv.net/ngvar.js?_=1404578270267");
		assertTrue(m.matches());
	}
}
