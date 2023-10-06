package de.ganskef.mocuishle.cache;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.DirectoryWalker;

public class StoreMarker extends DirectoryWalker<File> {

	public List<File> read(File startDirectory) {
		List<File> results = new ArrayList<>();
		try {
			walk(startDirectory, results);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return results;
	}

	protected void handleFile(File file, int depth, Collection<File> results) {
		if (depth > 0) {
			results.add(file);
		}
	}
}
