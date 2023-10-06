package de.ganskef.mocuishle.ui;

import java.util.Iterator;

import de.ganskef.mocuishle.IAction;
import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.ICache.PageMode;
import de.ganskef.mocuishle.Markup;
import de.ganskef.mocuishle.ui.SpecialUrl.Command;

public class OutgoingNavigation extends AbstractMarkupNavigation implements IAction {

	private final ICache mCache;

	private final Requested mRequesteds;

	private final Command mCommand;

	public OutgoingNavigation(ICache cache, Requested requesteds, Command command) {
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
	}

	@Override
	public String prepareAnswer() {
		String markup = null;
		Markup.Outgoing def = mCache.getMarkup().getOutgoing();
		ICache.PageMode pageMode = ICache.PageMode.forCommand(mCommand.toString());
		String switchTemplate = (pageMode == PageMode.MORE) ? def.getSwitchQuick() : def.getSwitchAll();
		StringBuilder b = new StringBuilder();
		Iterator<String> it = mCache.getOutgoing().uriIterator(pageMode);
		while (it.hasNext()) {
			String each = it.next();
			String format = mCache.getOutgoing().isDeleted(each) ? def.getDeleted() : def.getElement();
			b.append(String.format(format, each, mCommand));
		}
		String moreLink = switchTemplate;
		String trashLink = String.format(mCache.getOutgoing().hasTrash() ? def.getTrashFull() : def.getTrashEmpty(),
				mCommand, "");
		String commandName = mCommand.toString();
		String mode = commandName.substring(0, commandName.indexOf('-'));
		String page = mCache.getOutgoing().hasUris() ? def.getUrls() : def.getEmpty();
		markup = String.format(page, currentHostsMarkup(), b, mode, moreLink, trashLink);
		return markup;
	}

	// FIXME duplicated code BrowseNavigation
	private CharSequence currentHostsMarkup() {
		Markup.Browse def = mCache.getMarkup().getBrowse();
		StringBuilder b = new StringBuilder();
		for (Requested.RequestedHost each : mRequesteds) {
			String format = mCache.isBlocked(each.getName()) ? def.getCurrentsBlocked() : def.getCurrentsElement();
			b.append(String.format(format, each.getName(), each.getAge()));
		}
		if (b.length() == 0) {
			return "";
		}
		return String.format(def.getCurrentsTable(), b);
	}
}
