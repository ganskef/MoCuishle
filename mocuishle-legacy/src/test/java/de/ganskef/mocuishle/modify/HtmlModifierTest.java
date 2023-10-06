package de.ganskef.mocuishle.modify;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.io.File;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.ganskef.mocuishle.ICacheableModify;
import de.ganskef.mocuishle.IPlatform;
import de.ganskef.mocuishle.Markup;

public class HtmlModifierTest {

	@Mock
	IPlatform platform;

	private ICacheableModify cached;

	@Before
	public void initMocks() {
		MockitoAnnotations.initMocks(this);
		when(platform.getHttpSpoolDir())
				.thenReturn(new File(String.format("target/%s/http", getClass().getSimpleName())));
	}

	/** TODO replace with Mockito */
	static class CacheableModifyMock implements ICacheableModify {

		private final IPlatform platform;

		private String url;

		CacheableModifyMock(IPlatform platform) {
			this.platform = platform;
		}

		@Override
		public String toUrl(String href) {
			return href;
		}

		@Override
		public boolean isRequested(String href) {
			url = href;
			return ("requested".equals(href));
		}

		@Override
		public boolean isCached(String href) {
			url = href;
			return ("cached".equals(href));
		}

		@Override
		public void recordBrowse(String title) {
		}

		@Override
		public String getLocalUrl() {
			return "http://127.0.0.1:9090";
		}

		@Override
		public String getHostName() {
			return "www.freebsd.org";
		}

		@Override
		public boolean isOkStatus() {
			return true;
		}

		@Override
		public Markup getMarkup() {
			return new Markup(platform);
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

	private String modify(String input) {
		HtmlModifier replacer = new HtmlModifier(cached, input);
		return replacer.getModifiedHtml(true).toString();
	}

	@Before
	public void before() {
		cached = new CacheableModifyMock(platform);
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
		cached = new CacheableModifyMock(platform) {
			@Override
			public Markup getMarkup() {
				return new Markup(platform) {
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
