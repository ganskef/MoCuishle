# Mo Cuishle - A caching proxy for offline use

[*Mo Cuishle*](https://ganskef.github.io/MoCuishle/) is written in *Java* on top of 
[*LittleProxy-mitm*](https://github.com/ganskef/LittleProxy-mitm), 
[*LittleProxy*](https://github.com/adamfisk/LittleProxy), 
[*Netty*](http://netty.io/). It's available as a
[*Mozilla* add-on](http://ganskef.github.io/MoCuishle/mozilla-install/) 
and an 
[*Android* app](https://ganskef.github.io/MoCuishle/android-install/). So, you can 
use it nearly everywhere, on *Linux*, *Mac OS X*, *Windows*, *Android*, and I've 
seen it on *FreeBSD*. Other browsers like *Google Chrome* are possible. But 
you can **not** use it with *iOS* devices since they're lacking *Java*. 

This repository contains a [*Jekyll*](https://github.com/jekyll/jekyll) site 
based on the beautiful theme [*Simplicity*](http://phlow.github.io/simplicity/) 
by [*Phlow*](http://phlow.de/).

Please note it's the place to get a 
[binary preview](https://ganskef.github.io/MoCuishle/mocuishle/) of *Mo Cuishle*.
There's no [public code](https://ganskef.github.io/MoCuishle/license/) available 
at the moment. 

## Get it up and running

First of all you have to clone the repository. The *Jekyll* site is a *GitHub* 
[project page](http://jekyllrb.com/docs/github-pages/#project-pages). It resides 
in the branch named `gh-pages` therefore. It's the default branch. 

To create a clone enter this command line:
<pre>
$ git clone https://github.com/ganskef/MoCuishle.git
</pre>

The directory `MoCuishle` must not exist or empty. You could choose another name 
like here:
<pre>
$ git clone https://github.com/ganskef/MoCuishle.git MoCuishle.git
</pre>

To start a local web server with this site enter this commands:
<pre>
$ cd MoCuishle.git
$ bundle install --path ~/.gem
$ PATH="$HOME/.gem/ruby/2.1.0/bin:$PATH"
$ jekyll serve -c _config.yml,_config_dev.yml
</pre>

If there's no problem you can open this URL in a browser: 
[http://localhost:4000/](http://localhost:4000/)

## Dependencies

You need [*Ruby*](http://www.ruby-lang.org/en/downloads/) and 
[*RubyGems*](https://rubygems.org/pages/download). *Pyton* and *NodeJS* are 
obsolete now after *GitHub* is using *Jekyll 3*. With *Debian* it is simple 
like this:
<pre>
# aptitude install ruby rubygems-integration
</pre>

For me the simplest way was shown above with `bundle install`. 
The `--path ~/.gem` option installs all dependencies like *Jekyll* in the 
users home without modifying the system. 

You should add the gem path permanently to your PATH variable in your 
`~/.bashrc` file for example.

This procedure is written from my memories. Please let me know if something 
fails for you. 

## Contributing

The content of the *Mo Cuishle* website is written in 
[*Markdown*](https://github.com/adam-p/markdown-here/wiki/Markdown-Cheatsheet) 
residing in [_posts](https://github.com/ganskef/MoCuishle/tree/gh-pages/_posts).

To create a pull request you need a *GitHub* account, fork the repository, clone 
your fork locally, commit, push and use *GitGub* to create the pull request. If 
this isn't what you want I'm happy to integrate a patch or diff file too. Just 
try it. And, of course you have the option to open an 
[issue](https://github.com/ganskef/MoCuishle/issues).
