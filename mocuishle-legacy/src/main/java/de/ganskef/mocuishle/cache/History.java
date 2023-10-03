package de.ganskef.mocuishle.cache;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.ICache.BrowseDoc;
import de.ganskef.mocuishle.ICache.BrowseHost;
import de.ganskef.mocuishle.ICache.BrowsePage;
import de.ganskef.mocuishle.ICache.PageMode;
import de.ganskef.mocuishle.util.LongUtil;

/**
 * Class extracted from Cache to envelop the history functions browse, more and
 * it's trash.
 */
public class History {

	private static final Logger log = LoggerFactory.getLogger(History.class);

	private static final int MAX_BROWSE_COUNT = 20;

	private static final String BROWSE_FOLDER_NAME = PageMode.BROWSE.name().toLowerCase();

	private static final String MORE_FOLDER_NAME = PageMode.MORE.name().toLowerCase();

	private static final String TRASH_FOLDER_NAME = PageMode.TRASH.name().toLowerCase();

	private final IStore mStore;

	public History(IStore cache) {
		mStore = cache;
	}

	private File initBrowseDir() {
		File dir = mStore.getSpoolDir(BROWSE_FOLDER_NAME);
		if (!dir.exists() && !dir.mkdirs()) {
			log.error("Can't create {} directoy {}", BROWSE_FOLDER_NAME, dir);
			return null;
		}
		return dir;
	}

	private File initMoreDir() {
		File dir = mStore.getSpoolDir(MORE_FOLDER_NAME);
		if (!dir.exists() && !dir.mkdirs()) {
			log.error("Can't create {} directoy {}", MORE_FOLDER_NAME, dir);
			return null;
		}
		return dir;
	}

	private File[] getMostRecentDirs(File parentDir) {
		if (!parentDir.isDirectory()) {
			return new File[0];
		}
		File[] results = getDirs(parentDir);
		Arrays.sort(results, new Comparator<File>() {
			public int compare(File o1, File o2) {
				return -LongUtil.compareLong(o1.lastModified(), o2.lastModified());
			}
		});
		return results;
	}

