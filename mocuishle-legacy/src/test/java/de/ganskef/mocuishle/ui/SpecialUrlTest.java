package de.ganskef.mocuishle.ui;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import de.ganskef.mocuishle.IAction;
import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.ICache.State;
import de.ganskef.mocuishle.IFullTextIndex;
import de.ganskef.mocuishle.cache.History;

public class SpecialUrlTest {

	private ICache cache;

	private History history;

	private Requested requesteds;

	private IFullTextIndex index;

	@Before
	public void before() {
		index = mock(IFullTextIndex.class);
		history = mock(History.class);
		cache = mock(ICache.class);
		when(cache.getHistory()).thenReturn(history);
		when(cache.getFullTextIndex()).thenReturn(index);
		requesteds = new Requested();
	}

	@Test
	public void testWelcome() {
		String url = "/";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(WelcomeNavigation.class, action.getClass());
	}

	@Test
	public void testWelcomeOnInstall() {
		when(history.isWelcome()).thenReturn(true);
		String url = "/browse";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(WelcomeNavigation.class, action.getClass());
	}

	@Test
	public void testBrowse() {
		String url = "/browse";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(BrowseNavigation.class, action.getClass());
	}

	@Test
	public void testBrowseAddress() {
		String url = "/browse/address";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(BrowseNavigation.class, action.getClass());
	}

	@Test
	public void testWelcomeBrowseX() {
		String url = "/browseX";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(WelcomeNavigation.class, action.getClass());
	}

	@Test
	public void testMore() {
		String url = "/more";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(BrowseNavigation.class, action.getClass());
	}

	@Test
	public void testTrash() {
		String url = "/trash";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(BrowseNavigation.class, action.getClass());
	}

	@Test
	public void testBrowseOutgoing() {
		String url = "/browse-outgoing";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(OutgoingNavigation.class, action.getClass());
	}

	@Test
	public void testMoreOutgoing() {
		String url = "/more-outgoing";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(OutgoingNavigation.class, action.getClass());
	}

	@Test
	public void testSearch() {
		String url = "/browse?query=test&count=5&index=0";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(SearchNavigation.class, action.getClass());
	}

	@Test
	public void testMarkup() {
		String url = "/markup/xxx";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(ResourceNavigation.class, action.getClass());
	}

	@Test
	public void testFontsForBootstrap() {
		String url = "/fonts/xxx";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(ResourceNavigation.class, action.getClass());
	}

	@Test
	public void testBlockAddress() {
		String url = "/block/address";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/browse/", action.prepareAnswer());
		verify(cache).addBlocked("address");
	}

	@Test
	public void testUnblockAddress() {
		String url = "/unblock/address";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/browse/", action.prepareAnswer());
		verify(cache).removeBlocked("address");
	}

	@Test
	public void testDeletePath() {
		String url = "/delete/http/address/path";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/more/address", action.prepareAnswer());
		verify(cache).deleteBrowsePath("http", "address", "/path");
	}

	@Test
	public void testUndeletePath() {
		String url = "/undelete/http/address/path";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/more/address", action.prepareAnswer());
		verify(cache).undeleteBrowsePath("http", "address", "/path");
	}

	@Test
	public void testDeleteHost() {
		String url = "/delete/address";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/more/", action.prepareAnswer());
		verify(cache).deleteBrowsePath(null, "address", null);
	}

	@Test
	public void testUndeleteHost() {
		String url = "/undelete/address";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/more/", action.prepareAnswer());
		verify(cache).undeleteBrowsePath(null, "address", null);
	}

	@Test
	public void testOutgoingFetch() {
		String url = "/outgoing-fetch";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/more-outgoing/", action.prepareAnswer());
		verify(cache).fetchOutgoingUris();
	}

	@Test
	public void testOutgoingDelete() {
		String url = "/delete-outgoing/http://path";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/more-outgoing/", action.prepareAnswer());
		verify(cache).deleteOutgoing("http://path");
	}

	@Test
	public void testOutgoingUndelete() {
		String url = "/undelete-outgoing/http://path";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/more-outgoing/", action.prepareAnswer());
		verify(cache).undeleteOutgoing("http://path");
	}

	@Test
	public void testOutgoingEmptyTrash() {
		String url = "/outgoing-empty-trash";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/browse-outgoing/", action.prepareAnswer());
		verify(cache).emptyTrashOutgoing();
	}

	@Test
	public void testEmptyTrash() {
		String url = "/empty-trash/";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/browse/", action.prepareAnswer());
		verify(cache).emptyTrash();
	}

	@Test
	public void testEmptyTrashBrowse() {
		String url = "/empty-trash/browse/";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/browse/", action.prepareAnswer());
		verify(cache).emptyTrash();
	}

	@Test
	public void testEmptyTrashMore() {
		String url = "/empty-trash/more/";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/more/", action.prepareAnswer());
		verify(cache).emptyTrash();
	}

	@Test
	public void testEmptyTrashBrowseAddress() {
		String url = "/empty-trash/browse/address";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/browse/address", action.prepareAnswer());
		verify(cache).emptyTrash();
	}

	@Test
	public void testEmptyTrashMoreAddress() {
		// FIXME "/empty-trash/xxx/address"
		// FIXME "/empty-trash/xxx/address/xxx"
		String url = "/empty-trash/more/address";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/more/address", action.prepareAnswer());
		verify(cache).emptyTrash();
	}

	@Test
	public void testRebuildIndex() {
		String url = "/rebuild-index";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/browse/", action.prepareAnswer());
		verify(cache).getFullTextIndex();
		verify(index).replaceIndex();
	}

	@Test
	public void testModeCacheonly() {
		String url = "/cacheonly";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/mode-cacheonly", action.prepareAnswer());
		verify(cache).setState(State.CACHEONLY);
	}

	@Test
	public void testModeTethering() {
		String url = "/tethering";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/mode-tethering", action.prepareAnswer());
		verify(cache).setState(State.TETHERING);
	}

	@Test
	public void testModeAutomatic() {
		String url = "/automatic";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/mode-automatic", action.prepareAnswer());
		verify(cache).setState(State.AUTOMATIC);
	}

	@Test
	public void testModeFlatrate() {
		String url = "/flatrate";
		IAction action = SpecialUrl.createAnswer(cache, requesteds, url);
		assertEquals(RedirectAction.class, action.getClass());
		assertEquals("/mode-flatrate", action.prepareAnswer());
		verify(cache).setState(State.FLATRATE);
	}
}
