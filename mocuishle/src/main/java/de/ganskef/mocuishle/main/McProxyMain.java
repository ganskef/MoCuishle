package de.ganskef.mocuishle.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.IPlatform;
import de.ganskef.mocuishle.proxy.McProxy;

public class McProxyMain {

	private static final Logger log = LoggerFactory.getLogger(McProxyMain.class);

	/*
	 * This block prevents the Maven Shade plugin to remove the specified classes
	 */
	static {
		@SuppressWarnings("unused")
		Class<?>[] classes = new Class<?>[] { org.apache.log4j.xml.DOMConfigurator.class, //
				org.apache.log4j.AsyncAppender.class, //
				org.apache.log4j.FileAppender.class, //
				org.apache.log4j.ConsoleAppender.class, //
				org.apache.log4j.PatternLayout.class, //
				org.bouncycastle.jce.provider.BouncyCastleProvider.class, //
				org.bouncycastle.jcajce.provider.asymmetric.rsa.DigestSignatureSpi.class,
				org.bouncycastle.jcajce.provider.asymmetric.RSA.class, //
				org.bouncycastle.jcajce.provider.asymmetric.X509.class, //
				org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory.class,
				org.bouncycastle.jcajce.provider.digest.SHA1.class, //
		};
	}

	public static void main(String[] args) throws Exception {
		IPlatform platform = new JavaPlatform();
		// TODO move all the initialization into platform

		String libraryPath = platform.resolve("lib").toString();
		System.setProperty("sqlite4java.library.path", libraryPath);
		initResource(libraryPath, "libsqlite4java-linux-amd64.so");
		initResource(libraryPath, "libsqlite4java-osx.dylib");
		initResource(libraryPath, "sqlite4java-win32-x64.dll");
		initResource(libraryPath, "sqlite4java-win32-x86.dll");

		if (!Arrays.asList(args).contains("noinstall")) {
			log.info("Install Native Messaging to support browser extensions...");
			StartupScript.write(platform);
			Path startScriptPath = StartupScript.getStartScriptPath(platform);
			BrowserExtensionSupport.configAll(startScriptPath);
		}

		McProxy proxy = new McProxy(platform);
		try {
			proxy.start();
		} catch (Exception e) {
			log.error("Startup failure", e);
			proxy.stop();
			if (Thread.activeCount() > 1) {
				// stop is not working after initialization failed :-( ...
				// so write the logs and exit
				TimeUnit.MILLISECONDS.sleep(100);
				System.exit(1);
			}
		}
	}

	static void initResource(String targetDir, String name) throws IOException {
		File target = new File(targetDir, name);
		if (!target.exists()) {
			// log.info()
			new File(targetDir).mkdirs();
			try (InputStream is = McProxyMain.class.getResourceAsStream("/" + name);
					OutputStream os = new FileOutputStream(target)) {
				IOUtils.copy(is, os);
			}
		}
	}
}
