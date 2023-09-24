package de.ganskef.mocuishle2.util;

import static org.junit.Assert.assertEquals;

import de.ganskef.mocuishle.util.HttpDateUtil;
import org.junit.Assert;
import org.junit.Test;

public class HttpDateUtilTest {

  private static final String STANDARD_FORMAT = "Sun, 06 Nov 1994 08:49:37 GMT";
  private static final String OBSOLETE_FORMAT_1 = "Sun, 06-Nov-1994 08:49:37 GMT";
  private static final String OBSOLETE_FORMAT_2 = "Sun Nov 6 08:49:37 1994";

  private static final String COMPARABLE_FORMAT = "19941106-08:49:37";

  @Test
  public void testWrongInputReturnsNull() {
    String expected = null;
    Assert.assertEquals(expected, HttpDateUtil.comparable(null));
    assertEquals(expected, HttpDateUtil.comparable("xxx"));
    assertEquals(expected, HttpDateUtil.comparable("00.00.0000"));
    assertEquals(expected, HttpDateUtil.comparable(COMPARABLE_FORMAT));
    assertEquals(expected, HttpDateUtil.comparable(STANDARD_FORMAT + " "));
  }

  @Test
  public void testFormatNormalization() throws Exception {
    String expected = COMPARABLE_FORMAT;
    assertEquals(expected, HttpDateUtil.comparable(STANDARD_FORMAT));
    assertEquals(expected, HttpDateUtil.comparable(OBSOLETE_FORMAT_1));
    assertEquals(expected, HttpDateUtil.comparable(OBSOLETE_FORMAT_2));
  }
}
