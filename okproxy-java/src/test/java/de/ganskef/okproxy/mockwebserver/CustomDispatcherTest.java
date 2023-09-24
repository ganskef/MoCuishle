/*
 * Copyright (C) 2012 Google Inc.
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

import de.ganskef.okproxy.Dispatcher;
import de.ganskef.okproxy.OkHttpServer;
import de.ganskef.okproxy.Request;
import de.ganskef.okproxy.Response;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomDispatcherTest {

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
  void simpleDispatch() throws Exception {
    server.start();
    final List<Request> requestsMade = new ArrayList<>();
    final Dispatcher dispatcher =
        new Dispatcher() {
          @Override
          public Response dispatch(Request request) {
            requestsMade.add(request);
            return new Response();
          }
        };
    assertThat(requestsMade.size()).isEqualTo(0);
    server.setDispatcher(dispatcher);
    final URL url = server.url("/").url();
    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.getResponseCode(); // Force the connection to hit the "server".
    // Make sure our dispatcher got the request.
    assertThat(requestsMade.size()).isEqualTo(1);
  }

  @Test
  void outOfOrderResponses() throws Exception {
    AtomicInteger firstResponseCode = new AtomicInteger();
    AtomicInteger secondResponseCode = new AtomicInteger();
    server.start();
    final String secondRequest = "/bar";
    final String firstRequest = "/foo";
    final CountDownLatch latch = new CountDownLatch(1);
    final Dispatcher dispatcher =
        new Dispatcher() {
          @Override
          public Response dispatch(Request request) throws InterruptedException {
            if (request.getPath().equals(firstRequest)) {
              latch.await();
            }
            return new Response();
          }
        };
    server.setDispatcher(dispatcher);
    final Thread startsFirst = buildRequestThread(firstRequest, firstResponseCode);
    startsFirst.start();
    final Thread endsFirst = buildRequestThread(secondRequest, secondResponseCode);
    endsFirst.start();
    endsFirst.join();
    // First response is still waiting.
    assertThat(firstResponseCode.get()).isEqualTo(0);
    // Second response is done.
    assertThat(secondResponseCode.get()).isEqualTo(200);

    latch.countDown();
    startsFirst.join();
    // And now it's done!
    assertThat(firstResponseCode.get()).isEqualTo(200);
    // (Still done).
    assertThat(secondResponseCode.get()).isEqualTo(200);
  }

  private Thread buildRequestThread(String path, AtomicInteger responseCode) {
    return new Thread(
        () -> {
          URL url = server.url(path).url();
          HttpURLConnection conn;
          try {
            conn = (HttpURLConnection) url.openConnection();
            responseCode.set(conn.getResponseCode()); // Force the connection to hit the "server".
          } catch (IOException ignored) {
          }
        });
  }
}
