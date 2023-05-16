package de.ganskef.mocuishle.modify;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import de.ganskef.mocuishle.ICacheableModify;
import de.ganskef.mocuishle.IPlatform;
import de.ganskef.mocuishle.Markup;
import de.ganskef.mocuishle.McTestProxy;
import de.ganskef.mocuishle.main.JavaPlatform;

public class HtmlModifierTest {

	static class ModifyCacheableAdapter implements ICacheableModify {

		private String url;

		public String toUrl(String href) {
			return href;
		}

		public boolean isRequested(String href) {
			url = toUrl(href);
			return ("requested".equals(href));
		}

		public boolean isCached(String href) {
			url = toUrl(href);
			return ("cached".equals(href));
		}

		public void recordBrowse(String title) {
			// TODO Auto-generated method stub

		}

		public String getLocalUrl() {
			return "http://127.0.0.1:" + new JavaPlatform().getProxyPort();
		}

		public String getHostName() {
			return "www.freebsd.org";
		}

		public boolean isOkStatus() {
			return true;
		}

		@Override
		public Markup getMarkup() {
			return new Markup(getPlatform());
		}

		IPlatform getPlatform() {
			return new McTestProxy();
		}

		@Override
		public String getUrl() {
			return url;
		}

		@Override
		public long lastModified() {
			return System.currentTimeMillis();
		}
	}

	private ModifyCacheableAdapter cached;

	private String modify(String input) {
		HtmlModifier replacer = new HtmlModifier(cached, input);
		return replacer.getModifiedHtml(true).toString();
	}

	@Before
	public void before() {
		cached = new ModifyCacheableAdapter();
	}

	@Test
	public void testDontTouchMarkupWithoutTitle() throws Exception {
		String expected = "<a href=\"xxx\">xxx</a>";
		assertEquals(expected, modify(expected));
	}

	@Test
	public void testModify() throws Exception {
		String expected = "<html><head><!--MoCuishle begin head--><title>"
				+ "</title><!--MoCuishle end head--><style type=\"text/css\" media=\"screen\">html {border-left: 5px dotted; border-color:#00B000;}</style><!--MC-->"
				+ "</head><body><!--MoCuishle begin body-->" + "<a href=\"xxx\"><font color=\"#B00000\">xxx</font></a>"
				+ "<!--MoCuishle end body--><p align=\"right\">up to date - <a href=\"http://127.0.0.1:9090/browse/www.freebsd.org\">browse</a> <a href=\"http://127.0.0.1:9090/browse-outgoing\">outgoing</a> Mo Cuishle"
				+ "</body></html>";
		assertEquals(expected, modify("<html><head><title></title></head><body><a href=\"xxx\">xxx</a></body></html>"));
	}

	@Test
	public void testModifyEncodedUrlInBodyEnd() throws Exception {
		cached = new ModifyCacheableAdapter() {
			@Override
			public Markup getMarkup() {
				return new Markup(getPlatform()) {
					@Override
					public Modify getModify() {
						return new Modify() {
							@Override
							public String getBodyEnd() {
								return "<!--$3 is encoded URL-->%3$s";
							}
						};
					}
				};
			}
		};
		String expected = "<html><head><!--MoCuishle begin head--><title>"
				+ "</title><!--MoCuishle end head--><style type=\"text/css\" media=\"screen\">html {border-left: 5px dotted; border-color:#00B000;}</style><!--MC-->"
				+ "</head><body><!--MoCuishle begin body-->"
				+ "<a href=\"http://yyy\"><font color=\"#B00000\">xxx</font></a>"
				+ "<!--$3 is encoded URL-->http%3A%2F%2Fyyy" //
				+ "</body></html>";
		assertEquals(expected,
				modify("<html><head><title></title></head><body><a href=\"http://yyy\">xxx</a></body></html>"));
	}
}
