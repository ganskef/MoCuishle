package de.ganskef.mocuishle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExtensionAssemblyTest {

  private String targetClasses;

  public static String list(Path path) throws IOException {
    StringBuilder b = new StringBuilder();
    List<Path> paths = new ArrayList<>();
    Path absolutePath = path.toAbsolutePath();
    list(absolutePath, paths);
    Collections.sort(paths);
    int offset = absolutePath.toString().length();
    paths.forEach(
        each ->
            b.append(
                    each.toString() //
                        .substring(offset) //
                        .replaceAll("^[\\/]?(.*?)[\\/]?$", "$1") //
                    )
                .append('\n'));
    return b.toString();
  }

  public static void list(Path path, List<Path> paths) {
    paths.add(path);
    if (Files.isDirectory(path)) {
      try {
        Files.newDirectoryStream(path).forEach(each -> list(each, paths));
      } catch (IOException e) {
        new IllegalStateException(e);
      }
    }
  }

  @BeforeEach
  public void before() throws IOException {
    targetClasses = list(Paths.get("target/classes"));
  }

  @Test
  public void testMajorFilesInClasses() throws Exception {
    assertTrue(targetClasses.contains("\nLICENSE.txt\n"));
    assertTrue(targetClasses.contains("\nmanifest.json\n"));
    assertTrue(targetClasses.contains("\nmocuishle.js\n"));
  }

  @Test
  public void testChromeAssemblyEqualsToClasses() throws IOException {
    Iterator<Path> it =
        Files.newDirectoryStream(Paths.get("target"), "mocuishle-browser-*-chrome.zip").iterator();
    assertTrue(it.hasNext(), "Chrome extension exists");

    URI uri = URI.create("jar:file:" + it.next().toAbsolutePath());
    try (FileSystem zipfs = FileSystems.newFileSystem(uri, Collections.emptyMap()); ) {
      assertEquals(targetClasses, list(zipfs.getPath("/")), "Assembly compared to classes");
    }
  }

  @Test
  public void testFirefoxAssemblyEqualsToClasses() throws IOException {
    Iterator<Path> it =
        Files.newDirectoryStream(Paths.get("target"), "mocuishle-browser-*-firefox.zip").iterator();
    assertTrue(it.hasNext(), "Firefox extension exists");

    URI uri = URI.create("jar:file:" + it.next().toAbsolutePath());
    try (FileSystem zipfs = FileSystems.newFileSystem(uri, Collections.emptyMap()); ) {
      assertEquals(targetClasses, list(zipfs.getPath("/")), "Assembly compared to classes");
    }
  }

  @Test
  public void testChromeResourcesFiltering() throws IOException {
    Path manifest = Paths.get("target/classes/manifest.json");
    for (Iterator<String> it = Files.lines(manifest, StandardCharsets.UTF_8).iterator();
        it.hasNext(); ) {
      assertTrue(!it.next().contains("${"), "Every property is replaced by filtering");
    }
  }

  @Test
  public void testChromeAssemblyFiltering() throws IOException {
    URI assembly =
        URI.create(
            "jar:file:"
                + Files.newDirectoryStream(Paths.get("target"), "mocuishle-browser-*-chrome.zip")
                    .iterator()
                    .next()
                    .toAbsolutePath());
    try (FileSystem zipfs = FileSystems.newFileSystem(assembly, Collections.emptyMap()); ) {
      Path manifest = zipfs.getPath("/manifest.json");
      for (Iterator<String> it = Files.lines(manifest, StandardCharsets.UTF_8).iterator();
          it.hasNext(); ) {
        assertTrue(!it.next().contains("${"), "Every property is replaced by filtering");
      }
    }
  }

  @Test
  public void testFirefoxAssemblyFiltering() throws IOException {
    URI assembly =
        URI.create(
            "jar:file:"
                + Files.newDirectoryStream(Paths.get("target"), "mocuishle-browser-*-firefox.zip")
                    .iterator()
                    .next()
                    .toAbsolutePath());
    try (FileSystem zipfs = FileSystems.newFileSystem(assembly, Collections.emptyMap()); ) {
      Path manifest = zipfs.getPath("/manifest.json");
      for (Iterator<String> it = Files.lines(manifest, StandardCharsets.UTF_8).iterator();
          it.hasNext(); ) {
        assertTrue(!it.next().contains("${"), "Every property is replaced by filtering");
      }
    }
  }
}
