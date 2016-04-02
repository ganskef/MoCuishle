---
image: android-site-actions.png
caption: "Mo Cuishle's simple navigation for Mobile, too."
subheadline: "Rebuilt to be signed, On/Off Switch in Android."
title: "Signed to be Simple"
---

New binary previews are uploaded to fix "*Mo Cuishle* could not be verified for
use in Firefox and has been disabled." In Android a switch to enable/disable 
*Mo Cuishle* is added.
<!--more-->

# Signed Mozilla Firefox Add-ons

The *Mo Cuishle* Add-ons are signed by the
[Mozilla Add-ons site (AMO)](https://addons.mozilla.org/) now. This passes back
a lot of simplicity, lost in Firefox version 43 and above. Signing will be 
mandatory with no override, in Firefox 46 versions. For details, see [Mozilla
Support](https://support.mozilla.org/en-US/kb/add-on-signing-in-firefox).

The full featured *Mo Cuishle* Add-on, containing *Java* classes and *SQLite*
binaries can not be an *Executable Jar* any longer. The *META-INF/MANIFEST.MF* 
file is replaced during signing. Nevertheless a *XPI* is a *Zip* archive, 
containing the *Jar* file. Extract the mocuishle.jar file to run *Mo Cuishle* 
stand alone. 

The second add-on delivered with the *Android App* is an alternate packaging to 
use with a standalone running *Mo Cuishle* proxy. It's 20 KB only.

# Little blue MC switches

On desktop *Mo Cuishle* provides an *ActionButton* to switch between enabled and
disabled. It changes the needed proxy and cache settings in *Mozilla Firefox*. 
The certificate store is changed on install/enable/disable/uninstall *Mo 
Cuishle* only. Now, on *Android* a *PageActions* button is added to the URL bar,
to switch on/off.

The second blue *MC* in the *Android Title Bar* belongs to the *Mo Cuishle* 
service. This is launched by the *Android App*, separated from the add-on but 
neccessary, since it is the *Java* proxy to work with.

# Refactorings, a new build process

Behind the scenes, I've added tests for the *Web UI* navigations. I've done some
refactorings, since I've got some ideas to introduce new features. Signing 
enforces replacing *cfx* with *jpm* to build with the *Add-on SDK*. The signed 
add-on is integrated with the *Android App*. I have to reorganize my build.
