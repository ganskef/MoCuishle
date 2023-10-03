package de.ganskef.mocuishle.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.IAction;
import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.ICache.State;
import de.ganskef.mocuishle.Markup;

public class SpecialUrl {

	private static final Logger log = LoggerFactory.getLogger(SpecialUrl.class);

	private static final Pattern SPECIAL_URL_PATTERN = Pattern.compile(Command.toRegExp());

	public enum Command {
		CACHEONLY, AUTOMATIC, TETHERING, FLATRATE, BROWSE, MORE, BLOCK, UNBLOCK, //
		DELETE, UNDELETE, TRASH, EMPTY_TRASH, REBUILD_INDEX, MARKUP, FONTS, //
		BROWSE_OUTGOING, MORE_OUTGOING, TRASH_OUTGOING, DELETE_OUTGOING, //
		UNDELETE_OUTGOING, OUTGOING_FETCH, OUTGOING_EMPTY_TRASH, EXPORT, //
		PASS, UNPASS, OUTGOING_ITERATE, VALIDATE_CACHE_ITERATE;

		private final String mUrl;

		private Command() {
			this.mUrl = name().toLowerCase().replace('_', '-');
		}

		public static final String toRegExp() {
			StringBuilder b = new StringBuilder();
			b.append("^/(");
			String separator = "";
			for (Command each : values()) {
				b.append(separator).append(each.mUrl);
				separator = "|";
			}
			b.append(")(?:/(.*?))?(\\?.*)?$");
			return b.toString();
		}

		public static Command forSpecialUrl(String url) {
			for (Command each : values()) {
				if (each.mUrl.equals(url)) {
					return each;
				}
			}
			return null;
		}

		@Override
		public String toString() {
			return mUrl;
		}
	}

	private static final Pattern HOST_FROM_PATH_PATTERN = Pattern.compile("(?:([^/]+)/)?([^/]+)(/.*)?");

	static class CommandPath {
		private final String scheme;
		private final String hostName;
		private final String path;

		public CommandPath(String path) {
			Matcher m = HOST_FROM_PATH_PATTERN.matcher(path);
			if (m.matches()) {
				this.scheme = m.group(1);
				this.hostName = m.group(2);
				this.path = m.group(3);
			} else {
				this.scheme = "";
				this.hostName = "";
				this.path = "";
			}
		}
	}

	private static final Pattern HOST_NAME_PATTERN = Pattern.compile("https?/([^/]+).*", Pattern.CASE_INSENSITIVE);

	static class HostPath {
		private final String hostName;

		public HostPath(String path) {
			Matcher m = HOST_NAME_PATTERN.matcher(path);
			if (m.matches()) {
				this.hostName = m.group(1);
			} else {
				this.hostName = "";
			}
		}
	}

	private static final Pattern SEARCH_PATTERN = Pattern.compile("^\\?.*?query=([^&]*)&count=(\\d+)&index=(\\d+)");

	public static IAction createAnswer(ICache cache, Requested requesteds, String url) {
		Matcher m = SPECIAL_URL_PATTERN.matcher(url);
		if (m.matches()) {
			Command command = Command.forSpecialUrl(m.group(1));
			String path = (m.group(2) == null) ? "" : m.group(2);
			String query = (m.group(3) == null) ? "" : m.group(3);

			if (command == Command.EXPORT) {
				String absoluteFile = cache.export(path.substring(0, path.length() - 4));
				return new ExportAction(absoluteFile);
			}
			if (command == Command.BLOCK) {
				requesteds.refresh(path);
				cache.addBlocked(path);
				return new RedirectAction("/browse/");
			}
			if (command == Command.UNBLOCK) {
				requesteds.refresh(path);
				cache.removeBlocked(path);
				return new RedirectAction("/browse/");
			}
			if (command == Command.PASS) {
				requesteds.refresh(path);
				cache.addPassed(path);
				return new RedirectAction("/browse/");
			}
			if (command == Command.UNPASS) {
				requesteds.refresh(path);
				cache.removePassed(path);
				return new RedirectAction("/browse/");
			}
			if (command == Command.DELETE) {
				CommandPath p = new CommandPath(path + query);
				cache.deleteBrowsePath(p.scheme, p.hostName, p.path);
				HostPath hp = new HostPath(path);
				return new RedirectAction("/more/" + hp.hostName);
			}
			if (command == Command.UNDELETE) {
				CommandPath p = new CommandPath(path + query);
				cache.undeleteBrowsePath(p.scheme, p.hostName, p.path);
				HostPath hp = new HostPath(path);
				return new RedirectAction("/more/" + hp.hostName);
			}
			if (command == Command.OUTGOING_ITERATE) {
				return new OutgoingIterateNavigation(cache);
			}
			if (command == Command.VALIDATE_CACHE_ITERATE) {
				return new ValidateNavigation(cache);
			}
			if (command == Command.OUTGOING_FETCH) {
				// FIXME replaced
				cache.fetchOutgoingUris();
				return new RedirectAction("/more-outgoing/");
			}
			if (command == Command.DELETE_OUTGOING) {
				cache.deleteOutgoing(path + query);
				return new RedirectAction("/more-outgoing/");
			}
			if (command == Command.UNDELETE_OUTGOING) {
				cache.undeleteOutgoing(path + query);
				return new RedirectAction("/more-outgoing/");
			}
			if (command == Command.OUTGOING_EMPTY_TRASH) {
				cache.emptyTrashOutgoing();
				return new RedirectAction("/browse-outgoing/");
			}
			if (command == Command.EMPTY_TRASH) {
				cache.emptyTrash();
				return new RedirectAction(path == null || path.length() == 0 ? "/browse/" : "/" + path);
			}
			if (command == Command.REBUILD_INDEX) {
				cache.getFullTextIndex().replaceIndex();
				return new RedirectAction(path == null || path.length() == 0 ? "/browse/" : "/" + path);
			}
			if (command == Command.CACHEONLY || command == Command.AUTOMATIC || command == Command.TETHERING
					|| command == Command.FLATRATE) {
				cache.setState(State.valueOf(command.name()));
				return new RedirectAction("/mode-" + command);
			}
			if (query != null) {
				Matcher mq = SEARCH_PATTERN.matcher(query);
				if (mq.matches()) {
					String search = Markup.xmlDecoded(Markup.urlDecoded(mq.group(1))).toString();
					int count = Integer.valueOf(mq.group(2));
					int index = Integer.valueOf(mq.group(3));
					return new SearchNavigation(cache, command, path, search, index, count);
				}
			}
			if (command == Command.MARKUP || command == Command.FONTS) {
				return new ResourceNavigation(cache, path);
			}
			if (command == Command.BROWSE_OUTGOING || command == Command.MORE_OUTGOING) {
				log.info(">>> {}", command);
				return new OutgoingNavigation(cache, requesteds, command);
			}
			if (command == Command.BROWSE && cache.getHistory().isWelcome()) {
				return new WelcomeNavigation(cache, url);
			}
			if (command == Command.BROWSE || command == Command.MORE || command == Command.TRASH) {
				return new BrowseNavigation(cache, requesteds, command, path);
			}
			log.warn("Command not implemented {}", command);
		}
		return new WelcomeNavigation(cache, url);
	}
}
