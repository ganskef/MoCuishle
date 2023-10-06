package de.ganskef.mocuishle;

import java.util.List;

import de.ganskef.mocuishle.ICache.BrowseDoc;

/**
 * Interface used by the proxy UI to perform a Full Text Search and receive the
 * results.
 */
public interface IFullTextSearch {

	public static class SearchResult {

		private final List<BrowseDoc> browseDocs;
		private final long count;
		private final int pageIndex;
		private final int pageSize;

		public SearchResult(List<BrowseDoc> browseDocs, long count, int pageIndex, int pageSize) {
			this.browseDocs = browseDocs;
			this.count = count;
			this.pageIndex = pageIndex;
			this.pageSize = pageSize;
		}

		public SearchResult() {
			this(null, 0, 0, 0);
		}

		public boolean isError() {
			return browseDocs == null;
		}

		public List<BrowseDoc> getBrowseDocs() {
			return browseDocs;
		}

		public long getCount() {
			return count;
		}

		public int getPageIndex() {
			return pageIndex;
		}

		public int getPageSize() {
			return pageSize;
		}
	}

	SearchResult query(String queryString, int index, int count);
}
