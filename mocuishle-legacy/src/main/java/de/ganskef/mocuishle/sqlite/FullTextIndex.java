package de.ganskef.mocuishle.sqlite;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteJob;
import com.almworks.sqlite4java.SQLiteQueue;
import com.almworks.sqlite4java.SQLiteStatement;

import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.ICache.BrowseDoc;
import de.ganskef.mocuishle.IFullTextIndex;
import de.ganskef.mocuishle.Markup;

public class FullTextIndex implements IFullTextIndex {

	private static final String TABLE_DDL =
			// "CREATE VIRTUAL TABLE documents USING fts5(path, content);";

			"CREATE VIRTUAL TABLE documents USING fts3("
					+ "path TEXT NOT NULL, content TEXT, tokenize=unicode61 \"tokenchars=.=@\");";

	private static final String INSERT_SQL = "INSERT INTO documents VALUES (?, ?);";

	private static final Logger log = LoggerFactory.getLogger(FullTextIndex.class);

	private static final String DB_FILE_NAME = "mocuishle.sqlite";
	private static final String TEMP_FILE_NAME = "mocuishle.sqlite.tmp";

	static {
		FullTextSearch.initSqlite();
	}

	private ICache mCache;

	public FullTextIndex(ICache cache) {
		mCache = cache;
	}

	@Override
	public void replaceIndex() {
		for (File each : mCache.getWritableDir().listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isFile() && pathname.getName().startsWith(TEMP_FILE_NAME);
			}
		})) {
			delete(each);
		}
		final File tempFile = new File(mCache.getWritableDir(), TEMP_FILE_NAME);
		try {
			tempFile.createNewFile();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

		// FIXME is that a misuse of such a queue? Should be only once and
		// should be reused.
		//
		final SQLiteQueue queue = new SQLiteQueue(tempFile) {
			/**
			 * Disable reincarnation after indexing is failing to avoid an endless loop
			 * flooding the log files.
			 */
			@Override
			protected long getReincarnationTimeout() {
				return -1;
			}
		};
		queue.start();
		queue.execute(new SQLiteJob<Integer>() {
			@Override
			protected Integer job(SQLiteConnection connection) throws Throwable {
				final long ms = System.currentTimeMillis();
				int counter = 0;
				SQLiteConnection db = null;
				try {
					db = new SQLiteConnection(tempFile);
					db.open(true);
					// if (!dbExists) {
					db.exec(TABLE_DDL);
					// }
					SQLiteStatement st = db.prepare(INSERT_SQL);
					try {
						Iterator<BrowseDoc> it = mCache.browseDocIterator();
						while (it.hasNext()) {
							counter++;
							BrowseDoc each = it.next();
							String hash = each.getHash();
							CharSequence content = each.getContent();
							st.bind(1, hash);
							st.bind(2, content.toString());
							st.step();
							st.reset();
						}
					} finally {
						st.dispose();
					}
				} finally {
					if (db != null) {
						db.dispose();
					}
				}
				log.info("Indexed {} documents in {}ms", counter, System.currentTimeMillis() - ms);
				delete(getDbFile());
				// Windows can't rename this file, but the DbFile is deleted. It
				// works to rename it on first search, but it could be better.
				//
				tempFile.renameTo(getDbFile());
				queue.stop(true);
				return null;
			}
		});
	}

	private void delete(File removed) {
		if (!removed.delete() && removed.exists()) {
			log.warn("Delete failed, try on exit {}", removed);
			removed.deleteOnExit();
		}
	}

	// private File availableFile(File current) {
	// File available = current;
	// int i = 1;
	// while (available.exists()) {
	// available = new File(current.getPath() + i++);
	// }
	// return available;
	// }

	private File getDbFile() {
		return new File(mCache.getWritableDir(), DB_FILE_NAME);
	}

	@Override
	public long getIndexSize() {
		return getDbFile().length();
	}

	public static boolean checkSqlite() {
		SQLiteConnection db = null;
		try {
			db = new SQLiteConnection(null);
			db.open();
			return true;
		} catch (SQLiteException e) {
			return false;
		} finally {
			if (db != null) {
				db.dispose();
			}
		}
	}

	// @Override
	// public void insertOrReplace(BrowseDoc document) {
	// File dbFile = getDbFile();
	// SQLiteConnection db = null;
	// try {
	// db = new SQLiteConnection(dbFile);
	// db.open(true);
	//
	// SQLiteStatement st = db.prepare(INSERT_SQL);
	// try {
	// insertOrReplace(st, document.getHash(), document.getContent());
	// } catch (SQLiteException e) {
	// log.error("Failed to insert " + document, e);
	// } finally {
	// st.dispose();
	// }
	//
	// } catch (SQLiteException e) {
	// log.error("Failed to open db " + dbFile, e);
	// } finally {
	// db.dispose();
	// }
	// }

	@Override
	public long lastModified() {
		return getDbFile().lastModified();
	}

	@Override
	public Markup getMarkup() {
		return mCache.getMarkup();
	}
}
