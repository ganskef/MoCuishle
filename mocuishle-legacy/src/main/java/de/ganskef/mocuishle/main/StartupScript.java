package de.ganskef.mocuishle.main;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.IPlatform;

public enum StartupScript {
	UNIX {
		@Override
		protected String getFormat() {
			return "#!/bin/bash -e%n" //
					+ "exec /usr/bin/java -jar \"%s\" noinstall 2>&1>/dev/null%n";
		}

		@Override
		protected String getFileExtension() {
			return ".sh";
		}

		@Override
		protected void setPermissions(Path path) throws IOException {
			Set<PosixFilePermission> executable = PosixFilePermissions.fromString("rwxr-xr-x");
			Files.setPosixFilePermissions(path, executable);
		}
	},
	WINDOWS {
		@Override
		protected String getFormat() {
			return "start /MIN \"Mo Cuishle\" java.exe -jar \"%s\" noinstall%n";
		}

		@Override
		protected String getFileExtension() {
			return ".bat";
		}

		@Override
		protected void setPermissions(Path ignored) {
		}
	};

	private static final Logger log = LoggerFactory.getLogger(StartupScript.class);

	protected abstract String getFormat();

	protected abstract String getFileExtension();

	protected abstract void setPermissions(Path path) throws IOException;

	public static void write(IPlatform platform) {
		try {
			StartupScript script = getInstance();
			Path path = getStartScriptPath(platform, script);
			List<CharSequence> content = new ArrayList<CharSequence>();
			Path jarPath = getJarPath();
			if (jarPath != null) {
				content.add(String.format(script.getFormat(), jarPath));
				Files.write(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				script.setPermissions(path);
			}
		} catch (IOException | URISyntaxException e) {
			log.info("Init startup script fails: " + e);
		}
	}

	private static Path getJarPath() throws URISyntaxException, IOException {
		Path path = new File(StartupScript.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath();
		if (path.toString().endsWith(".jar")) {
			return path;
		}
		Iterator<Path> it = Files.list(path.getParent()).iterator();
		while (it.hasNext()) {
			Path each = it.next();
			if (each.toString().endsWith("-shaded.jar")) {
				return each;
			}
		}
		return null;
	}

	private static Path getStartScriptPath(IPlatform platform, StartupScript script) {
		return platform.resolve("mocuishle" + script.getFileExtension());
	}

	private static StartupScript getInstance() {
		return File.separatorChar == '/' ? UNIX : WINDOWS;
	}

	public static Path getStartScriptPath(IPlatform platform) {
		return getStartScriptPath(platform, getInstance());
	}
}
