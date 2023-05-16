package de.ganskef.mocuishle.cache;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import de.ganskef.mocuishle.ICache.PageMode;
import de.ganskef.mocuishle.util.LongUtil;

/**
 * Class extracted from Cache to envelop the history of outgoings and it's
 * trash.
 */
public class Outgoing {

	private static final Logger log = LoggerFactory.getLogger(Outgoing.class);

	private static final int MAX_BROWSE_COUNT = 20;

	private static final String OUTGOING_FOLDER_NAME = "outgoing";

	private static final String REFRESH_SESSION = "refresh_session";

	private final IStore mStore;

	private final Cache<String, Iterator<String>> mSession;

	public Outgoing(IStore store) {
		mStore = store;
		mSession = CacheBuilder.newBuilder() //
				.expireAfterAccess(10, TimeUnit.SECONDS) //
				.concurrencyLevel(5) //
				.build();
	}

	public Iterator<String> uriIterator(PageMode mode) {
		final File dir = initOutgoingDir();
		Map<String, File> distincts = new HashMap<String, File>();
		boolean empty = true;
		for (File each : dir.listFiles()) {
			empty = true;
			String name = each.getName();
			if (name.startsWith("O") || name.startsWith("U")) {
				distincts.put(name.substring(1), each);
			}
		}
		if (empty) {
			dir.delete();
		}
		File trash = mStore.getSpoolDir(OUTGOING_FOLDER_NAME + "_");
		if (trash.exists()) {
			for (File each : trash.listFiles()) {
				String name = each.getName();
				if (name.startsWith("O") || name.startsWith("U")) {
					File obsolete = distincts.put(name.substring(1), each);
					if (obsolete != null) {
						// obsolete.delete();
					}
				}
			}
		}
		List<File> outgoings = new ArrayList<File>(distincts.values());
		Collections.sort(outgoings, new Comparator<File>() {
			@Override
			public int compare(File o1, File o2) {
				return -LongUtil.compareLong(o1.lastModified(), o2.lastModified());
			}
		});
		List<String> uris = new LinkedList<String>();
		for (File each : outgoings) {
			LineIterator it = null;
			try {
				it = FileUtils.lineIterator(each);
				if (it.hasNext()) {
					String line = it.next();
					if (each.getName().startsWith("O")) {
						if (!line.startsWith("GET ") && !line.startsWith("HEAD ")) {
							log.warn("IGNORED METHOD in {}", line);
							// each.delete();
						} else {
							String[] strings = line.split(" ");
							String uri = strings[1];
							uris.add(uri);
						}
					}
					if (each.getName().startsWith("U")) {
						uris.add(line);
					}
					if (mode == PageMode.BROWSE && uris.size() > MAX_BROWSE_COUNT) {
						break;
					}
				}
			} catch (IOException e) {
				log.warn("Couldn't read outgoing file {}", String.valueOf(e));
			} finally {
				LineIterator.closeQuietly(it);
			}
		}
		final Iterator<String> it = uris.iterator();
		return new Iterator<String>() {

			private String current;

			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public String next() {
				current = it.next();
				return current;
			}

			@Override
			public void remove() {
				it.remove();
				String hash = mStore.getHashedName(current);
				new File(dir, "O" + hash).delete();
				new File(dir, "U" + hash).delete();
				current = null;
			}
		};
	}

	public boolean isDeleted(String uri) {
		File dir = mStore.getSpoolDir(OUTGOING_FOLDER_NAME + "_");
		if (!dir.isDirectory()) {
			return false;
		}
		String hash = mStore.getHashedName(uri);
		return new File(dir, "O" + hash).exists() || new File("U" + hash).exists();
	}

	public void deleteOutgoing(String uri) {
		File targetDir = mStore.getSpoolDir(OUTGOING_FOLDER_NAME + "_");
		File sourceDir = initOutgoingDir();
		if (uri.length() == 0) {
			mStore.moveDir(targetDir, sourceDir);
			sourceDir.delete();
		} else {
			targetDir.mkdirs();
			String hash = mStore.getHashedName(uri);
			mStore.movePage(new File(targetDir, "U" + hash), sourceDir);
			mStore.movePage(new File(targetDir, "O" + hash), sourceDir);
		}
	}

	public void undeleteOutgoing(String uri) {
		File targetDir = initOutgoingDir();
		File sourceDir = mStore.getSpoolDir(OUTGOING_FOLDER_NAME + "_");
		if (uri.length() == 0) {
			mStore.moveDir(targetDir, sourceDir);
		} else {
			String hash = mStore.getHashedName(uri);
			mStore.movePage(new File(targetDir, "U" + hash), sourceDir);
			mStore.movePage(new File(targetDir, "O" + hash), sourceDir);
		}
	}

	public void emptyTrashOutgoing() {
		File trashDir = mStore.getSpoolDir(OUTGOING_FOLDER_NAME + "_");
		mStore.deletePageDirectory(trashDir);
	}

	public boolean hasTrash() {
		return mStore.getSpoolDir(OUTGOING_FOLDER_NAME + "_").exists();
	}

	public boolean hasUris() {
		File spoolDir = mStore.getSpoolDir(OUTGOING_FOLDER_NAME);
		return spoolDir.exists();
	}

	public File initOutgoingDir() {
		File dir = mStore.getSpoolDir(OUTGOING_FOLDER_NAME);
		if (!dir.exists() && !dir.mkdirs()) {
			log.error("Can't create outgoing directoy: {}", dir);
			return null;
		}
		return dir;
	}

	public boolean isRequested(String url) {
		String hashedName = mStore.getHashedName(url);
		File outgoingFile = new File(mStore.getSpoolDir(OUTGOING_FOLDER_NAME), "/O" + hashedName);
		return outgoingFile.exists();
	}

	public Iterator<String> refreshIterator() {
		try {
			Iterator<String> it = mSession.get(REFRESH_SESSION, new Callable<Iterator<String>>() {
				@Override
				public Iterator<String> call() throws Exception {
					Outgoing.log.info("Init outgoing iterator");
					return uriIterator(PageMode.BROWSE);
				}
			});
			if (!it.hasNext()) {
				Outgoing.log.info("Invalidate outgoing iterator");
				mSession.invalidate(REFRESH_SESSION);
			}
			return it;
		} catch (ExecutionException e) {
			log.error("Can't iterate outgoing", e);
			return Collections.emptyIterator();
		}
	}
}
