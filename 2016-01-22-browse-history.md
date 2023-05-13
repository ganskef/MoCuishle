![](../images/welcome-browse.png)

> Your history of pages recently visited.

# Browse History

*Mo Cuishle* provides your browse history ordered by the last access.

The list contains distinct URLs grouped by the host name. The intention is to 
show document URLs only, filtered, with no internal stuff like images or CSS. 

Since the history is built in the proxy server instead of the browser it's 
difficult a little. Sometimes *iframes* with ads are included too. Consider to 
[block](2016-01-19-block-unblock.md) such unwanted contents.

*Quick browse...* and *Show all...* switches between the first 20 entries only 
and a full list. It's not a complete paging mechanism, but avoids waiting for 
huge directories on regular use. Therefore it defaults to the short lists mostly.

It's possible to *delete* URLs or complete hosts. Deleted elements can be 
restored by *undelete*. *Trash...* lists the deleted URLs only. *Empty trash* 
removes the strikethrough entries.

Please note that the documents are deleted only. Referenced data are still 
present in the cache. There is no feature to clean up the cache at the 
moment[^1]. Since it's a file system based cache it's possible to delete 
directories.

Beginning with the 2016-05-16 version it's possible in the list of hosts to 
[export](2016-05-16-export-ui-and-cleaned-titles.md#export-cached-contents)
all the cached contents.

----
[^1]: Modern web sites refer its dependencies not via *href* or *src* only. To 
      catch everything it's necessary to interpret all the CSS and JavaScript 
      like a browser does it.