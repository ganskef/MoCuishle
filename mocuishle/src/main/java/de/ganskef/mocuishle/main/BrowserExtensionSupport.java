package de.ganskef.mocuishle.main;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.IPlatform;

public enum BrowserExtensionSupport {
	FIREFOX_UNIX {
		protected Path browserUserConfigDir() {
			return USER_HOME.resolve(".mozilla");
		}

		@Override
		protected String nativeMessagingHostsFolderName() {
			return "native-messaging-hosts";
		}

		@Override
		protected CharSequence manifest(Path startScriptPath) {
			return mozillaManifest(startScriptPath);
		}
	},

	FIREFOX_APPLE {
		protected Path browserUserConfigDir() {
			return APPLE_DIR.resolve("Mozilla");
		}

		@Override
		protected CharSequence manifest(Path startScriptPath) {
			return mozillaManifest(startScriptPath);
		}
	},

	FIREFOX_WINDOWS {
		@Override
		protected Path browserUserConfigDir() {
			return USER_HOME.resolve(IPlatform.APPLICATION_NAME);
		}

		@Override
		protected String nativeMessagingHostsFolderName() {
			return "native-messaging-firefox";
		}

		@Override
		protected CharSequence manifest(Path startScriptPath) {
			return mozillaManifest(startScriptPath);
		}

		@Override
		public void config(Path startScriptPath) {
			if (File.separatorChar == '\\') {
				super.config(startScriptPath);
				configRegistry("Mozilla");
			}
		}
	},

	CHROME_UNIX {
		protected Path browserUserConfigDir() {
			return CONFIG_DIR.resolve("google-chrome");
		}

		@Override
		protected CharSequence manifest(Path startScriptPath) {
			return chromiumManifest(startScriptPath);
		}
	},

	CHROME_APPLE {
		protected Path browserUserConfigDir() {
			return APPLE_DIR.resolve("Google").resolve("Chrome");
		}

		@Override
		protected CharSequence manifest(Path startScriptPath) {
			return chromiumManifest(startScriptPath);
		}
	},

	CHROME_WINDOWS {
		@Override
		protected Path browserUserConfigDir() {
			return USER_HOME.resolve(IPlatform.APPLICATION_NAME);
		}

		@Override
		protected String nativeMessagingHostsFolderName() {
			return "native-messaging-chromium";
		}

		@Override
		protected CharSequence manifest(Path startScriptPath) {
			return chromiumManifest(startScriptPath);
		}

		@Override
		public void config(Path startScriptPath) {
			if (File.separatorChar == '\\') {
				super.config(startScriptPath);
				configRegistry("Google\\Chrome");
			}
		}
	},

	CHROMIUM_UNIX {
		protected Path browserUserConfigDir() {
			return CONFIG_DIR.resolve("chromium");
		}

		@Override
		protected CharSequence manifest(Path startScriptPath) {
			return chromiumManifest(startScriptPath);
		}
	},

	CHROMIUM_APPLE {
		protected Path browserUserConfigDir() {
			return APPLE_DIR.resolve("Chromium");
		}

		@Override
		protected CharSequence manifest(Path startScriptPath) {
			return chromiumManifest(startScriptPath);
		}
	};

	private static final Logger log = LoggerFactory.getLogger(BrowserExtensionSupport.class);

	private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));
	private static final Path APPLE_DIR = USER_HOME.resolve("Library").resolve("Application Support");
	private static final Path CONFIG_DIR = USER_HOME.resolve(".config");

	private static final String MANIFEST_JSON_FORMAT = "{%n" //
			+ "  \"name\": \"de.ganskef.mocuishle\",%n" //
			+ "  \"description\": \"A caching proxy for offline use.\",%n" //
			+ "  \"path\": \"%s\",%n" //
			+ "  \"type\": \"stdio\",%n" //
			+ "  \"%s\": [ \"%s\" ]%n" //
			+ "}%n";

	private static final String MANIFEST_FILE_NAME = "de.ganskef.mocuishle.json";

	private static final String MOZILLA_EXTENSION = "mocuishle@jetpack";
	private static final String CHROMIUM_EXTENSION = "chrome-extension://ajccdogbepemoknjbdigfdnjlinpbedj/";

	private static CharSequence chromiumManifest(Path startScriptPath) {
		return String.format(MANIFEST_JSON_FORMAT, startScriptPath, "allowed_origins", CHROMIUM_EXTENSION);
	}

	private static CharSequence mozillaManifest(Path startScriptPath) {
		return String.format(MANIFEST_JSON_FORMAT, startScriptPath, "allowed_extensions", MOZILLA_EXTENSION);
	}

	protected abstract Path browserUserConfigDir();

	protected abstract CharSequence manifest(Path startScriptPath);

	protected String nativeMessagingHostsFolderName() {
		return "NativeMessagingHosts";
	}

	public void config(Path startScriptPath) {
		Path configDir = browserUserConfigDir();
		if (Files.exists(configDir)) {
			log.info(String.valueOf(this));
			Path dir = configDir.resolve(nativeMessagingHostsFolderName());
			try {
				Files.createDirectories(dir);
				List<CharSequence> content = new ArrayList<CharSequence>();
				content.add(manifest(startScriptPath.toAbsolutePath()));
				Files.write(dir.resolve(MANIFEST_FILE_NAME), //
						content, //
						StandardOpenOption.CREATE, //
						StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				log.info("Config Native Messaging fails: " + e);
			}
		}
	}

	public static void configAll(Path startScriptPath) {
		for (BrowserExtensionSupport each : values()) {
			each.config(startScriptPath);
		}
	}

	class LogOutputStream extends OutputStream {

		private StringBuilder b = new StringBuilder();

		@Override
		public void write(int i) throws IOException {
			char c = (char) i;
			if (c == '\r' || c == '\n') {
				if (b.length() > 0) {
					log.info(b.toString());
					b.setLength(0);
				}
			} else {
				b.append(c);
			}
		}
	}

	public void configRegistry(String application) {
		try (OutputStream os = new LogOutputStream()) {
			Path manifestPath = USER_HOME.resolve(IPlatform.APPLICATION_NAME).resolve(MANIFEST_FILE_NAME)
					.toAbsolutePath();
			Process p = new ProcessBuilder("reg.exe", "add",
					String.format("HKEY_CURRENT_USER\\SOFTWARE\\%s\\NativeMessagingHosts", application), //
					"/v", "de.ganskef.mocuishle", "/t", "REG_SZ", "/d", manifestPath.toString(), "/f").start();
			IOUtils.copy(p.getInputStream(), os);
			IOUtils.copy(p.getErrorStream(), os);
			// p.getInputStream().transferTo(os);
			// p.getErrorStream().transferTo(os);
		} catch (IOException e) {
			log.info("Register Native Messaging fails: " + e);
		}
	}
}
