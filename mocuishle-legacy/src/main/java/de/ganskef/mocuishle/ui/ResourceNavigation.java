package de.ganskef.mocuishle.ui;

import java.nio.file.Path;
import java.nio.file.Paths;

import de.ganskef.mocuishle.IActionPath;
import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.Markup;

/** Answers a resource from the markup folder */
public class ResourceNavigation extends AbstractMarkupNavigation implements IActionPath {

	private final ICache mCache;

	private final String mPath;

	public ResourceNavigation(ICache cache, String path) {
		if (cache == null) {
			throw new IllegalArgumentException("cache is null");
		}
		if (path == null) {
			throw new IllegalArgumentException("path is null");
		}
		mCache = cache;
		mPath = path;
	}

	@Override
	public Path getPath() {
		return Paths.get(mPath);
	}

	@Override
	public String prepareAnswer() {
		Markup def = mCache.getMarkup();
		String resource = def.getResource(mPath);
		return resource;
	}
}
