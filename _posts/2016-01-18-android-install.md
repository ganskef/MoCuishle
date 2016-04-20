---
image: android-startup.png
caption: "Start up with your browse history in Android."
subheadline: "Download and get it working on mobile devices."
title: "Android Install"
---

*Mo Cuishle* binary preview is provided as an unsigned APK only. It's not 
available at Google Play or other stores, but here.<br><a class="button info" 
href="{{ site.url }}/mocuishle-binary-preview/mocuishle-1.0-20160420.apk">Download</a> 4MB
<!--more-->

**The Software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
either express or implied.**

**Security Note: The encryption of the browser is broken. All content is stored 
UNENCRYPTED on your device.**

# Mozilla browser

First you have to install a *Mozilla* browser. Grab it from *Google Play*, or 
from the *Mozilla* [archive](https://ftp.mozilla.org/pub/mobile/releases/). 

# 1. Android APK, containing the Add-on

To install *Mo Cuishle* binary preview it's neccessary to enable *Unknown 
sources*. This option is in *Settings* under *Security* on your phone. After 
this it's possible to install the APK.

The *Mo Cuishle* app starts with a simple settings activity. You have to enable 
the Proxy with the first setting. A notification icon "MC" is displayed while 
running the service in background. [^1]

# 2. Mozilla Firefox Add-on

The second setting displays a list of the installed *Mozilla* browsers. 
Selecting one opens the location of the add-on to install:

<img class="" src="{{ site.urlimg }}android-settings-activity.png" alt="">
<img class="" src="{{ site.urlimg }}android-browser-extension.png" alt="">

Open `mocuishle.xpi` and install it. *That's simple!* 

# Start Up

*Mo Cuishle* is enabled on starting *Mozilla Firefox*. The browser opens a tab 
with the [Browse History]({{ site.url }}/browse-history/). 

Clicking the little blue *MC Page Action* icon stops/starts the proxy usage and 
removes/enters the required settings in *Mozilla Firefox*.

For background information please refer to 
[Mozilla Install]({{ site.url }}/mozilla-install/#other-browsers-settings), too. 

# Extended Settings, you will never need it

These are mostly for development and tests: 

<img class="" src="{{ site.urlimg }}android-extended-settings-1.png" alt="">
<img class="" src="{{ site.urlimg }}android-extended-settings-2.png" alt="">

---

[^1]: *Mo Cuishle* consists of an app and a *Mozilla* add-on separated since 
      *Android* has it's *Dalvik* runtime. The *Mozilla* add-on can't spawn a 
      *Java* process so the *Mo Cuishle* service has to be up and running always. 

