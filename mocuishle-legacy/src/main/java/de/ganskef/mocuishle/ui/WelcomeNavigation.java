package de.ganskef.mocuishle.ui;

import de.ganskef.mocuishle.IAction;
import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.ICache.State;
import de.ganskef.mocuishle.Markup;
import de.ganskef.mocuishle.Markup.Welcome;

public class WelcomeNavigation extends AbstractMarkupNavigation implements IAction {

	protected final ICache mCache;
	protected final String mUrl;

	public WelcomeNavigation(ICache cache, String url) {
		if (cache == null) {
			throw new NullPointerException("cache");
		}
		if (url == null) {
			throw new NullPointerException("url");
		}
		this.mCache = cache;
		this.mUrl = url;
	}

	@Override
	public String prepareAnswer() {
		Welcome def = mCache.getMarkup().getWelcome();
		String format = def.getPage();
		Object truncated = Markup.truncated(mUrl, 0, 60);
		State cacheState = mCache.getState();
		String cacheonly = cacheState == State.CACHEONLY ? def.getCacheonly() : def.getCacheonlyDisabled();
		String tethering = cacheState == State.TETHERING ? def.getTethering() : def.getTetheringDisabled();
		String automatic = cacheState == State.AUTOMATIC ? def.getAutomatic() : def.getAutomaticDisabled();
		String flatrate = cacheState == State.FLATRATE ? def.getFlatrate() : def.getFlatrateDisabled();
		String markup = String.format(format, truncated, cacheonly, tethering, automatic, flatrate, "");
		return markup;
	}

	@Override
	public Status status() {
		if (mUrl.equals("/") || mUrl.equals("/mode-cacheonly") || mUrl.equals("/mode-tethering")
				|| mUrl.equals("/mode-automatic") || mUrl.equals("/mode-flatrate")) {
			return Status.OK;
		}
		return Status.BAD_REQUEST;
	}
}
