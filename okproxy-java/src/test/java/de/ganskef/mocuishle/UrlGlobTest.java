package de.ganskef.mocuishle;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import okio.BufferedSink;
import okio.Okio;
import org.junit.jupiter.api.Test;

class UrlGlobTest {

  private static final String BASE_NAME = "target/" + UrlGlob.class.getSimpleName() + "-";

  @Test
  void testInitWithNullFileMissused() {
    final String fileName = null;
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(
            () -> {
              new UrlGlob(fileName);
            });
  }

  @Test
  void testInitWithMissedFileMissconfigured() {
    final String fileName = "./MISSED_FILE";
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(
            () -> {
              new UrlGlob(fileName);
            });
  }

  @Test
  void testInitWithDirectoryFileMissconfigured() {
    final String fileName = "./";
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(
            () -> {
              new UrlGlob(fileName);
            });
  }

  @Test
  void testEmpty() throws Exception {
    final String pathname = BASE_NAME + "empty.txt";
    try (BufferedSink sink = Okio.buffer(Okio.sink(new File(pathname)))) {
      sink.writeUtf8("");
    }
    UrlGlob empty = new UrlGlob(pathname);
    assertThat(empty.toString())
        .contains("addresses: 0", "patterns: 0", "lines: 0")
        .doesNotContain("localhost");
  }

  @Test
  void testLocalhost() throws Exception {
    final String pathname = BASE_NAME + "localhost.txt";
    try (BufferedSink sink = Okio.buffer(Okio.sink(new File(pathname)))) {
      sink.writeUtf8("localhost");
    }
    UrlGlob globs = new UrlGlob(pathname);
    assertThat(globs.toString())
        .contains("addresses: 1", "patterns: 0", "lines: 1")
        .doesNotContain("localhost");
  }

  @Test
  void testEndOfLine() throws Exception {
    final String pathname = BASE_NAME + "eol.txt";
    try (BufferedSink sink = Okio.buffer(Okio.sink(new File(pathname)))) {
      sink.writeUtf8("localhost").writeUtf8("\n");
    }
    UrlGlob globs = new UrlGlob(pathname);
    assertThat(globs.toString()) //
        .contains("addresses: 1", "patterns: 0", "lines: 1");
  }

  @Test
  void testDuplicate() throws Exception {
    final String pathname = BASE_NAME + "duplicate.txt";
    final String one = "localhost";
    try (BufferedSink sink = Okio.buffer(Okio.sink(new File(pathname)))) {
      sink.writeUtf8(one).writeUtf8("\n").writeUtf8(one);
    }
    UrlGlob globs = new UrlGlob(pathname);
    assertThat(globs.toString()) //
        .contains("addresses: 1", "patterns: 0", "lines: 1");
  }

  @Test
  void testIp4Address() throws Exception {
    final String pathname = BASE_NAME + "ip4.txt";
    try (BufferedSink sink = Okio.buffer(Okio.sink(new File(pathname)))) {
      sink.writeUtf8("127.0.0.1");
    }
    UrlGlob globs = new UrlGlob(pathname);
    assertThat(globs.toString()) //
        .contains("addresses: 1", "patterns: 0", "lines: 1");
  }

  @Test
  void testComment() throws Exception {
    final String pathname = BASE_NAME + "comment.txt";
    try (BufferedSink sink = Okio.buffer(Okio.sink(new File(pathname)))) {
      sink.writeUtf8("# comment");
    }
    UrlGlob globs = new UrlGlob(pathname);
    assertThat(globs.toString()) //
        .contains("addresses: 0", "patterns: 0", "lines: 1");
  }

  @Test
  void testCommentIgnoreFirstline() throws Exception {
    final String pathname = BASE_NAME + "initcomment.txt";
    try (BufferedSink sink = Okio.buffer(Okio.sink(new File(pathname)))) {
      sink.writeUtf8("# Globs ");
    }
    UrlGlob globs = new UrlGlob(pathname);
    assertThat(globs.toString()) //
        .contains("addresses: 0", "patterns: 0", "lines: 0");
  }

