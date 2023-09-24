package de.ganskef.mocuishle2.main;

import static org.junit.Assert.*;

import java.io.StringWriter;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class BrowserExtensionSupportTest {

  @Test
  public void test() throws Exception {
    /* Use he find command here to run the test on Unix and Windows. */
    Process p =
        new ProcessBuilder("echo")
            /*
             * INHERIT will send the output to the current Java process:
             *
             * .redirectOutput(Redirect.INHERIT).redirectError(Redirect.INHERIT)
             */
            .start();

    try (StringWriter err = new StringWriter()) {
      IOUtils.copy(p.getErrorStream(), err);
      assertEquals("", err.toString());
    }
    try (StringWriter out = new StringWriter()) {
      IOUtils.copy(p.getInputStream(), out);
      assertNotEquals("", out.toString());
    }
  }
}
