---
subheadline: "Guided installation on Android 6."
title: "Better Android"
---

*Mo Cuishle* runs on Android but installation isn't fail safe with the latest 
versions. It's leaded better now, and testing includes the latest Netty snapshot 
4.1.

<!--more-->

 * To fix HTTPS on Android devices 5., 5.1, 6.0 the latest Netty snapshot is 
   used with the testing APK including [#4718](https://github.com/netty/netty/issues/4718).

 * Android 6 introduces permissions to access the public sdcard volume the Mo 
   Cuishle cache resides. The controls to start the background proxy process 
   and to install the browser extension are disabled, and the summary points the 
   user to the problem.

 * A Mozilla browser is required to install the add-on. If missed the system 
   browser opens the URL of the 
   [Mozilla archive](https://ftp.mozilla.org/pub/mobile/releases/). 
