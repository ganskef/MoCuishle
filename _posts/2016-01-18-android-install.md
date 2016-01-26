---
#image: welcome-block.png
subheadline: "Download and get it working on mobile devices"
title: "Android Install"
---

*Mo Cuishle* binary preview is provided as an unsigned APK only. It's 
not available at Google Play or other stores.
<!--more-->

**The Software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
either express or implied.**

**Security Note: The encryption of the browser is broken. All content is stored 
UNENCRYPTED on your device.**

**<a class="button info" 
href="{{ base }}/mocuishle-binary-preview/mocuishle-1.0-20160125.apk">Download</a>&nbsp;<a 
class="button info" 
href="{{ base }}/mocuishle-binary-preview/mocuishle-testing-1.0-20160125.apk">5+ testing</a>** 4MB

There is an issue [#4718](https://github.com/netty/netty/issues/4718) with Netty 
and secured connections on *Android* versions 5+ ongoing. Use the testing APK 
with Android 5.0, 5.1, 6.0 only. It's working but I've noticed some blocking and 
cancelled connections on emulators. It could be better to use the regular APK 
which terminates all HTTPS connections on these phones.

#Mozilla browser

First you have to install a *Mozilla* browser. Grab it from *Google Play*, or 
from the *Mozilla* [archive](https://ftp.mozilla.org/pub/mobile/releases/). 

#Android APK, containing the Add-on

To install *Mo Cuishle* binary preview it's neccessary to enable *Unknown 
sources*. This option is in *Settings* under *Security* on your phone. After 
this it's possible to install the APK by open it in browser or a file manager.

The *Mo Cuishle* app starts with a simple settings activity. You have to enable 
the Proxy with the first setting. A notification icon "MC" is displayed while 
running the service in background. 

*Mo Cuishle* consists of an app and a *Mozilla* add-on separated since *Android* 
has it's *Dalvik* runtime. The *Mozilla* add-on can't spawn a *Java* process so 
the *Mo Cuishle* service has to be up and running always. 

The second setting displays a list of the installed *Mozilla* browsers. 
Selecting one tries to install the add-on onto. That's simple! 

#Complications

Please enable unsigned add-ons regarding 
[Mozilla Install]({{ base }}/mozilla-install/#complication-first-time-only) first.

Sometimes I've seen the browser showing the file URL but not installing the 
add-on. In this case you can modify the URL to open the directory 
`.../MoCuishle/browser-extension/` in the browser. Then click on *mocuishle.xpi* 
directly to start the installation. 

Don't use the *Mozilla* download with *Android*. It contains *Java* classes and 
*Sqlite* binaries you never need on your phone, and more important the path of 
*Mo Cuishle* on your device is probably wrong, so the certificate will not be 
installed. This causes HTTPS to fail. 

#Extended Settings

TO DESCRIBE IT HERE

For background informations please refer to 
[Mozilla Install]({{ base }}/mozilla-install/#other-browsers-settings) too. 