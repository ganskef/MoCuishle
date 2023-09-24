package de.ganskef.mocuishle;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okio.BufferedSource;
import okio.Okio;

public class UrlGlob {
  private static final Logger LOG = Logger.getLogger(UrlGlob.class.toString());

  private static final Pattern INPUT_LINE_PATTERN =
      Pattern.compile("(#.*)|([\\w.-]+)|([*\\w.-]+)|.*");

  private final List<String> lines;

  private final Set<String> addresses;

  private final Pattern globsPattern;

  private final int patternsSize;

  public UrlGlob(String pathname) {
    Objects.requireNonNull(pathname);
    this.lines = new ArrayList<>();
    this.addresses = new HashSet<>();
    String regex;
    try {
      Set<String> patterns = new HashSet<>();
      File file = new File(pathname);
      BufferedSource source = Okio.buffer(Okio.source(file));
      for (String line; (line = source.readUtf8Line()) != null; ) {
        Matcher m = INPUT_LINE_PATTERN.matcher(line);
        if (m.matches()) {
          String comment = m.group(1);
          String address = m.group(2);
          String glob = m.group(3);
          if (comment != null) {
            if (!comment.startsWith("# Globs ")) {
              LOG.info(() -> String.format("Comment: %s <- %s", comment, file));
              lines.add(comment);
            }
          } else if (address != null) {
            if (addresses.add(address)) {
              lines.add(address);
            }
          } else if (glob != null) {
            String pattern = glob.replaceAll("\\.", "\\\\.").replaceAll("\\*", ".*?");
            if (patterns.add(pattern)) {
              LOG.info(() -> String.format("Pattern: %s -> %s <- %s", glob, pattern, file));
              lines.add(glob);
            }
          } else if (line.trim().length() == 0) {
            LOG.fine(() -> String.format("Line: # <- %s", file));
            lines.add("#");
          } else {
            LOG.warning(String.format("Wrong: '%s' <- %s", line, file));
            lines.add("# wrong: " + line);
          }
        }
      }
      patternsSize = patterns.size();
      if (patternsSize == 1) {
        regex = patterns.iterator().next();
      } else if (patternsSize > 1) {
        StringBuilder b = new StringBuilder();
        b.append("(?:");
        String sep = "";
        for (String each : patterns) {
          b.append(sep).append(each);
          sep = "|";
        }
        b.append(")");
        regex = b.toString();
      } else {
        regex = "";
      }
      LOG.info(
          () ->
              String.format(
                  "Init: addresses: %s, patterns: %s, lines: %s <- %s",
                  addresses.size(), patternsSize, lines.size(), file));
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage());
    }
    this.globsPattern = Pattern.compile(regex);
  }

  public boolean contains(String hostname) {
    if (hostname == null) {
      return false;
    }
    return (addresses.contains(hostname) || globsPattern.matcher(hostname).matches());
  }

  // public void add(String line) {
  // // TODO
  // }

  // public void remove(String line) {
  // // TODO
  // }

  // public void write() throws IOException {
  // // TODO
  // }

  @Override
  public String toString() {
    return String.format(
        "%s{addresses: %s, patterns: %s, lines: %s}",
        getClass().getSimpleName(), addresses.size(), patternsSize, lines.size());
  }
}
