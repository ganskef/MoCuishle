# POM "mocuishle-okproxy"

This is the *Java* implementation of a http proxy server based on *[OkHttp](https://square.github.io/okhttp/)* with interception and TLS impersonation (Man In The Middle).

The modules of *Mo Cuishle* will be developed in the parent module. See the **[README](../README.md)** and **https://ganskef.github.io/MoCuishle/** for further information.

## Customization

The upstream client is [`OkHttpClient`](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-ok-http-client/) which allows [interceptor](https://square.github.io/okhttp/features/interceptors/) chaining and [last recent use cache](https://square.github.io/okhttp/features/caching/) usage given by [`OkHttpClient.Builder`](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-ok-http-client/-builder/). So initialize the proxy with prepared builders to modify its behavior.

## Examples

OkProxy provides sample application to show and probe its possibilities.

* SimpleServer
* SecuredServer
* SimpleProxy
* InterceptionProxy
