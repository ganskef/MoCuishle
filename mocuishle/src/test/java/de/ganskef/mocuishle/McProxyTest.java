package de.ganskef.mocuishle;

import static org.junit.Assert.*;

import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy.Type;
import java.net.SocketAddress;
import java.net.URL;
import java.net.URLConnection;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import de.ganskef.test.Client;
import de.ganskef.test.IProxy;
import de.ganskef.test.Server;

public class McProxyTest {

	private static Server server;
	private static IProxy offlineProxy;
	private static IProxy onlineProxy;

	@AfterClass
	public static void afterClass() {
		server.stop();
		offlineProxy.stop();
		onlineProxy.stop();
	}

	@BeforeClass
	public static void beforeClass() throws Exception {
		server = new Server(9091).start();
		onlineProxy = new McTestProxy(9092) {
			public ConnectionState getConnectionState() {
				return ConnectionState.FULL;
			};
		}.start();
		offlineProxy = new McTestProxy(9093) {
			public ConnectionState getConnectionState() {
				return ConnectionState.OFFLINE;
			};
		}.start();
	}

	@Test
	public void testNormalImage() throws Exception {
		long ms = System.currentTimeMillis();
		String url = //
				server.getBaseUrl() + "/src/test/resources/www/SMALL_IMAGE.png";
		// "http://127.0.0.1:80/~frank/SMALL_IMAGE.png";
		File direct = new Client().get(url);

		File online = new Client().get(url, onlineProxy); // once to spool
		assertEquals(direct.length(), online.length());

		File offline = new Client().get(url, offlineProxy);
		assertEquals(direct.length(), offline.length()); // twice for spooled

		System.out.println((System.currentTimeMillis() - ms) + "ms");
	}

	// Note: this test depends highly on systems and Netty versions. It works
	// with Debian 7, but fails with 6 and Mac OS X with an older Java. It works
	// with Netty 4.0.25.Final, but fails with 4.1.0.Beta3. An other Netty works
	// offline, but fails online with missed css. It is not the same with
	// Windows.
	// It works for me with 4.1.0.Beta4 and 4.1.0.Beta5.
	//
	@Test
	public void testHugeImage() throws Exception {
		String url = //
				server.getBaseUrl() + "/src/test/resources/www/HUGE_IMAGE.JPG";
		// "http://127.0.0.1:80/~frank/HUGE_IMAGE.JPG";
		File direct = new Client().get(url);

		File online = new Client().get(url, onlineProxy); // once to spool
		assertEquals(direct.length(), online.length());

		File offline = new Client().get(url, offlineProxy);
		assertEquals(direct.length(), offline.length()); // twice for spooled
	}

	@Test(timeout = 1000)
	public void testDirectServerBlocked() throws Exception {
		String url = //
				server.getBaseUrl() + "/src/test/resources/www/SMALL_IMAGE.png";
		// "http://127.0.0.1:80/~frank/SMALL_IMAGE.png";
		new Client().get(url);
	}

	@Test(timeout = 1000)
	public void testOfflineProxyBlocked() throws Exception {
		String url = //
				server.getBaseUrl() + "/src/test/resources/www/SMALL_IMAGE.png";
		// "http://127.0.0.1:80/~frank/SMALL_IMAGE.png";
		new Client().get(url, offlineProxy);
	}

	@Test(timeout = 1000)
	public void testOnlineProxyBlocked() throws Exception {
		String url = //

				// > 70! secs blocked with local Netty server
				server.getBaseUrl() + "/src/test/resources/www/SMALL_IMAGE.png";

		// > 15 secs blocked with local Apache server
		// "http://127.0.0.1:80/~frank/SMALL_IMAGE.png";
		new Client().get(url, onlineProxy);
	}

	@Test(timeout = 1000)
	public void testAlternateClient() throws Exception {
		SocketAddress sa = new InetSocketAddress("127.0.0.1", onlineProxy.getProxyPort());
		java.net.Proxy pc = new java.net.Proxy(Type.HTTP, sa);
		URL url = new URL(server.getBaseUrl() + "/src/test/resources/www/SMALL_IMAGE.png");
		URLConnection conn = url.openConnection(pc);
		conn.connect();
		InputStream is = conn.getInputStream();
		while (is.read() != -1) {
		}
		/*
		 * Der Netty-Client hat bis zu 70 Sekunden blockiert, wenn man mit ihm über
		 * einen online Proxy gearbeitet hat. So passiert das nicht mehr.
		 */
	}
}
