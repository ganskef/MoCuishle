---
image: windows-search.png
caption: "Mozilla Firefox 46.0 doing Full Text Search on Windows."
subheadline: "Gotten a license to work with."
title: "Windows 8.1 N tested"
---

New binary previews are uploaded to fix issues discovered during tests with 
*Windows* and *Oracle's* 32-bit *JVM*. It affects the *Full Text Search* and 
caching of *HTTPS* pages in these environments.
<!--more-->

# Full Text Search on Windows

[<img class="left" src="{{ site.urlimg }}windows-folder-300.png" alt="">]({{ site.urlimg }}windows-folder.png)
*Mo Cuishle* provides a [Full Text Search]({{ site.url }}/full-text-search/). 
Building the *SQLite* index is done in background. The last step is renaming the 
index file from `mocuishle.sqlite.tmp` to `mocuishle.sqlite`. This is failing 
on *Microsoft Windows*.

I've spent a lot of time solving this asynchronous write effects with no 
success. So the first search request renames the file if necessary and possible.
Now full text search is properly working on *Windows* too.

# HTTPS with Oracle's 32-bit JVM

[<img class="left" src="{{ site.urlimg }}windows-github-300.png" alt="">]({{ site.urlimg }}windows-github.png)
With *Windows 8.1 N* from [https://java.com/download](https://java.com/download) 
a 32-bit *JVM* was installed by default. This has covered up an error introduced 
with the [Chrome and Mediaserver]({{ site.url }}/chrome-and-mediaserver/) 
change, see [history]({{ site.url }}/mocuishle/#history). For more details 
refer to
[LittleProxy-mitm#18](https://github.com/ganskef/LittleProxy-mitm/issues/18).


# Notable Addition

The tests have shown that a running proxy isn't reliably available. *Windows* 
is not delivered with *Java* anymore, the *Android* app could be off, a lot of 
other reasons are possible.

The *Mo Cuishle* add-on should not break the browser if the proxy process can't 
be reached at all. On enabling the the blue MC the connection is verified now. 
The browser settings are reseted if it fails, and the add-on is disabled again.