---
#image: welcome-block.png
subheadline: "Download and get it working on Java platforms"
title: "Mozilla Install"
---

*Mo Cuishle* binary preview is provides as unsigned XPI only. It's not reviewed 
and listed on [addons.mozilla.org](https://addons.mozilla.org/) (AMO).
<!--more-->

**The Software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
either express or implied.**

**Security Note: The encryption of the browser is broken. All content is stored 
UNENCRYPTED on your device.**

**<a class="button info" 
href="../mocuishle-binary-preview/mocuishle-1.0-20160125.xpi">Download</a>** 7MB

Please open an [issue](https://github.com/ganskef/MoCuishle/issues) containing 
informations about your environment in case you've no success.

# Java

First of all you need *Java* installed on your system. 

#Mozilla Add-on

The *Add-on XPI* depends on a *Mozilla* browser to install. Install it by 
drag and drop `mocuishle-1.0-SNAPSHOT.xpi` onto the browser. That's simple!

#Complication (first time only)
Since December 2015, starting with *Firefox* 43.0 you can't install an unsigned 
add-on like *Mo Cuishle* binary preview. So you have to use this trick [^1]:

Enter the URL `about:config` in *Firefox*, confirm if needed, then enter 
`xpinstall.signatures.required` in the search field, and set the value to 
`false`. 

I've tried to sign the add-on but it's not so easy... A work item. 

#Start Up

*Mo Cuishle* is enabled on start up and launches the Java proxy in background. 
The browser opens a Tab with the [Browse History](/browse-history/). 

Clicking the blue *MC* Toolbar Icon stops/starts the Java process and 
removed/entered the following required settings in *Mozilla Firefox*:

 * Manual proxy configuration localhost:9090 for all protocols.

 * Browser cache size to 0 to avoid conflicting with *Mo Cuishle* caching.

 * Disable automatic online/offline management for *Mo Cuishle*.

The *Mozilla Add-on* includes the *Mo Cuishle* logging in the *Browser Console*. 
Try <kbd>ctrl</kbd>+<kbd>shift</kbd>+<kbd>J</kbd>.

Enabling/Disabling the installation imports/removes the Certificate Authority 
needed to cache HTTPS contents. Try it in case of problems with certificates.

#Other Browsers, Settings

*Mo Cuishle* is a proxy application following HTTP standards. So it can be used 
without the add-on or with other browsers like *Google Chrome* too. Since it's 
an *Executable JAR* simply enter the command:

 java -jar mocuishle-1.0-SNAPSHOT.xpi

Logging is written to the console.

You have to find equivalents for following *Mozilla Firefox* preferences 
configured with `about:config`:

Manual proxy configuration localhost:9090 for all protocols:

 * prefs.set("network.proxy.http", "localhost");
 * prefs.set("network.proxy.http_port", 9090);
 * prefs.set("network.proxy.ssl", "localhost");
 * prefs.set("network.proxy.ssl_port", 9090);
 * prefs.set("network.proxy.type", 1);

Browser cache size to 0 to avoid conflicting with *Mo Cuishle* caching:

 * prefs.set("browser.cache.disk.capacity", 0);

Disable automatic online/offline management for *Mo Cuishle*:

 * prefs.set("network.manage-offline-status", false);
 * prefs.set("network.online", true);

Disable weak cipher suites failing on Android (should be obsolete, filtered by 
LittleProxy-mitm):

 * prefs.set("security.ssl3.dhe_rsa_aes_128_sha", false);
 * prefs.set("security.ssl3.dhe_rsa_aes_256_sha", false);

 [^1]: The trick was formerly described here: <a href="https://support.mozilla.org/en-US/kb/add-on-signing-in-firefox?as=u&utm_source=inproduct#w_override-add-on-signing-advanced-users">https://support.mozilla.org/en-US/kb/add-on-signing-in-firefox?as=u&utm_source=inproduct#w_override-add-on-signing-advanced-users</a>