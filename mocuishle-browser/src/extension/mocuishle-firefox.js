'use strict';

/**
 * Port for native messaging to the Mo Cuishle application.
 */
var proxyConnection = null;

function onErrorInfo(error) {
  console.info(`Ignored: ${error}`);
}

function startupTab(tabs) {
  for (let tab of tabs) {
    if (tab.url.match(/http:\/\/localhost:9090\/.*/)) {
      browser.tabs.update(tab.id, { active: true });
      return;
    }
  }
  browser.tabs.create({ "url": "http://localhost:9090/browse" });
}

function enableMoCuishle() {
  function onCleared() {
    browser.proxy.settings.set({
      value: {
        proxyType: "manual",
        http: "localhost:9090",
        httpProxyAll: true
      }
    });
    /** Open a MoCuishle tab if not exists */
    // Try to work, if none tabs authorization (optional_permission).
    browser.tabs.query({ url: "*://localhost/*" }).then(startupTab, onErrorInfo);
    browser.browserAction.setIcon({
      path: {
        16: "data/ic_launcher-16.png",
        32: "data/ic_launcher-32.png",
        48: "data/ic_launcher-48.png",
        64: "data/ic_launcher-64.png"
      }
    });
  }
  browser.browsingData.removeCache({}).then(onCleared, onErrorInfo);
}

function startMoCuishle() {
  try {
    proxyConnection = browser.runtime.connectNative("de.ganskef.mocuishle");
    setTimeout(function() {
      enableMoCuishle();
    }, 2000);
  } catch (e) {
    proxyConnection = {};
    // Try to work, if Mo Cuishle application is already running.
    onErrorInfo(e);
    enableMoCuishle();
  }
}

function cleanupTabs(tabs) {
  for (let tab of tabs) {
    if (tab.url.match(/http:\/\/localhost:9090\/.*/)) {
      browser.tabs.remove(tab.id);
    }
  }
}

function disableMoCuishle() {
  try {
    proxyConnection.disconnect();
  } catch (e) {
    // Ignore any error on disconnecting to continue switch.
    onErrorInfo(e);
  }
  proxyConnection = null;
  browser.proxy.settings.set({ value: { proxyType: "none" } });
  browser.browserAction.setIcon({
    path: {
      16: "data/ic_launcher-16b.png",
      32: "data/ic_launcher-32b.png",
      48: "data/ic_launcher-48b.png",
      64: "data/ic_launcher-64b.png"
    }
  });
  /** Remove MoCuishle tabs on exit */
  // Try to work, if none tabs authorization (optional_permission).
  browser.tabs.query({ url: "*://localhost/*" }).then(cleanupTabs, onErrorInfo);
}

/**
 * On clicked switch on/off the proxy usage.
 */
browser.browserAction.onClicked.addListener(() => {
  if (proxyConnection == null) {
    startMoCuishle();
  } else {
    disableMoCuishle();
  }
});

/**
 * On startup, connect to the app, starts if needed.
 */
startMoCuishle();
