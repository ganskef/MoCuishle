# Mo Cuishle - A caching proxy for offline use

> All web pages you've seen on the Internet via HTTP or HTTPS will be available later to read them without having to remain connected.

See **https://ganskef.github.io/MoCuishle/** for further information.

## Build and install

Get this repository:

    git clone https://github.com/ganskef/MoCuishle

Change dir and build with Maven:

    mvn clean install

Tested on *Windows*, *macOS*, *GNU/Debian* and *FreeBSD*, development on *Arch Linux* with *OpenJDK* 21+35 and *Maven* 3.8.7. It's simple *Java* internally with a *Kotlin* runtime dependency *OkHttp*.

## Initialize the application

First run the *Java* application by double click or command line:

    java -jar mocuishle/target/mocuishle-2.2.0-shaded.jar

Browser communication to the application is initialized, it's necessary if the location of the `JAR` is changed:

    122    2023-10-03 23:19:26,664 INFO  [main] d.g.m.MoCuishleMain - Install Native Messaging to support browser extensions...
    128    2023-10-03 23:19:26,670 INFO  [main] d.g.m.m.BrowserExtensionSupport - FIREFOX_UNIX
    128    2023-10-03 23:19:26,670 INFO  [main] d.g.m.m.BrowserExtensionSupport - CHROME_UNIX
    128    2023-10-03 23:19:26,670 INFO  [main] d.g.m.m.BrowserExtensionSupport - CHROMIUM_UNIX
    494    2023-10-03 23:19:27,036 INFO  [main] d.g.m.p.McOkProxy - Starting proxy server with port 9090 ...
    502    2023-10-03 23:19:27,044 INFO  [main] d.g.m.p.McOkProxy - Startup done

In the home directory a `~/MoCuishle` directory is created. It contains the runtime data of the application and will updated if needed. A symbolic link to put it to another location is okay.

## Browser Certificate

The first execution creates a certificate `~/MoCuishle/mocuishle.pem` to install in your browsers Authorities or system wide if needed.

* [X] ~~*2023-09-30 MoCuishle uses the formerly mocuishle.pem instead of okhttp.pem (but mocuishle.p12 contains a root certificate instead of the intermediate certificate, might not work)*~~ [2023-10-06] Mo Cuishle works with an existing KeyStore too.

The second file `~/MoCuishle/mocuishle.p12` is generated to hold the matching private key and intermediate certificate, the proxy use to intercept upstream connections (Man In The Middle).

If needed, use a PKCS12 KeyStore (and certificate authority) of your own by setting this system properties:

|Property                               |Default value               |
|:------------------------------------- |:-------------------------- |
|de.ganskef.mocuishle.keystore.alias    |mocuishle                   |
|de.ganskef.mocuishle.keystore.name     |_Mo Cuishle (offline cache) |
|de.ganskef.mocuishle.keystore.password |Be Your Own Lantern         |

(historic default password taken from <https://github.com/adamfisk/LittleProxy>)

## Install Browser Extensions

For *Chrome* like browsers install the built browser extension with the URI: `chrome://extensions`. It requires to enable the *Developer mode*. Use the button *Load unpacked* to select the subdir `mocuishle-browser/target/classes` in your workspace. The browser should open an new tab with <http://localhost:9090/>.

**Hint:** For *Chrome* browsers it's **necessary to get the ID** of the unpacked extension which is generated at first installation. Until it's published in the Web Stores, please modify the native host config files. I'm working on it, see: [#7](https://github.com/ganskef/MoCuishle/issues/7).

*[Native messaging host](https://developer.chrome.com/docs/apps/nativeMessaging/#native-messaging-host)* requires `allowed_origins` which is generated while installing an unpacked extension in the *Chrome* browser. The file looks like this, but with your home dir, here with an additional unpacked extension:

    {
      "name": "de.ganskef.mocuishle",
      "description": "A caching proxy for offline use.",
      "path": "/home/frank/MoCuishle/mocuishle.sh",
      "type": "stdio",
      "allowed_origins": [ "chrome-extension://ajccdogbepemoknjbdigfdnjlinpbedj/","chrome-extension://oohnpccaehbcpnanlbbboljabnajlhem/" ]
    }

For *Mozilla* browsers it's necessary to use signed extension. You have to use a *[Developer Edition](https://www.mozilla.org/firefox/developer/)* to load the built extension `mocuishle-browser/target/mocuishle-browser-2.1.4-firefox.zip`. Have a look at the release page to grab a signed extension.

Of course you can use an other proxy switcher, but try to disable the browser cache to avoid conflicts, since *Mo Cuishle* should be the cache for offline use.

## Trouble Shooting

* [x] ~~I had some problems with more recent versions of *Java* and *Maven* while testing *macOS* and *Windows*. I'm working on it (see: [#5](https://github.com/ganskef/MoCuishle/issues/5). Please use the given versions at the moment. Double check using an *OpenJDK* instead of a *JDK* or *JRE* and a [Previous Stable 3.8.x Release](https://maven.apache.org/download.cgi?.#previous-stable-3-8-x-release) of *Maven*.~~ 2023-10-03 After replacing *LittleProxy-mitm* with *OkProxy* build succeeds on every tested system, with current *Maven* and *Java*, with `LANG=de_DE` and `LANG=en_US`. Please describe an [Issue](https://github.com/ganskef/MoCuishle/issues), if not.
* [x] ~~I had test failures on macOS and Windows, try `mvn clean install -DskipTests`. I'm working on it.~~ 2023-09-30 After replacing *LittleProxy-mitm* with *OkProxy* tests are working on every tested system.
* You have to be an administrative user in *macOS*, maybe *Windows* too to install the browser certificate as a trusted certificate agency. If not, You could use a *Mozilla* browser to install it.
* *Native Host Messaging* (browser extension starts the *Java* application) won't work with new installed unpacked extension in *Chrome* browsers. I'm working on it, see: [#7](https://github.com/ganskef/MoCuishle/issues/7). **Simply use the autostart feature of your system to launch the MoCuishle JAR in the background.**

This is not as simple like the [early days](https://ganskef.github.io/MoCuishle/#!2016-09-26-mocuishle.md#The_vision_-_Ideas_behind). For good security reasons in [modern browser extensions](https://blog.mozilla.org/addons/2018/08/21/timeline-for-disabling-legacy-firefox-add-ons/) it's not possibly anymore:

* ... to install the certificate in the browser automatically
* ... to simply handle a Java process by the extension
* ... to package the Java application into the extension

