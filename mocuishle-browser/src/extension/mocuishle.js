'use strict';

/**
 * Port for native messaging to the Mo Cuishle application.
 */
var proxyConnection = null;

var validateTabId = null;

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
    try {
      chrome.tabs.query({ url: "*://localhost/*" }, startupTab);
    } catch (e) {
      // Try to work, if none tabs authorization (optional_permission).
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
  validateTabId = null;
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
  validateTabId = null;
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
  try {
    chrome.tabs.query({ url: "*://localhost/*" }, cleanupTabs);
  } catch (e) {
    // Try to work, if none tabs authorization (optional_permission).
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

chrome.runtime.onSuspend.addListener(() => {
  if (proxyConnection != null) {
    disableMoCuishle();
  }
});

/**
 * The XMLHttpRequest object to load the urls for validate-cache-iterate.
 */
var req;

var validateTabId;

/**
 * Handles parsing the feed data we got back from XMLHttpRequest.
 */
function validateResponse() {
  var cacheUrl = req.responseText;
  if (!cacheUrl || cacheUrl === "http://localhost:9090/done") {
    return;
  }
  setTimeout(function() {
    chrome.tabs.query({ currentWindow: true, active: true }, function(tabs) {
      if (tabs[0].url === cacheUrl) {
        validateNext();
      }
    });
  }, 10000);
  chrome.tabs.update({ "url": cacheUrl });
}

function validateNext() {
  req = new XMLHttpRequest();
  req.onload = validateResponse;
  req.onerror = onErrorInfo;
  req.open('GET', 'http://localhost:9090/validate-cache-iterate', true);
  req.send(null);
}

function validateCache() {
  function onCleared() {
    chrome.tabs.update({
      "url": 'http://localhost:9090/cacheonly'
    }, function(tab) {
      validateTabId = tab.id;
    });
  }
  try {
    chrome.browsingData.removeCache({}, onCleared);
  } catch (e) {
    onErrorInfo(e);
  }
}

/**
 * Each time a tab is updated, check the status and validate next if needed.
 */
chrome.tabs.onUpdated.addListener((id, changeInfo, tab) => {
  if (tab.id == validateTabId && changeInfo.status == 'complete') {
    validateNext();
  }
});

/**
 * Validate cache on entering URL (ugly workaround before implementing a popup).
 */
chrome.webNavigation.onCompleted.addListener(validateCache, { url: [{ urlMatches: 'http://localhost:9090/validate' }] });

/**
 * On startup, connect to the app, starts if needed.
 */
startMoCuishle();
