# Mo Cuishle - A caching proxy for offline use

> All web pages you've seen on the Internet via HTTP or HTTPS will be available later to read them without having to remain connected.

See **https://ganskef.github.io/MoCuishle/** for details.

## Build and install

Get this repository with submodules included:

    git clone --recurse-submodules https://github.com/ganskef/MoCuishle

The submodules are in the special branch `enable_offline_caching_with_mitm` version `1.1.1-offline` to enable interception while offline by spoofing the requested address:

    git pull --recurse-submodules

*Mo Cuishle* depends on *OpenJDK 11* and *Maven 3.8* (see: [#5](https://github.com/ganskef/MoCuishle/issues/5)):

    mvn clean install

Tested with *Windows*, *macOS* and *Linux* **OpenJDK 11.0.19 and Maven 3.8.8**.

## Install Browser Extensions

For *Chrome* like browsers install the built browser extension with the URI: `chrome://extensions`. It requires to enable the *Developer mode*. Use the button *Load unpacked* to select the subdir `mocuishle-browser/target/classes` in your workspace. The browser should open an new tab with <http://localhost:9090/>.

**Hint:** For *Chrome* browsers it's **necessary to get the ID** of the unpacked extension which is generated at first installation. Modify this [this line](https://github.com/ganskef/MoCuishle/blob/master/mocuishle/src/main/java/de/ganskef/mocuishle/main/BrowserExtensionSupport.java#L158) depending on your installation and build again. I'm working on it, see: [#7](https://github.com/ganskef/MoCuishle/issues/7).

    private static final String CHROMIUM_EXTENSION = "chrome-extension://ajccdogbepemoknjbdigfdnjlinpbedj/";

*[Native messaging host](https://developer.chrome.com/docs/apps/nativeMessaging/#native-messaging-host)* requires `allowed_origins` which is generated while installing an unpacked extension in the *Chrome* browser.

For *Mozilla* browsers it's necessary to use signed extension. You have to use a *[Developer Edition](https://www.mozilla.org/firefox/developer/)* to load the built extension `mocuishle-browser/target/mocuishle-browser-2.1.2-firefox.zip`. Have a look at the release page to grab a signed extension.

## Initialize the application

First run the *Java* application by double click or command line:

    java -jar mocuishle/target/mocuishle-2.1.0-shaded.jar

Browser communication to the application is initialized, it's necessary if the location of the `JAR` is changed:

    0      2023-05-13 18:53:30,327 INFO  [main] main.McProxyMain - Install Native Messaging to support browser extensions...
    13     2023-05-13 18:53:30,340 INFO  [main] main.BrowserExtensionSupport - FIREFOX_UNIX
    14     2023-05-13 18:53:30,341 INFO  [main] main.BrowserExtensionSupport - CHROME_UNIX
    15     2023-05-13 18:53:30,342 INFO  [main] main.BrowserExtensionSupport - CHROMIUM_UNIX
    27     2023-05-13 18:53:30,354 INFO  [main] proxy.McProxy - Starting proxy server with port 9090 ...
    364    2023-05-13 18:53:30,691 INFO  [main] proxy.McProxy - Startup done

In the home directory a `~/MoCuishle` directory is created. It contains the runtime data of the application and will updated if needed. A symbolic link to put it to another location is okay.

## Browser Certificate

The first execution creates a certificate `~/MoCuishle/mocuishle.pem` to install in your browsers. For details see [LittleProxy-mitm](https://github.com/ganskef/LittleProxy-mitm#get-it-up-and-running).

## Trouble Shooting

* I had some problems with more recent versions of *Java* and *Maven* while testing *macOS* and *Windows*. I'm working on it (see: [#5](https://github.com/ganskef/MoCuishle/issues/5). Please use the given versions at the moment. Double check using an *OpenJDK* instead of a *JDK* or *JRE* and a [Previous Stable 3.8.x Release](https://maven.apache.org/download.cgi?.#previous-stable-3-8-x-release) of *Maven*.
* I had test failures on macOS and Windows, try `mvn clean install -DskipTests`. I'm working on it.
* You have to be an administrative user in *macOS*, maybe *Windows* too to install the browser certificate as a trusted certificate agency. If not, You could use a *Mozilla* browser to install it.
* *Native Host Messaging* (browser extension starts the *Java* application) won't work with new installed unpacked extension in *Chrome* browsers. I'm working on it, see: [#7](https://github.com/ganskef/MoCuishle/issues/7).

This is not as simple like the [early days](https://ganskef.github.io/MoCuishle/#!2016-09-26-mocuishle.md#The_vision_-_Ideas_behind), but I'm working on it.
