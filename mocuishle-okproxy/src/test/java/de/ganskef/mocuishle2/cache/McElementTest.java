package de.ganskef.mocuishle2.cache;

import static org.junit.Assert.assertEquals;

import de.ganskef.mocuishle.IPlatform;
import de.ganskef.mocuishle.cache.McCache;
import de.ganskef.mocuishle.cache.McElement;
import de.ganskef.mocuishle2.McTestProxy;
import org.junit.Before;
import org.junit.Test;

public class McElementTest {

  private IPlatform platform;

  private McCache cache;

  private McElement rootElement;

  private String root;

  @Before
  public void before() {
    platform = new McTestProxy();
    cache = new McCache(platform);
    root = "http://martinfowler.com/";
    rootElement = cache.createElement(root);
  }

  @Test
  public void testRootUriWithSlash() {
    String href = "http://martinfowler.com";
    assertEquals(root, rootElement.toUrl(href));
  }

  @Test
  public void testHrefWithSpace() {
    String href = "API design.html";
    String url = root + "API%20design.html";
    assertEquals(url, rootElement.toUrl(href));
  }

  @Test
  public void testHrefWithAmpersAnd() {
    String href = "http://www.freebsd.org/cgi/man.cgi?query=sysinstall&amp;sektion=8";
    String url = "http://www.freebsd.org/cgi/man.cgi?query=sysinstall&sektion=8";
    assertEquals(url, rootElement.toUrl(href));
  }

  @Test
  public void testHttpsSchema() {
    McElement httpsElement = cache.createElement("https://www.archlinux.org/");
    String href = "/packages/";
    String url = "https://www.archlinux.org/packages/";
    assertEquals(url, httpsElement.toUrl(href));
  }
}
