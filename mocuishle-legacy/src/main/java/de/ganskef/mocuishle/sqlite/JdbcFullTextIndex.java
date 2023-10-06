package de.ganskef.mocuishle.sqlite;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;

import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.ICache.BrowseDoc;
import de.ganskef.mocuishle.IFullTextIndex;
import de.ganskef.mocuishle.Markup;

public class JdbcFullTextIndex implements IFullTextIndex {

	private static final String TABLE_DDL = "CREATE VIRTUAL TABLE documents USING fts5(path, content);";

	private static final String INSERT_SQL = "INSERT INTO documents VALUES (?, ?);";

	private static final Logger log = LoggerFactory.getLogger(JdbcFullTextIndex.class);

	private static final String DB_FILE_NAME = "mocuishle-fts3.sqlite";
	private static final String TEMP_FILE_NAME = "mocuishle-fts3.sqlite.tmp";

	private ICache mCache;

	public JdbcFullTextIndex(ICache cache) {
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
		final File dbfile = availableFile(new File(mCache.getWritableDir(), TEMP_FILE_NAME));
		try {
			dbfile.createNewFile();
			Class.forName("org.sqlite.JDBC");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
		Runnable job = new Runnable() {
			@Override
			public void run() {
				final long ms = System.currentTimeMillis();
				int counter = 0;
				Connection db = null;
				try {
					db = DriverManager.getConnection("jdbc:sqlite:" + dbfile.getAbsolutePath());
					Statement statement = db.createStatement();
					statement.setQueryTimeout(30);
					statement.executeUpdate(TABLE_DDL);

					PreparedStatement st = db.prepareStatement(INSERT_SQL);
					Iterator<BrowseDoc> it = mCache.browseDocIterator();
					while (it.hasNext()) {
						counter++;
						BrowseDoc each = it.next();
						String hash = each.getHash();
						CharSequence content = each.getContent();
						st.setString(1, hash);
						st.setString(2, content.toString());
						st.execute();
					}
				} catch (SQLException e) {
					log.error("Error while inserting into " + db, e);
				} finally {
					if (db != null) {
						try {
							db.close();
						} catch (SQLException e) {
							// ignore
						}
					}
				}
				File removed = availableFile(new File(mCache.getWritableDir(), TEMP_FILE_NAME));
				getDbFile().renameTo(removed);
				delete(removed);
				dbfile.renameTo(getDbFile());
				log.info("Indexed {} documents in {}ms", counter, System.currentTimeMillis() - ms);
			}
		};
		job.run();
	}

	private void delete(File removed) {
		if (!removed.delete() && removed.exists()) {
			log.warn("Delete failed, try on exit {}", removed);
			removed.deleteOnExit();
		}
	}

	private File availableFile(File current) {
		File available = current;
		int i = 1;
		while (available.exists()) {
			available = new File(current.getPath() + i++);
		}
		return available;
	}

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

	@Override
	public long lastModified() {
		return getDbFile().lastModified();
	}

	@Override
	public Markup getMarkup() {
		return mCache.getMarkup();
	}
}
