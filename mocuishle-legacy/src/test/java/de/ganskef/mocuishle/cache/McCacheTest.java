package de.ganskef.mocuishle.cache;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.ganskef.mocuishle.IPlatform;

public class McCacheTest {

	@Mock
	private IPlatform platform;

	@Before
	public void initMocks() {
		MockitoAnnotations.initMocks(this);
		when(platform.getHttpSpoolDir())
				.thenReturn(new File(String.format("target/%s/http", getClass().getSimpleName())));
	}

	@Test
	public void testIsValidated() throws Exception {
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
