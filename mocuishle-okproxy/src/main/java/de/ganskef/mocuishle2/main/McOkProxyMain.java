package de.ganskef.mocuishle2.main;

import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.IPlatform;
import de.ganskef.mocuishle.Markup;
import de.ganskef.mocuishle.cache.McCache;
import de.ganskef.mocuishle.main.BrowserExtensionSupport;
import de.ganskef.mocuishle.main.JavaPlatform;
import de.ganskef.mocuishle.main.StartupScript;
import de.ganskef.mocuishle2.proxy.McOkProxy;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class McOkProxyMain {

  static {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  private static final Logger log = LoggerFactory.getLogger(McOkProxyMain.class);

  /*
   * This block prevents the Maven Shade plugin to remove the specified classes
   */
  static {
    @SuppressWarnings("unused")
    Class<?>[] classes = new Class<?>[] {};
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

    ICache cache = new McCache(platform);
    McOkProxy proxy = new McOkProxy(platform, cache);
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
      try (InputStream is = Markup.class.getResourceAsStream("/" + name);
          OutputStream os = Files.newOutputStream(target.toPath())) {
        IOUtils.copy(is, os);
      }
    }
  }
}
