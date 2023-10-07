![](images/under-the-hood.jpg "Under the hood")

> OkProxy and upgraded dependencies

#  Replace Netty with OkHttp

Current pull request [Replace Netty with OkHttp (#10)](https://github.com/ganskef/MoCuishle/pull/10) closes issue [Recent JDK and Maven causes build failures (#5)](https://github.com/ganskef/MoCuishle/issues/5). Switching development to recent Java and Maven was an important requirement to proceed.

[LittleProxy](https://github.com/adamfisk/LittleProxy) and [Netty](https://netty.io/) have been working well in Mo Cuishle for ten years, but OkHttp works better now. The dependencies of Mo Cuishle are modernized and stripped down, the resulting code is minimized and clear too. The streamlined [Impersonation](https://github.com/ganskef/MoCuishle/blob/master/mocuishle-okproxy/src/main/java/de/ganskef/okproxy/Impersonation.java) class based on `okhttp-tls` is an excellent example. [Security](https://square.github.io/okhttp/security/security/) is a question of trust. [OkHttp](https://square.github.io/okhttp/) and [Okio](https://square.github.io/okio/) is pure and simple Java byte code in the JVM, but uses the [Kotlin standard library](https://kotlinlang.org/) at runtime.

## A lot of possibilities

[Kotlin Native](https://kotlinlang.org/docs/native-overview.html) offers the ability to compile code to native binaries which can run without a virtual machine on platforms like iOS, and Kotlin should have better support of Android development of corse. OkProxy is implemented in a Kotlin variant too.

The [Ktor](https://ktor.io/docs/welcome.html) framework easily build connected applications. It's possible to bundle modules to a modern web application by a configuration file without recompiling the application. It might provide the *Mo Cuishle - Legacy* features:
* Web Server with [routing](https://ktor.io/docs/routing-in-ktor.html) for [static](https://ktor.io/docs/serving-static-content.html) and dynamic content
* Templating with [JTE](https://ktor.io/docs/jte.html)
* Webextension might be able to stop Mo Cuishle by a [Shutdown URL](https://ktor.io/docs/shutdown-url.html)
* ...

## Maintenance

An important step after upgrading to current dependencies is automated testing and automated build on different platforms. I've done this manually and resolved a lot of surprises, but automation can save a lot of time.

To reimplement proven features it should give integration tests first. Fine grained JUnit tests could hinder restructuring. But new implementations should be test first. This will be an important issue for me.

Performance and reliability on different systems seems to be very improved. But it would be better to measure it by test with a [tool](https://github.com/denji/awesome-http-benchmark), and to scan for [security](https://www.zaproxy.org/) issues too.