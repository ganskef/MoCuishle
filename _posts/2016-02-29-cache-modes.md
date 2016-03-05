---
image: cache-mode-automatic.png
caption: "Welcome Page with mode switches."
subheadline: "Modes to prefer offline or online contents."
title: "Cache Modes"
---

Update 2016-03-05: Flatrate mode was answering from cache like automatic mode. 
This is fixed now. The versions from 2016-02-29 are obsolete.

New binary previews are uploaded with welcome page extended to switch modes for 
answering from cache.
<!--more-->

By clicking on *Mo Cuishle* in the web UI navigation lists a welcome page is 
provided. It's possible now there to switch modes of the cache.

# AUTOMATIC

This is the default behavior of *Mo Cuishle*. Depending the state of the network 
interface the contents are answered from cache. With a mobile connection the 
content from the cache is preferred.

# TETHERING

Using tethering via *Bluetooth*, *WLAN* or *USB* the device *Mo Cuishle* is 
running can not detect the mobile connection. This is an usual use case with a 
Notebook, so this mode enables a mobile behavior manually. 

# FLATRATE

With a mobile connection it's necessary to hit refresh to check for the latest 
contents. Use this mode to override this behavior and request contents if newer
than the cached version as done with a full connection.

# CACHEONLY

This mode should suppress all connections to the web, so it's a hidden mode. If 
requests will be relayed by the proxy it's a bug. It is a fairly new feature.

# Latest Netty 4.1.0.CR3

This release integrate the fixes of all known *Android* 5.0, 5.1, and 6.0 issues. 
Using a Snapshot is not needed any longer.

# Refactorings

It's done some restructuring and clean up in the user interface classes. All the 
views should be work like before.