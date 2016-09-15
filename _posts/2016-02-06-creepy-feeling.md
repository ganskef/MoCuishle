---
image: creepy-feeling.png
caption: "Modified content - verified by Google?"
subheadline: "A long running Firefox bug with minor priority."
title: "Creepy Feeling"
---

Using *Mo Cuishle* you're doing Man-In-The-Middle which is usually a very bad 
thing. A Mozilla add-on installs a root certificate and you can enable and 
disable it with a single click. And, there is a bug in Firefox:
<!--more-->

* [Bug 1243901](https://bugzilla.mozilla.org/show_bug.cgi?id=1243901) - Replace Certificate Authority without user notification, wrong CA displayed 
* [Bug 1230321](https://bugzilla.mozilla.org/show_bug.cgi?id=1230321) - Firefox 42.0 shows wrong certificate issuer when reloading a page after cert has changed 
* [Bug 1225299](https://bugzilla.mozilla.org/show_bug.cgi?id=1225299) - Odd behaviors of the RC4 notifications bar
* [Bug 1196728](https://bugzilla.mozilla.org/show_bug.cgi?id=1196728) - Wrong Certificate Info in Tooltip and 'Short-Info'

Having *Mo Cuishle* in my personal use only, this might not be a problem. But 
now it's been published. So I've filed the 
[bug](https://bugzilla.mozilla.org/show_bug.cgi?id=1243901) RESOLVED DUPLICATE. 

I've seen this behavior from the beginning 2015 at least. Let's see what happens:

Open https://www.google.de, 

 - switch to MITM, refresh, icon shows "Verified by: Google Inc" -> bad, 
 - click on the symbol, dialog shows "Verified by: Google Inc" -> bad, 
 - click More Informations..., settings pane shows Mo Cuishle -> okay, ...

In the bug I've described the comfortable features in the API which made me the 
*creepy feeling*. Within the 
[bug comment #3](https://bugzilla.mozilla.org/show_bug.cgi?id=1243901#c3) it's 
stated "As for nsIX509CertDB2, it was folded into nsIX509CertDB and no longer 
exists, ..." 

A year before, January 2015, at time of writing the add-on at least, 
[nsIX509CertDB2](http://doxygen.db48x.net/mozilla-full/html/df/d1e/interfacensIX509CertDB2.html) 
was separated from the frozen 
[nsIX509CertDB](http://doxygen.db48x.net/mozilla-full/html/db/d7a/interfacensIX509CertDB.html). 

These two methods in the second interface are introducing the power to replace 
certificates in no time without a user notification like it is done by *Mo 
Cuishle*. I'm a beginner programming JavaScript with Firefox APIs and I hear 
they talking, but...

# Update 2016-04-03

Please, don't misunderstand me. I love *Mozilla Firefox* for giving me the 
feature to install a certificate. But, it would be okay to ask the user for 
trusting the CA for websites with the well known 
[dialog](https://github.com/ganskef/LittleProxy-mitm#get-it-up-and-running), 
and to delete the *Mo Cuishle* certificate with its explicit identifier only. 
This is possible with the old *API*, without the two new methods, I think.

# Update 2016-09-10

With *Aurora Firefox Developer Edition* 50.0a2 (2016-09-10) I've tried out the 
behavior today. It's a fresh look, but it is the same again. The modified 
content (notice the green bullets border) is "Verified by Google Inc.":

<img class="" src="{{ site.urlimg }}creepy-feeling-201609.png" alt="" height="270">
<img class="" src="{{ site.urlimg }}creepy-feeling-201609-2.png" alt="" height="270">
