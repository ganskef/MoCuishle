'use strict';

/**
 * Port for native messaging to the Mo Cuishle application.
 * 
 * Connecting to a native messaging host using chrome.runtime.connectNative() in
 * an extension's service worker will keep the service worker alive as long as 
 * the port is open.
 * 
 * https://developer.chrome.com/docs/extensions/whatsnew/#m100-native-msg-lifetime
 */
var proxyConnection = null;

function startupTab(tabs) {
  for (let tab of tabs) {
    if (tab.url.match(/http:\/\/localhost:9090\/.*/)) {
      chrome.tabs.update(tab.id, { active: true });
      return;
    }
  }
  chrome.tabs.create({ "url": "http://localhost:9090/browse" });
}

async function enableMoCuishle() {
  chrome.storage.session.set({ enabled: true });
  // Try to work, if Mo Cuishle application is already running or not.
  try {
    proxyConnection = await chrome.runtime.connectNative("de.ganskef.mocuishle");
  } catch (e) {
    console.warn(`startMoCuishle throws ${e}`);
  }
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
      console.warn(`tabs.query throws ${e}`);
    }
    chrome.action.setIcon({
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
    console.warn(`browsingData.removeCache throws ${e}`);
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
  chrome.storage.session.set({ enabled: false });
  try {
    proxyConnection.disconnect();
  } catch (e) {
    console.warn(`proxyConnection.disconnect throws ${e}`);
  }
  chrome.proxy.settings.set({ value: { mode: "direct" } });
  chrome.action.setIcon({
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
    console.warn(`tabs.query on exit throws ${e}`);
  }
}

/**
 * On clicked switch on/off the proxy usage.
 */
chrome.action.onClicked.addListener(() => {
  chrome.storage.session.get(["enabled"]).then((item) => {
    console.log(`action.onClicked while enabled=${item.enabled}`);
    if (item.enabled != true) {
      enableMoCuishle();
    } else {
      disableMoCuishle();
    }
  });
});

/**
 * Remove on uninstall, but fails to remove on closing Crone caused by the 
 * asynchrone design. The Mo Cuishle app is not forced to close like on Firefox.
 */
chrome.runtime.onSuspend.addListener(() => disableMoCuishle());

/**
 * On startup, connect to the Mo Cuishle app, starts if needed.
 */
chrome.runtime.onInstalled.addListener(() => enableMoCuishle());
chrome.runtime.onStartup.addListener(() => enableMoCuishle());
