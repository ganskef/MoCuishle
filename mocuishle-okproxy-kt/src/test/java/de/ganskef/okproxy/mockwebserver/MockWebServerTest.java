/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ganskef.okproxy.mockwebserver;

import static org.assertj.core.api.Assertions.*;

import de.ganskef.okproxy.OkHttpServer;
import de.ganskef.okproxy.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * MockWebServerTest of <a href=
 * "https://square.github.io/okhttp/3.x/mockwebserver/okhttp3/mockwebserver/MockWebServer.html">MockWebServer</a>
 * version 3.14.9 adapted to verify {@link OkHttpServer}.
 */
final class MockWebServerTest {

  static {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  private OkHttpServer server;

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @BeforeEach
  void setUp() {
    server = new OkHttpServer();
  }

  @Test
  public void defaultResponse() {
    Response response = new Response();
    assertThat(headersToList(response)).containsExactly("Content-Length: 0");
    assertThat(response.getStatus()).isEqualTo("HTTP/1.1 200 OK");
  }

  @Test
  public void setResponseReason() {
    String[] reasons = {
      "OkHttpServer Response",
      "Informational",
      "OK",
      "Redirection",
      "Client Error",
      "Server Error",
      "Mock Response"
    };
    for (int i = 0; i < 600; i++) {
      Response response = new Response().setResponseCode(i);
      String expectedReason = reasons[i / 100];
      assertThat(response.getStatus()).isEqualTo(("HTTP/1.1 " + i + " " + expectedReason));
      assertThat(headersToList(response)).containsExactly("Content-Length: 0");
    }
  }

  @Test
  public void setStatusControlsWholeStatusLine() {
    Response response = new Response().setStatus("HTTP/1.1 202 That'll do pig");
    assertThat(headersToList(response)).containsExactly("Content-Length: 0");
    assertThat(response.getStatus()).isEqualTo("HTTP/1.1 202 That'll do pig");
  }

  @Test
  public void setBodyAdjustsHeaders() throws IOException {
    Response response = new Response().setBody("ABC");
    assertThat(headersToList(response)).containsExactly("Content-Length: 3");
    assertThat(response.getBody().readUtf8()).isEqualTo("ABC");
  }

  @Test
  public void mockResponseAddHeader() {
    Response response =
        new Response()
            .clearHeaders()
            .addHeader("Cookie: s=square")
            .addHeader("Cookie", "a=android");
    assertThat(headersToList(response)).containsExactly("Cookie: s=square", "Cookie: a=android");
  }

  @Test
  public void mockResponseSetHeader() {
    Response response =
        new Response()
            .clearHeaders()
            .addHeader("Cookie: s=square")
            .addHeader("Cookie: a=android")
            .addHeader("Cookies: delicious");
    response.setHeader("cookie", "r=robot");
    assertThat(headersToList(response)).containsExactly("Cookies: delicious", "cookie: r=robot");
  }

  @Test
  public void mockResponseSetHeaders() {
    Response response =
        new Response().clearHeaders().addHeader("Cookie: s=square").addHeader("Cookies: delicious");

    response.setHeaders(new Headers.Builder().add("Cookie", "a=android").build());

    assertThat(headersToList(response)).containsExactly("Cookie: a=android");
  }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void regularResponse() throws Exception {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void redirect() throws Exception {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void dispatchBlocksWaitingForEnqueue() throws Exception {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void nonHexadecimalChunkSize() throws Exception {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void responseTimeout() throws Exception {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void disconnectAtStart() throws Exception {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void throttleRequest() throws Exception {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void throttleResponse() throws Exception {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void delayResponse() throws IOException {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void disconnectRequestHalfway() throws Exception {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void disconnectResponseHalfway() throws IOException {
  // }

  private List<String> headersToList(Response response) {
    Headers headers = response.getHeaders();
    int size = headers.size();
    List<String> headerList = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      headerList.add(headers.name(i) + ": " + headers.value(i));
    }
    return headerList;
  }

  @Test
  public void shutdownWithoutStart() throws IOException {
    OkHttpServer server = new OkHttpServer();
    server.shutdown();
  }

  // close isn't implemented in OkHttpServer
  // @Test public void closeViaClosable() throws IOException {
  // }

  @Test
  public void shutdownWithoutEnqueue() throws IOException {
    OkHttpServer server = new OkHttpServer();
    server.start();
    server.shutdown();
  }

  @Test
  public void portImplicitlyStarts() throws IOException {
    assertThat(server.getPort() > 0).isTrue();
  }

  @Test
  public void hostnameImplicitlyStarts() throws IOException {
    assertThat(server.getHostName()).isNotNull();
  }

  @Test
  public void toProxyAddressImplicitlyStarts() throws IOException {
    assertThat(server.toProxyAddress()).isNotNull();
  }

  @Test
  public void differentInstancesGetDifferentPorts() throws IOException {
    OkHttpServer other = new OkHttpServer();
    assertThat(other.getPort()).isNotEqualTo(server.getPort());
    other.shutdown();
  }

  // apply isn't implemented in OkHttpServer
  // @Test public void statementStartsAndStops() throws Throwable {
  // }

  // OkHttpServer always responds to a request
  // @Test public void shutdownWhileBlockedDispatching() throws Exception {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void requestUrlReconstructed() throws Exception {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void shutdownServerAfterRequest() throws Exception {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void http100Continue() throws Exception {
  // }

  // // OkHttpServer doesn't implement HTTP 2.0
  // @Test public void testH2PriorKnowledgeServerFallback() {
  // }

  // OkHttpServer doesn't implement HTTP 2.0
  // @Test public void testH2PriorKnowledgeServerDuplicates() {
  // }

  // OkHttpServer doesn't implement HTTP 2.0
  // @Test public void testMockWebServerH2PriorKnowledgeProtocol() {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void https() throws Exception {
  // }

  // enqueue isn't implemented in OkHttpServer
  // @Test public void httpsWithClientAuth() throws Exception {
  // }
}
