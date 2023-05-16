package de.ganskef.mocuishle.cache;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UrlConfig {

	private static final Logger log = LoggerFactory.getLogger(UrlConfig.class);

	private static final Pattern STAR_PATTERN = Pattern.compile("\\*");

	private static final Pattern DOT_PATTERN = Pattern.compile("\\.");

	private static final Pattern URL_PATTERN = Pattern
			.compile("\\s*((?:http|https|\\*)://)?([\\w-\\.:\\*]+)(/[\\w-\\.\\*&;+%?/=]*)?\\s*");

	private static final Pattern ADDRESS_PATTERN = Pattern
			.compile("\\s*(?:(?:https?|\\*)://)?([\\*\\w\\.:-]+)(?:/.*)?\\s*", Pattern.CASE_INSENSITIVE);

	private Pattern mPattern;

	private final File mConfigFile;

	public UrlConfig(File configFile, String fallbackRegExp) {
		mConfigFile = configFile;
		if (configFile.isFile() && configFile.canRead()) {
			try {
				List<String> lines = FileUtils.readLines(configFile, McElement.DEFAULT_ENCODING);
				initPattern(lines);
			} catch (IOException e) {
				log.error("Can't read config file " + configFile.getName(), e);
			}
		}
		if (mPattern == null) {
			mPattern = Pattern.compile(fallbackRegExp, Pattern.CASE_INSENSITIVE);
			log.info("{} fallback RegExp {}", configFile, fallbackRegExp);
		}
	}

	private void initPattern(List<String> lines) {
		Set<String> distincts = new HashSet<String>();
		StringBuilder b = new StringBuilder();
		for (String each : lines) {
			Matcher m = URL_PATTERN.matcher(each);
			if (m.matches()) {
				String scheme = replaceStar(m.group(1) == null ? "*://" : m.group(1), "https?");
				String host = replaceStar(m.group(2), "[\\\\w\\\\.:-]+");
				String path = replaceStar(m.group(3) == null ? "(?:/*|)" : m.group(3), "[\\\\w\\\\.&;+%\\\\?/=_-]*");
				String config = scheme + host + path;
				appendPattern(config, distincts, b);
			} else {
				if (each.length() != 0 && !each.startsWith("#")) {
					try {
						Pattern.compile(each);
						appendPattern(each, distincts, b);
					} catch (PatternSyntaxException e) {
						log.warn("Can't compile {} {}", each, e.getMessage());
					}
				}
			}
		}
		if (b.length() != 0) {
			b.append(")");
			try {
				mPattern = Pattern.compile(b.toString());
				log.debug("{}", b);
			} catch (PatternSyntaxException e) {
				log.error("Can't compile " + mConfigFile.getName(), e);
			}
		}
	}

	private void appendPattern(String pattern, Set<String> distincts, StringBuilder b) {
		if (distincts.add(pattern)) {
			if (b.length() == 0) {
				b.append("(?:");
			} else {
				b.append("|");
			}
			b.append(pattern);
			log.debug("{} RegExp {}", mConfigFile, pattern);
		}
	}

	private String replaceStar(String urlConfigLine, String replacement) {
		String result = DOT_PATTERN.matcher(urlConfigLine).replaceAll("\\\\.");
		result = STAR_PATTERN.matcher(result).replaceAll(replacement);
		return result;
	}

	public boolean matches(String url) {
		if (url == null) {
			return false;
		}
		Matcher m = mPattern.matcher(url);
		return m.matches();
	}

	public String add(String input) {
		if (input != null && input.trim().length() != 0) {
			String definition = input.trim();
			List<String> lines = readConfiguration();
			if (!lines.contains(definition)) {
				lines.add(0, input);
				try {
					FileUtils.writeLines(mConfigFile, lines);
				} catch (IOException e) {
					log.error("Couldn't update url configuration " + mConfigFile, e);
				}
				initPattern(lines);
			}
			Matcher m = ADDRESS_PATTERN.matcher(definition);
			if (m.matches()) {
				return m.group(1);
			}
		}
		return null;
	}

	private List<String> readConfiguration() {
		if (!mConfigFile.isDirectory() && mConfigFile.canRead()) {
			try {
				return FileUtils.readLines(mConfigFile);
			} catch (IOException e) {
				log.info("Can't read url configuration " + mConfigFile, e);
			}
		}
		return new ArrayList<String>();
	}

	public void remove(String definition) {
		List<String> lines = readConfiguration();
		boolean modified = false;
		String hostName = definition.trim();
		Iterator<String> it = lines.iterator();
		while (it.hasNext()) {
			String each = it.next();
			Matcher m = ADDRESS_PATTERN.matcher(each);
			if (m.matches() && !m.group(1).equals("*")) {
				if (hostName.matches(m.group(1).replaceAll("\\*", ".*"))) {
					it.remove();
					modified = true;
				}
			}
		}
		if (modified) {
			try {
				FileUtils.writeLines(mConfigFile, lines);
				initPattern(lines);
			} catch (IOException e) {
				log.error("Couldn't update url configuration " + mConfigFile, e);
			}
		}
	}
}