  @Test
  void testEmptyLine() throws Exception {
    final String pathname = BASE_NAME + "emptyline.txt";
    try (BufferedSink sink = Okio.buffer(Okio.sink(new File(pathname)))) {
      sink.writeUtf8("\n");
    }
    UrlGlob globs = new UrlGlob(pathname);
    assertThat(globs.toString()) //
        .contains("addresses: 0", "patterns: 0", "lines: 1");
  }

  @Test
  void testWidespaceLine() throws Exception {
    final String pathname = BASE_NAME + "widespaceline.txt";
    try (BufferedSink sink = Okio.buffer(Okio.sink(new File(pathname)))) {
      sink.writeUtf8(" \t \n");
    }
    UrlGlob globs = new UrlGlob(pathname);
    assertThat(globs.toString()) //
        .contains("addresses: 0", "patterns: 0", "lines: 1");
  }

  @Test
  void testPatternLine() throws Exception {
    final String pathname = BASE_NAME + "patternline.txt";
    try (BufferedSink sink = Okio.buffer(Okio.sink(new File(pathname)))) {
      sink.writeUtf8("*.googlevideo.com");
    }
    UrlGlob globs = new UrlGlob(pathname);
    assertThat(globs.toString()) //
        .contains("addresses: 0", "patterns: 1", "lines: 1");
    assertThat(globs.contains("s.1.googlevideo.com")).isTrue();
    assertThat(globs.contains("www.google.com")).isFalse();
  }

  @Test
  void testPatternDuplicate() throws Exception {
    final String pathname = BASE_NAME + "patternduplicate.txt";
    try (BufferedSink sink = Okio.buffer(Okio.sink(new File(pathname)))) {
      String one = "*.googlevideo.com";
      sink.writeUtf8(one).writeUtf8("\n").writeUtf8(one);
    }
    UrlGlob globs = new UrlGlob(pathname);
    assertThat(globs.toString()) //
        .contains("addresses: 0", "patterns: 1", "lines: 1");
  }

  @Test
  void testPatternLines() throws Exception {
    final String pathname = BASE_NAME + "patterns.txt";
    try (BufferedSink sink = Okio.buffer(Okio.sink(new File(pathname)))) {
      sink.writeUtf8("*.googlevideo.com").writeUtf8("\n");
      sink.writeUtf8("*.google.*").writeUtf8("\n");
      sink.writeUtf8("127.0.*");
    }
    UrlGlob globs = new UrlGlob(pathname);
    assertThat(globs.toString()) //
        .contains("addresses: 0", "patterns: 3", "lines: 3");
    assertThat(globs.contains("s.1.googlevideo.com")).isTrue();
    assertThat(globs.contains("maps.google.de")).isTrue();
    assertThat(globs.contains("www.google.com")).isTrue();
    assertThat(globs.contains("127.0.0.1")).isTrue();
    assertThat(globs.contains("xxx.googleapi.com")).isFalse();
  }

  @Test
  void testWrongLines() throws Exception {
    final String pathname = BASE_NAME + "patterns.txt";
    try (BufferedSink sink = Okio.buffer(Okio.sink(new File(pathname)))) {
      sink.writeUtf8("\\*.googlevideo.com").writeUtf8("\n").writeUtf8("?.google.*");
    }
    UrlGlob globs = new UrlGlob(pathname);
    assertThat(globs.toString()) //
        .contains("addresses: 0", "patterns: 0", "lines: 2");
  }

  @Test
  void testNullAddress() throws Exception {
    final String pathname = BASE_NAME + "emptyline.txt";
    try (BufferedSink sink = Okio.buffer(Okio.sink(new File(pathname)))) {}
    UrlGlob globs = new UrlGlob(pathname);
    assertThatNoException()
        .isThrownBy(
            () -> {
              globs.contains(null);
            });
  }
}
