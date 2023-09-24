package de.ganskef.mocuishle2;

import static org.junit.Assert.*;

import de.ganskef.mocuishle.Markup;
import org.junit.Assert;
import org.junit.Test;

public class MarkupTest {

  @Test
  public void testUmlaut() {
    String expected = "http%3A%2F%2Flocalhost%2F%C3%B6";
    Assert.assertEquals(expected, Markup.urlEncoded("http://localhost/ö").toString());
  }

  @Test
  public void testUnmodified() {
    String expected = "-_.19aZ";
    assertEquals(expected, Markup.urlEncoded(expected).toString());
  }
}