	private File[] getDirs(File parent) {
		File[] results = parent.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return pathname.isDirectory();
			}
		});
		return results;
	}

	private List<BrowseHost> getBrowseHosts() {
		File[] browseDirs = getMostRecentDirs(initBrowseDir());
		List<BrowseHost> results = new ArrayList<BrowseHost>(browseDirs.length);
		int i = 0;
		for (File hostDir : browseDirs) {
			if (i++ >= MAX_BROWSE_COUNT) {
				// TODO rename files asynchronous
				File moreDir = new File(mStore.getSpoolDir(MORE_FOLDER_NAME), hostDir.getName());
				if (!moreDir.exists()) {
					hostDir.renameTo(moreDir);
				} else {
					moreDir.mkdirs();
					for (File each : hostDir.listFiles()) {
						File moreFile = new File(moreDir, each.getName());
						moreFile.delete();
						each.renameTo(moreFile);
					}
					hostDir.delete();
				}
			} else {
				results.add(new BrowseHost(hostDir.getName(), hostDir.lastModified(), false));
			}
		}
		if (i < MAX_BROWSE_COUNT) {
			File[] moreDirs = getMostRecentDirs(initMoreDir());
			for (File moreDir : moreDirs) {
				File browseDir = new File(mStore.getSpoolDir(BROWSE_FOLDER_NAME), moreDir.getName());
				if (!browseDir.exists()) {
					moreDir.renameTo(browseDir);
					if (i++ == MAX_BROWSE_COUNT) {
						break;
					}
				}
			}
		}
		Collections.sort(results, new Comparator<BrowseHost>() {
			public int compare(BrowseHost o1, BrowseHost o2) {
				return -LongUtil.compareLong(o1.lastModified(), o2.lastModified());
			}
		});
		return results;
	}

	public List<BrowseHost> getBrowseHosts(PageMode mode) {
		if (mode == PageMode.BROWSE) {
			return getBrowseHosts();
		}
		if (mode == PageMode.TRASH) {
			List<BrowseHost> results = new ArrayList<BrowseHost>();
			addHosts(results, TRASH_FOLDER_NAME, true);
			return results;
		}
		Set<BrowseHost> distincts = new HashSet<BrowseHost>();
		addHosts(distincts, BROWSE_FOLDER_NAME, false);
		addHosts(distincts, MORE_FOLDER_NAME, false);
		addHosts(distincts, TRASH_FOLDER_NAME, true);
		List<BrowseHost> results = new ArrayList<BrowseHost>(distincts);
		Collections.sort(results, new Comparator<BrowseHost>() {
			public int compare(BrowseHost o1, BrowseHost o2) {
				return -LongUtil.compareLong(o1.lastModified(), o2.lastModified());
			}
		});
		return results;
	}

	private void addHosts(Collection<BrowseHost> results, String folderName, boolean deleted) {
		File parentDir = mStore.getSpoolDir(folderName);
		if (parentDir.isDirectory()) {
			File[] hostDirs = getDirs(parentDir);
			for (File each : hostDirs) {
				BrowseHost host = new BrowseHost(each.getName(), each.lastModified(), deleted);
				results.add(host);
			}
		}
	}

	public List<BrowsePage> getBrowsePages(String hostName, PageMode mode) {
		if (mode == PageMode.BROWSE) {
			return getBrowsePages(hostName);
		}
		if (mode == PageMode.TRASH) {
			List<BrowsePage> results = new ArrayList<BrowsePage>();
			addPages(TRASH_FOLDER_NAME, hostName, results, true);
			return results;
		}
		Set<BrowsePage> distincts = new HashSet<BrowsePage>();
		addPages(BROWSE_FOLDER_NAME, hostName, distincts, false);
		addPages(MORE_FOLDER_NAME, hostName, distincts, false);
		addPages(TRASH_FOLDER_NAME, hostName, distincts, true);
		List<BrowsePage> results = new ArrayList<BrowsePage>(distincts);
		Collections.sort(results, new Comparator<BrowsePage>() {
			public int compare(BrowsePage o1, BrowsePage o2) {
				return -LongUtil.compareLong(o1.lastModified(), o2.lastModified());
			}
		});
		return results;
	}

	private void addPages(String dirName, String hostName, Collection<BrowsePage> results, boolean deleted) {
		File moreDir = new File(mStore.getSpoolDir(dirName), hostName);
		if (moreDir.isDirectory()) {
			File[] pageFiles = moreDir.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return pathname.isFile();
				}
			});
			for (File each : pageFiles) {
				addPage(hostName, results, each, deleted);
			}
		}
	}

	private List<BrowsePage> getBrowsePages(String hostName) {
		File browseDir = new File(initBrowseDir(), hostName);
		File[] browseFiles = browseDir.isDirectory() ? browseDir.listFiles() : new File[0];
		Arrays.sort(browseFiles, new Comparator<File>() {
			public int compare(File o1, File o2) {
				return -LongUtil.compareLong(o1.lastModified(), o2.lastModified());
			}
		});
		File moreDir = new File(initMoreDir(), hostName);
		if (browseFiles.length >= MAX_BROWSE_COUNT) {
			moreDir.mkdirs();
		}
		List<BrowsePage> results = new ArrayList<BrowsePage>();
		int i = 0;
		for (File browseFile : browseFiles) {
			if (i++ >= MAX_BROWSE_COUNT) {
				File moreFile = new File(moreDir, browseFile.getName());
				long lastModified = browseFile.lastModified();
				moreFile.delete();
				browseFile.renameTo(moreFile);
				moreDir.setLastModified(lastModified);
			} else {
				addPage(hostName, results, browseFile, false);
			}
		}
		if (i < MAX_BROWSE_COUNT && moreDir.isDirectory()) {
			long timeStamp = moreDir.lastModified();
			File[] moreFiles = moreDir.listFiles();
			Arrays.sort(moreFiles, new Comparator<File>() {
				public int compare(File o1, File o2) {
					return -LongUtil.compareLong(o1.lastModified(), o2.lastModified());
				}
			});
			for (File moreFile : moreFiles) {
				File browseFile = new File(browseDir, moreFile.getName());
				if (browseDir.exists() && !browseFile.exists()) {
					moreFile.renameTo(browseFile);
					addPage(hostName, results, browseFile, false);
				} else {
					addPage(hostName, results, moreFile, false);
				}
				if (i++ == MAX_BROWSE_COUNT) {
					break;
				}
			}
			moreDir.setLastModified(timeStamp);
		}
		return results;
	}

	private void addPage(String hostName, Collection<BrowsePage> results, File browseFile, boolean deleted) {
		File urlFile = mStore.getPageUrlFile(hostName, browseFile);
		File dataFile = new File(urlFile.getParentFile(), "D" + urlFile.getName().substring(1));
		if (!urlFile.isFile() || !dataFile.isFile()) {
			log.warn("Missed {}{} for {}", urlFile.exists() ? "" : "U", dataFile.exists() ? "" : "D",
					browseFile.getName());
			browseFile.delete();
			urlFile.delete();
			dataFile.delete();
			return;
		}
		try {
			String url = FileUtils.readFileToString(urlFile, McElement.DEFAULT_ENCODING);
			String input = FileUtils.readFileToString(browseFile, McElement.DEFAULT_ENCODING);
			String title = (input.trim().length() == 0) ? url
					: Jsoup.parse(String.valueOf(convertEncoding(input))).text();
			results.add(new BrowsePage(url, title, browseFile.lastModified(), deleted));
		} catch (IOException e) {
			log.error("Couldn't read browse page", e);
			browseFile.delete();
		}
	}

	private CharSequence convertEncoding(CharSequence input) {
		/*
		 * Mo Cuishle cache handles text/html always with ISO-8859-1 to decodes the
		 * bytes 1:1 into chars and thus is transparent when recoding to bytes. To get
		 * the right umlauts, text has to be converted, if it was UTF-8.
		 */
		try {
			byte[] bytes = String.valueOf(input).getBytes(McElement.DEFAULT_ENCODING);
			ByteBuffer in = ByteBuffer.wrap(bytes);
			CharBuffer buffer = mStore.decodeUTF8(in);
			return buffer;
		} catch (CharacterCodingException e) {
			return input;
		}
	}

	public void recordBrowsing(File hostDir, String hashedName, String title) throws IOException {
		if (mStore.isCacheOnly()) {
			log.debug("Validate Cache {} {} {}", title, hashedName, hostDir);
			return;
		}
		reorgBrowse(hostDir, hashedName, title, System.currentTimeMillis());
	}

	public void reorgBrowse(File hostDir, String hashedName, String title, long lastModified) throws IOException {
		File browseDir = initBrowseDir();
		File dir = new File(browseDir, hostDir.getName());
		File file = new File(dir, hashedName);
		long dirModified = Math.max(dir.lastModified(), lastModified);
		dir.mkdirs();
		FileUtils.write(file, title, McElement.DEFAULT_ENCODING);
		file.setLastModified(lastModified);
		dir.setLastModified(dirModified);
		// remove more entry if exists, more dir shouldn't be checked every time
		// so it could be empty to be deleted manually
		//
		new File(browseDir.getParent(), MORE_FOLDER_NAME + "/" + hostDir.getName() + '/' + hashedName).delete();
		new File(browseDir.getParent(), TRASH_FOLDER_NAME + "/" + hostDir.getName() + '/' + hashedName).delete();
	}

	public void deleteBrowsePath(String scheme, String hostName, String path) {
		move(scheme, hostName, path, BROWSE_FOLDER_NAME, TRASH_FOLDER_NAME);
	}

	public void undeleteBrowsePath(String scheme, String hostName, String path) {
		move(scheme, hostName, path, TRASH_FOLDER_NAME, BROWSE_FOLDER_NAME);
		File trashDir = mStore.getSpoolDir(TRASH_FOLDER_NAME);
		if (trashDir.list().length == 0) {
			trashDir.delete();
		}
	}

	private void move(String scheme, String hostName, String path, String source, String target) {
		File targetDir = mStore.getSpoolDir(target, hostName);
		File sourceDir = mStore.getSpoolDir(source, hostName);
		File moreDir = mStore.getSpoolDir(MORE_FOLDER_NAME, hostName);
		if (path != null && path.length() > 0) {
			targetDir.mkdirs();
			String hashedName = mStore.getHashedName(scheme + "://" + hostName + path);
			File targetFile = new File(targetDir, hashedName);
			mStore.movePage(targetFile, targetDir);
			mStore.movePage(targetFile, moreDir);
			mStore.movePage(targetFile, sourceDir);
		} else {
			mStore.moveDir(targetDir, targetDir);
			mStore.moveDir(targetDir, moreDir);
			mStore.moveDir(targetDir, sourceDir);
		}
	}

	public void emptyTrash() {
		File trashDir = mStore.getSpoolDir(TRASH_FOLDER_NAME);
		File[] hostDirs = trashDir.listFiles();
		if (hostDirs == null) {
			log.warn("Trash is already empty");
			return;
		}
		for (File hostDir : hostDirs) {
			mStore.deletePageDirectory(hostDir);
		}
		mStore.deletePageDirectory(trashDir);
	}

	public boolean hasTrash() {
		return mStore.getSpoolDir(TRASH_FOLDER_NAME).exists();
	}

	public String getTitle(BrowseDoc doc) {
		String path = getPathSuffix(doc);
		File browseFile = new File(mStore.getSpoolDir(BROWSE_FOLDER_NAME), path);
		String result;
		if (browseFile.exists()) {
			result = mStore.readFileToString(browseFile);
		} else {
			File moreFile = new File(mStore.getSpoolDir(MORE_FOLDER_NAME), path);
			result = mStore.readFileToString(moreFile);
		}
		String encoded = convertEncoding(result).toString();
		// Unmasking HTML from the title text
		return Jsoup.parse(encoded).text();
	}

	public boolean isDeleted(BrowseDoc doc) {
		String path = getPathSuffix(doc);
		return new File(mStore.getSpoolDir(TRASH_FOLDER_NAME), path).exists();
	}

	private String getPathSuffix(BrowseDoc doc) {
		String hash = doc.getHash();
		int firstSlash = hash.indexOf('/');
		String path = hash.substring(firstSlash);
		return path;
	}

	public BrowseDoc createBrowseDoc(String uri) {
		return mStore.createBrowseDoc(uri);
	}

	public boolean isWelcome() {
		return !mStore.getSpoolDir(BROWSE_FOLDER_NAME).exists();
	}
}
