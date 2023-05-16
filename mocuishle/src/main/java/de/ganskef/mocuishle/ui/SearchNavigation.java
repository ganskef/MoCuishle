package de.ganskef.mocuishle.ui;

import de.ganskef.mocuishle.IAction;
import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.ICache.BrowseDoc;
import de.ganskef.mocuishle.IFullTextSearch;
import de.ganskef.mocuishle.IFullTextSearch.SearchResult;
import de.ganskef.mocuishle.Markup;
import de.ganskef.mocuishle.ui.SpecialUrl.Command;

public class SearchNavigation extends AbstractMarkupNavigation implements IAction {

	private final ICache mCache;

	private final Command mCommand;

	private final String mQuery;

	private final int mIndex;

	private final int mCount;

	private final String mHostName;

	public SearchNavigation(ICache cache, Command command, String hostName, String query, int index, int count) {
		if (cache == null) {
			throw new IllegalArgumentException("cache is null");
		}
		if (command == null) {
			throw new IllegalArgumentException("command is null");
		}
		this.mCache = cache;
		this.mCommand = command;
		this.mHostName = hostName;
		this.mQuery = query;
		this.mIndex = index;
		this.mCount = count;
	}

	@Override
	public String prepareAnswer() {
		String markup = null;
		Markup.Search def = mCache.getMarkup().getSearch();
		IFullTextSearch search = mCache.getFullTextSearch();
		StringBuilder b = new StringBuilder();
		SearchResult result = search.query(mQuery, mIndex, mCount);
		if (!result.isError()) {
			for (BrowseDoc each : result.getBrowseDocs()) {
				String pageUrl = mCache.getUrl(each);
				Object shortPath = Markup.urlDecoded(Markup.truncated(pageUrl, 0));
				Object pagePath = Markup.xmlEncoded(pageUrl.replaceFirst("://", "/"));
				String title = Markup.Truncated.truncate(mCache.getHistory().getTitle(each), 0, 60);
				Object snippet = each.getContent();
				String format = mCache.getHistory().isDeleted(each) ? def.getPagesDeleted() : def.getPagesElement();
				b.append(String.format(format, Markup.xmlEncoded(pageUrl), shortPath, title, snippet, pagePath));
			}
		}
		Object trashLink = String.format(mCache.getHistory().hasTrash() ? def.getTrashFull() : def.getTrashEmpty(),
				mCommand, mHostName);
		String emptyTrashLink = String.format(
				mCache.getHistory().hasTrash() ? def.getEmptyTrashFull() : def.getEmptyTrashEmpty(), mCommand,
				mHostName);
		Object allLink = (mCount < result.getCount())
				? String.format(def.getSwitchAll(), mCommand, mHostName, Markup.urlEncoded(mQuery), result.getCount())
				: "";

		boolean isPrev = mIndex > 0;
		boolean isNext = result.getCount() > ((mIndex + 1) * mCount);
		String prev = String.format(isPrev ? def.getPrevEnabled() : def.getPrevDisabled(), mCommand, mHostName,
				Markup.urlEncoded(mQuery), mCount, mIndex - 1);
		String next = String.format(isNext ? def.getNextEnabled() : def.getNextDisabled(), mCommand, mHostName,
				Markup.urlEncoded(mQuery), mCount, mIndex + 1);

		Object indexed = mCache.getMarkup().loaded(mCache.getFullTextIndex());

		markup = String.format(def.getPages(), Markup.xmlEncoded(mQuery), "currentHostsMarkup()", b, mCommand,
				mHostName, allLink, trashLink, emptyTrashLink, prev, next, indexed);
		return markup;
	}
}
