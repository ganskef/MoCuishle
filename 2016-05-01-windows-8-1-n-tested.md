![](../images/windows-search.png "Mozilla Firefox 46.0 doing Full Text Search on Windows.")

> Gotten a license to work with.

# Windows 8.1 N tested

New binary previews are uploaded to fix issues discovered during tests with 
*Windows* and *Oracle's* 32-bit *JVM*. It affects the *Full Text Search* and 
caching of *HTTPS* pages in these environments.

While I'm regular using *Mo Cuishle* on *Debian GNU/Linux* and *Mac OS X*, 
*Microsoft Windows* wasn't tested very well. Now I've acquired a license for 
testing purposes. Especially *Java Nonblocking IO (NIO)* and *Transport Level 
Security (TLS)* is needed both to test on every supported system. On *Android* 
it feels like it's needed on every supported device :-) . 

## Full Text Search on Windows

[<img class="left" src="../images/windows-folder-300.png" alt="">](../images/windows-folder.png)
*Mo Cuishle* provides a [Full Text Search](../full-text-search/). 
Building the *SQLite* index is done in background. The last step is renaming the 
index file from `mocuishle.sqlite.tmp` to `mocuishle.sqlite`. This is failing 
on *Microsoft Windows*.

I've spent a lot of time solving this asynchronous write effects with no 
success. So the first search request renames the file if necessary and possible.
Now full text search is properly working on *Windows* too.

## HTTPS with Oracle's 32-bit JVM

[<img class="left" src="../images/windows-github-300.png" alt="">](../images/windows-github.png)
With *Windows 8.1 N* from [https://java.com/download](https://java.com/download) 
a 32-bit *JVM* was installed by default. This has covered up an error introduced 
with the [Chrome and Mediaserver](2016-02-07-chrome-and-mediaserver.md) 
change, see [history](2016-09-26-mocuishle.md#history). For more details 
refer to
[LittleProxy-mitm#18](https://github.com/ganskef/LittleProxy-mitm/issues/18).


## Notable Addition

The tests have shown that a running proxy isn't reliably available. *Windows* 
is not delivered with *Java* anymore, the *Android* app could be off, a lot of 
other reasons are possible.

The *Mo Cuishle* add-on should not break the browser if the proxy process can't 
be reached at all. On enabling the the blue MC the connection is verified now. 
The browser settings are reseted if it fails, and the add-on is disabled again.