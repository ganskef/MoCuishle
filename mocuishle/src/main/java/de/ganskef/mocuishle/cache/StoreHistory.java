package de.ganskef.mocuishle.cache;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.DirectoryWalker;

import com.google.common.io.Files;

public class StoreHistory extends DirectoryWalker<File> {

	private final McCache mCache;

	private final List<String> mTitles;

	private final Set<String> mReferenceds;

	private boolean mBlocked;

	private int mPassedCount;

	private int mOutdatedCount;

	private int mTitlesCount;

	private int mRefsCount;

	private int mFilesCount;

	private int mImagesCount;

	public StoreHistory(McCache cache) {
		this.mCache = cache;
		this.mReferenceds = new HashSet<>();
		this.mTitles = new ArrayList<>();
		this.mBlocked = false;
	}

	@Override
	protected void handleDirectoryStart(File directory, int depth, Collection<File> results) throws IOException {
		if (depth == 1) {
			String name = directory.getName();
			System.out.println(name);
			mBlocked = /* !name.equalsIgnoreCase("www.bookware.de") || */ mCache.isBlocked(name);
			if (mBlocked) {
				System.out.println("ignored");
			}
			mFilesCount = 0;
			mImagesCount = 0;
			mPassedCount = 0;
			mOutdatedCount = 0;
			mTitlesCount = 0;
			mRefsCount = 0;
		}
	}

	@Override
	protected void handleFile(File file, int depth, java.util.Collection<File> results) throws IOException {
		if (!mBlocked && depth == 2) {
			handleFile(file);
		}
	}

	private void handleFile(File file) {
		String name = file.getName();
		if (name.startsWith("U") && name.length() == 23 && file.length() < 2000) {
			handleUrlFile(file);
		}
	}

	private void handleUrlFile(File file) {
		try {
			String url = Files.readFirstLine(file, StandardCharsets.ISO_8859_1);
			if (url == null || mCache.isPassed(url)) {
				mPassedCount++;
				return;
			}
			if (url.substring(0, 5).equalsIgnoreCase("http:") && mCache.isCacheHit("https" + url.substring(4))) {
				// System.out.println("Prefer existing https " + url);
				mOutdatedCount++;
				return;
			}
			McElement element = mCache.createElement(url);
			if (String.valueOf(element.getCachedResponse().getContentType()).startsWith("image/")) {
				mImagesCount++;
			}
			int titlesBefore = mTitles.size();
			int refsBefore = mReferenceds.size();
			element.reorgBrowse(mTitles, mReferenceds);
			mFilesCount++;
			mTitlesCount += mTitles.size() - titlesBefore;
			mRefsCount += mReferenceds.size() - refsBefore;

		} catch (IOException | BufferUnderflowException e) {
			// ignore while reorganizing cache
		}
	}

	@Override
	protected void handleDirectoryEnd(File directory, int depth, Collection<File> results) throws IOException {
		if (depth == 1) {
			System.out.println(String.format("%s-%s-%s.%s i%s (%s)", mPassedCount, mOutdatedCount, mTitlesCount,
					mRefsCount, mImagesCount, mFilesCount));
		}
	}

	public void reorg() {
		try {
			walk(mCache.getSpoolDir("https"), null);
			walk(mCache.getSpoolDir("http"), null);
			System.out.println("titles=" + mTitles.size() + ", referenceds=" + mReferenceds.size());
			// reorgReferenceds();
			// reorgUnreferenceds();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private void reorgReferenceds() throws IOException, MalformedURLException {
		List<String> misseds = new ArrayList<String>(mReferenceds);
		misseds.removeAll(mTitles);
		for (String each : misseds) {
			if (!mCache.isPassed(each) && mCache.isCacheHit(each)) {
				McElement element = mCache.createElement(each);
				if (!mCache.isBlocked(element.getHostName())) {
					element.updateLastRecentUsage();
				}
			}
		}
	}

	private void reorgUnreferenceds() {
		// List<String> unreferenceds = new ArrayList<String>(titles);
		// unreferenceds.removeAll(referenceds);
		// System.out.println(unreferenceds.size());
	}

	public static void main(String[] args) throws Exception {
		if (args == null || args.length < 1) {
			System.out.println("Parameters needed: mocuishleDir");
			return;
		}
		File mocuishleDir = new File(args[0]);
		if (!new File(mocuishleDir, "https").isDirectory() || !new File(mocuishleDir, "http").isDirectory()) {
			System.out.println("Missed cache directories: http and https");
			return;
		}
		// File markerDir = new File(mocuishleDir, "marker");
		// markerDir.mkdirs();
		// File marker = new File(markerDir, IStore.HISTORY_TIMESTAMP_NAME);
		// marker.delete();
		// // TODO delete validate marker directories
		// // TODO merge browse and more into cleared trash
		// marker.createNewFile();
		McCache cache = McCache.create(mocuishleDir);
		new StoreHistory(cache).reorg();
	}
}
