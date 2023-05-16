package de.ganskef.mocuishle.ui;

import de.ganskef.mocuishle.IAction;
import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.Markup;
import de.ganskef.mocuishle.ui.SpecialUrl.Command;

public class BrowseNavigation extends AbstractMarkupNavigation implements IAction {

	private final ICache mCache;

	private final Command mCommand;

	private final String mBrowsedHost;

	private final Requested mRequesteds;

	public BrowseNavigation(ICache cache, Requested requesteds, Command command, String browsedHost) {
		if (cache == null) {
			throw new IllegalArgumentException("cache is null");
		}
		if (requesteds == null) {
			throw new IllegalArgumentException("requesteds is null");
		}
		if (command == null) {
			throw new IllegalArgumentException("command is null");
		}
		this.mCache = cache;
		this.mRequesteds = requesteds;
		this.mCommand = command;
		this.mBrowsedHost = browsedHost;
	}

	@Override
	public String prepareAnswer() {
		String markup = null;
		Markup.Browse def = mCache.getMarkup().getBrowse();
		ICache.PageMode pageMode = ICache.PageMode.forCommand(mCommand.toString());
		String switchTemplate = (pageMode == ICache.PageMode.MORE) ? def.getSwitchQuick() : def.getSwitchAll();
		if (mBrowsedHost == null || mBrowsedHost.length() == 0) {
			StringBuilder b = new StringBuilder();
			for (ICache.BrowseHost each : mCache.getHistory().getBrowseHosts(pageMode)) {
				String hostName = each.getHostName();
				String format = each.isDeleted() ? def.getHostsDeleted() : def.getHostsElement();
				b.append(String.format(format, hostName, mCommand));
			}
			String moreLink = String.format(switchTemplate, "");
			String trashLink = String.format(mCache.getHistory().hasTrash() ? def.getTrashFull() : def.getTrashEmpty(),
					mCommand, "");
			String emptyTrashLink = String.format(
					mCache.getHistory().hasTrash() ? def.getEmptyTrashFull() : def.getEmptyTrashEmpty(), mCommand, "");
			markup = String.format(def.getHosts(), currentHostsMarkup(), b, mCommand, moreLink, trashLink,
					emptyTrashLink);
		} else {
			String hostName = mBrowsedHost;
			StringBuilder b = new StringBuilder();
			for (ICache.BrowsePage each : mCache.getHistory().getBrowsePages(hostName, pageMode)) {
				String pageUrl = each.getUrl();
				String pagePath = pageUrl.replaceFirst("://", "/");
				int beginIndex = hostName.length() + pagePath.indexOf('/') + 1;
				Object shortPath = Markup.truncated(Markup.urlDecoded(pagePath), beginIndex);
				String title = Markup.Truncated.truncate(each.getTitle(), 0, 60);
				String format = each.isDeleted() ? def.getPagesDeleted() : def.getPagesElement();
				b.append(String.format(format, Markup.xmlEncoded(pageUrl), shortPath, title, hostName,
						Markup.xmlEncoded(pagePath)));
			}
			String moreLink = String.format(switchTemplate, hostName);
			String trashLink = String.format(mCache.getHistory().hasTrash() ? def.getTrashFull() : def.getTrashEmpty(),
					mCommand, mBrowsedHost);
			String emptyTrashLink = String.format(
					mCache.getHistory().hasTrash() ? def.getEmptyTrashFull() : def.getEmptyTrashEmpty(), mCommand,
					mBrowsedHost);
			markup = String.format(def.getPages(), hostName, currentHostsMarkup(), b, mCommand, moreLink, trashLink,
					emptyTrashLink);
		}
		return markup;
	}

	// FIXME duplicated code OutgoingNavigation
	private CharSequence currentHostsMarkup() {
		Markup.Browse def = mCache.getMarkup().getBrowse();
		StringBuilder b = new StringBuilder();
		for (Requested.RequestedHost each : mRequesteds) {
			String format = mCache.isBlocked(each.getName()) ? def.getCurrentsBlocked()
					: mCache.isPassed(each.getUrl()) ? def.getCurrentsPassed() : def.getCurrentsElement();
			b.append(String.format(format, each.getName(), each.getAge()));
		}
		if (b.length() == 0) {
			return "";
		}
		return String.format(def.getCurrentsTable(), b);
	}
}
