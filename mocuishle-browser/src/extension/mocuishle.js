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
      chrome.tabs.update(tab.id, { active: true });
      return;
    }
  }
  chrome.tabs.create({ "url": "http://localhost:9090/browse" });
}

function enableMoCuishle() {
  function onCleared() {
    chrome.proxy.settings.set({
      value: {
        mode: "fixed_servers",
        rules: { singleProxy: { host: "localhost", "port": 9090 } }
      }
    });
    /** Open a MoCuishle tab if not exists */
    // Try to work, if none tabs authorization (optional_permission).
    try {
      chrome.tabs.query({ url: "*://localhost/*" }, startupTab);
    } catch (e) {
      onErrorInfo(e);
    }
    chrome.browserAction.setIcon({
      path: {
        16: "data/ic_launcher-16.png",
        32: "data/ic_launcher-32.png",
        48: "data/ic_launcher-48.png",
        64: "data/ic_launcher-64.png"
      }
    });
  }
  try {
    chrome.browsingData.removeCache({}, onCleared);
  } catch (e) {
    onErrorInfo(e);
  }
}

function startMoCuishle() {
  try {
    proxyConnection = chrome.runtime.connectNative("de.ganskef.mocuishle");
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
      chrome.tabs.remove(tab.id);
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
  chrome.proxy.settings.set({ value: { mode: "direct" } });
  chrome.browserAction.setIcon({
    path: {
      16: "data/ic_launcher-16b.png",
      32: "data/ic_launcher-32b.png",
      48: "data/ic_launcher-48b.png",
      64: "data/ic_launcher-64b.png"
    }
  });
  /** Remove MoCuishle tabs on exit */
  // Try to work, if none tabs authorization (optional_permission).
  try {
    chrome.tabs.query({ url: "*://localhost/*" }, cleanupTabs);
  } catch (e) {
    onErrorInfo(e);
  }
}

/**
 * On clicked switch on/off the proxy usage.
 */
chrome.browserAction.onClicked.addListener(() => {
  if (proxyConnection == null) {
    startMoCuishle();
  } else {
    disableMoCuishle();
  }
});

chrome.windows.onRemoved.addListener(function(windowId){
  if (proxyConnection != null) {
    disableMoCuishle();
  }
});

chrome.runtime.onSuspend.addListener(() => {
  if (proxyConnection != null) {
    disableMoCuishle();
  }
});

/**
 * On startup, connect to the app, starts if needed.
 */
startMoCuishle();
