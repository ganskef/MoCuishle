---
image: fulltextsearch-feb-2016.png
caption: "This search &quot;phrase&quot; works unexpected."
subheadline: "Review of Full Text Search for issue #1 and more."
title: "Search reviewed, close #1"
---

New binary previews are uploaded to fix
[issue #1](https://github.com/ganskef/MoCuishle/issues/1). Update: additionally
uploaded a second *Mozilla/Java* xpi version to fix network state detection on 
[*Windows*]({{ site.url }}/search-reviewed/#fixed-offline-on-microsoft-windows).
<!--more-->

# Bleeding Edge

*Mo Cuishle* is falling back now to the *SQLite* implementation of the *Android* 
system if its [sqlite4java](https://bitbucket.org/almworks/sqlite4java) 
implementation fails. With a bleeding edge Android the JNI libraries couldn't 
be loaded. It could not been reproduced in x86 emulator, but happens on a 
*Nexus 5* device running *Android* version 6.0.1.

On *Android* the *SQLite* extension [FTS3](https://www.sqlite.org/fts3.html) 
lacks tokenizers like unicode61. This is a disabled compile time option and 
leads to problems indexing and searching international text. For example the 
German word `Ärzte` `ärzte` `arzte` is the same with unicode61 but different 
with the default tokenizer. 

# Phrase Queries

By the way I've found some problems in the full text search navigation. Phrase 
queries, a combination of tokens and token prefixes (with *) in quoting marks, 
won't work. This is fixed now. Try to search words or words with * enclosed with
&quot; signs. Every word or phrase is combined with AND by default, but you 
could use OR and NOT, too (in capitals). 
[more details...](https://www.sqlite.org/fts3.html#section_3)

# Fixed offline on Microsoft Windows

At 2015-09-25 15:29:35 I've replaced usage of NetworkUtils from *LittleProxy* in 
*Mo Cuishle* since the class was removed. I've missed to test it on a *Windows* 
device. I'm dreadfully sorry. Recently I had a chance to try, and it fails. Of 
course! The state of the network is detected online always, so it doesn't answer 
from cache while offline. A workaround could be manually use the 
[tethering]({{ site.url }}/cache-modes/) mode, but now it's fixed.
