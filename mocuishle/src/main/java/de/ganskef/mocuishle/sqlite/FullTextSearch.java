package de.ganskef.mocuishle.sqlite;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;

import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.IFullTextSearch;
import de.ganskef.mocuishle.Markup;

public class FullTextSearch implements IFullTextSearch {

	private static final String DB_FILE_NAME = "mocuishle.sqlite";
	private static final String TEMP_FILE_NAME = "mocuishle.sqlite.tmp";

	private static final String COUNT_SQL = "SELECT count(*) FROM documents WHERE content MATCH ?";
	private static final String DATA_SQL = "SELECT path, snippet(documents, '<mark>', '</mark>', '...', 1, -64) FROM documents "
			+ "WHERE content MATCH ? %1$s LIMIT ? OFFSET ?";
	private static final String ORDER_BY_BEST_MATCH = "ORDER BY length(offsets(documents))";
	// XXX Should I use docid or rowid here?
	private static final String ORDER_BY_LAST_USAGE = "ORDER BY docid DESC";
	private static final int COUNT_FOR_ORDER_BY_USAGE = 100;

	private static final Logger log = LoggerFactory.getLogger(FullTextSearch.class);

	private SQLiteConnection mDatabase;
	private SQLiteStatement mPageStatement;
	private SQLiteStatement mCountStatement;
	private SQLiteStatement mManyStatement;

	private final ICache mCache;

	static {
		initSqlite();
	}

	static void initSqlite() {
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
	}

	public FullTextSearch(ICache cache) {
		mCache = cache;
	}

	@Override
	public IFullTextSearch.SearchResult query(String query, int index, int count) {
		try {
			openDatabase();
			final long ms = System.currentTimeMillis();

			String queryString = Markup.xmlDecoded(query).toString();
			mCountStatement.bind(1, queryString);
			mCountStatement.step();
			long selected = mCountStatement.columnLong(0);
			mCountStatement.reset();

			long offset = index * count;
			List<ICache.BrowseDoc> results = new ArrayList<ICache.BrowseDoc>();
			SQLiteStatement st = (selected < COUNT_FOR_ORDER_BY_USAGE) ? getPageStatement() : getManyStatement();
			st.bind(1, queryString);
			st.bind(2, (long) count);
			st.bind(3, (long) offset);

			while (st.step()) {
				int row = 0;
				String hash = st.columnString(row++);
				CharSequence content = st.columnString(row++);
				results.add(new ICache.BrowseDoc(hash, content));
			}
			st.reset();

			log.info("Found {} in {}ms for {}", selected, System.currentTimeMillis() - ms, queryString);

			return new IFullTextSearch.SearchResult(results, selected, index, count);
		} catch (SQLiteException e) {
			log.info("Query failed: {}", e.getLocalizedMessage());
			return new IFullTextSearch.SearchResult();
		} finally {
			dispose();
		}
	}

	public void openDatabase() throws SQLiteException {
		final long ms = System.currentTimeMillis();
		File dbfile = new File(mCache.getWritableDir(), DB_FILE_NAME);
		if (!dbfile.exists()) {
			// Windows can't rename the index file after create, but deletes the
			// DbFile. This is a workaround to fix it.
			//
			File tmpFile = new File(mCache.getWritableDir(), TEMP_FILE_NAME);
			tmpFile.renameTo(dbfile);
		}
		mDatabase = new SQLiteConnection(dbfile);
		mDatabase.open(true);
		mCountStatement = mDatabase.prepare(COUNT_SQL);
		mPageStatement = getPageStatement();
		log.debug("Open db {}ms {} bytes", System.currentTimeMillis() - ms, dbfile.length());
	}

	private SQLiteStatement getPageStatement() throws SQLiteException {
		if (mPageStatement == null) {
			mPageStatement = mDatabase.prepare(String.format(DATA_SQL, ORDER_BY_BEST_MATCH));
		}
		return mPageStatement;
	}

	private SQLiteStatement getManyStatement() throws SQLiteException {
		if (mManyStatement == null) {
			mManyStatement = mDatabase.prepare(String.format(DATA_SQL, ORDER_BY_LAST_USAGE));
		}
		return mManyStatement;
	}

	public void dispose() {
		if (mPageStatement != null) {
			mPageStatement.dispose();
		}
		if (mManyStatement != null) {
			mManyStatement.dispose();
		}
		if (mCountStatement != null) {
			mCountStatement.dispose();
		}
		if (mDatabase != null) {
			mDatabase.dispose();
		}
	}
}
