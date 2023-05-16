package de.ganskef.mocuishle.cache;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

import de.ganskef.mocuishle.IPlatform;
import de.ganskef.mocuishle.McTestProxy;

public class McCacheTest {

	@Test
	public void testIsValidated() throws Exception {
		IPlatform platform = new McTestProxy();
		McCache cache = new McCache(platform);
		File dir = new File(cache.getSpoolDir("."), "marker/http/127.0.0.1");
		File file = new File(dir, "Uo2f3jDBBqgIyrwe0jd0xMg");
		String url = "http://127.0.0.1/testIsValidated";
		file.delete();
		assertFalse(cache.isValidated(url));
		dir.mkdirs();
		file.createNewFile();
		assertTrue(cache.isValidated(url));
	}
}
