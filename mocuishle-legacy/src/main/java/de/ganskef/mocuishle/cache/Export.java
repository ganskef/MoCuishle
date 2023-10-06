package de.ganskef.mocuishle.cache;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.ICacheableProxy.CachedResponse;

/**
 * Exports the cached contents of a host in a Zip archive. Create a file for
 * every path, duplicates with a leading underscore.
 *
 * <p>
 * TODO: Do it multithreaded and asynchronous.
 *
 * @see <a href=
 *      "https://docs.oracle.com/javase/7/docs/technotes/guides/io/fsp/zipfilesystemprovider.html">Zip
 *      File System Provider</a>
 * @see <a href=
 *      "http://www.pixeldonor.com/2013/oct/12/concurrent-zip-compression-java-nio/">Concurrent
 *      ZIP Compression With Java NIO</a>
 */
public class Export {

	private static final Logger log = LoggerFactory.getLogger(Export.class);

	private static final Pattern HOST_FROM_PATH_PATTERN = Pattern.compile("([\\w-_\\.]+).*");

	private static final Pattern PATH_WITH_OPTIONAL_SCHEME_PATTERN = Pattern.compile("(https?)/(.*)");

	private static final String EXPORT_FOLDER_NAME = "export";

	private IStore mStore;

	private Map<String, String> mPathUrls;

	public Export(IStore store) {
		mStore = store;
		mPathUrls = new HashMap<>();
	}

	public void cleanExportDir() {
		File exportDir = mStore.getSpoolDir(EXPORT_FOLDER_NAME);
		if (!(exportDir.exists() || exportDir.mkdirs())) {
			log.warn("Can't create export directory {}", exportDir);
			return;
		}
		for (File each : exportDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isDirectory();
			}
		})) {
			deleteRecursive(each);
		}
	}

	public String export(String pathWithOptionalScheme) {
		Matcher m = PATH_WITH_OPTIONAL_SCHEME_PATTERN.matcher(pathWithOptionalScheme);
		String path;
		if (m.matches()) {
			String scheme = m.group(1);
			path = m.group(2);
			selectFiles(scheme, path);
		} else {
			path = pathWithOptionalScheme;
			selectFiles("http", path);
			selectFiles("https", path);
		}
		return exportFiles(path).toString();
	}

	private void selectFiles(String scheme, String input) {
		String hostName = getHostNameFromPath(input);
		if (hostName == null) {
			return;
		}
		String exportedUri = scheme + "://" + urlDecode(input);
		File hostDir = mStore.getSpoolDir(scheme, hostName);
		if (!hostDir.isDirectory()) {
			// no input
			return;
		}
		File[] files = hostDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isFile() && pathname.getName().startsWith("U");
			}
		});
		if (files.length == 0) {
			// no input
			return;
		}
		Arrays.sort(files, new Comparator<File>() {
			@Override
			public int compare(File o1, File o2) {
				return Long.compare(o1.lastModified(), o2.lastModified());
			}
		});
		final Random rnd = new Random();
		rnd.setSeed(System.currentTimeMillis());
		for (File each : files) {
			try {
				String url = FileUtils.readFileToString(each);
				log.debug(url);
				if (url.startsWith(exportedUri)) {
					try {
						String path = URLDecoder.decode(new URL(url).getPath(), "UTF-8");
						String previous = mPathUrls.put(path, url);
						if (previous != null) {
							Path p = Paths.get(path);
							// using 48bit random number
							long sl = ((long) rnd.nextInt()) << 32 | (rnd.nextInt() & 0xFFFFFFFFL);
							// let reserve of 16 bit for increasing, serials have to be positive
							sl = sl & 0x0000FFFFFFFFFFFFL;
							path = String.format("%s/_%s_%s", p.getParent(), sl, p.getFileName());
							previous = mPathUrls.put(path, previous);
						}
						while (previous != null) {
							// additional underscore, should never happens
							Path p = Paths.get(path);
							path = String.format("%s/_%s", p.getParent(), p.getFileName());
							previous = mPathUrls.put(path, previous);
						}
					} catch (IllegalArgumentException e) {
						log.warn("Can't parse requested URL " + url, e);
					}
				}
			} catch (IOException e) {
				log.warn("Can't read " + each, e);
			}
		}
	}

	class Requested {
	}

	// TODO: looks like a workaround to create a ZIP file
	static final byte[] EMPTY_ZIP = { 80, 75, 05, 06, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00,
			00, 00 };

	private Path exportFiles(String input) {
		String hostName = getHostNameFromPath(input);
		if (hostName == null) {
			// no input
			return null;
		}
		File exportDir = mStore.getSpoolDir(EXPORT_FOLDER_NAME);
		if (!(exportDir.exists() || exportDir.mkdirs())) {
			log.warn("Can't create export directory {}", exportDir);
			return null;
		}
		try {
			long ms = System.currentTimeMillis();
			Path zipFile = Files.createTempFile(exportDir.toPath(), hostName, ".zip").normalize().toAbsolutePath();
			Files.write(zipFile, EMPTY_ZIP, StandardOpenOption.WRITE);
			URI uri = URI.create("jar:" + zipFile.toUri());
			Map<String, String> env = new HashMap<>();
			env.put("create", "true");
			try (FileSystem zipfs = FileSystems.newFileSystem(uri, env)) {
				for (Map.Entry<String, String> each : mPathUrls.entrySet()) {
					Path parent = zipfs.getPath(hostName, each.getKey()).getParent();
					if (parent != null && !Files.exists(parent)) {
						Files.createDirectories(parent);
					}
				}
				for (Map.Entry<String, String> each : mPathUrls.entrySet()) {
					CachedResponse cr = null;
					try {
						cr = mStore.createCachedResponse(each.getValue());
					} catch (Exception e) {
						log.error("Cache read failed", e);
					}
					if (cr != null) {
						Path entry = zipfs.getPath(hostName, each.getKey());
						if (Files.isDirectory(entry)) {
							entry = entry.resolve("index.html");
						}
						while (Files.exists(entry)) {
							entry = entry.resolveSibling("_" + entry.getFileName());
						}
						Set<StandardOpenOption> options = new HashSet<>();
						options.add(StandardOpenOption.CREATE_NEW);
						options.add(StandardOpenOption.WRITE);
						try (FileChannel writeableChannel = zipfs.provider().newFileChannel(entry, options)) {
							ByteBuffer buffer = cr.getContent();
							writeableChannel.write(buffer);
							writeableChannel.close();
							buffer.clear();
						}
						Files.setLastModifiedTime(entry, FileTime.fromMillis(cr.getLoadedDate()));
					}
				}
			}
			log.info("Export ZIP of {} in {}ms", hostName, System.currentTimeMillis() - ms);
			return zipFile;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private String getHostNameFromPath(String path) {
		Matcher m = HOST_FROM_PATH_PATTERN.matcher(path);
		if (m.matches()) {
			return m.group(1);
		}
		return null;
	}

	private void deleteRecursive(File file) {
		if (file.isDirectory()) {
			for (File each : file.listFiles()) {
				deleteRecursive(each);
			}
		}
		file.delete();
	}

	private String urlDecode(String input) {
		try {
			return URLDecoder.decode(input, "UTF-8").replace("%20", " ");
		} catch (UnsupportedEncodingException e) {
			return input;
		}
	}
}
