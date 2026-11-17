/*
 * TV Device Bridge — content script (runs in every top-level page at document_start).
 *
 * 1) Desktop fingerprint: finishes what the desktop User-Agent starts, so sites that
 *    sniff navigator.platform / maxTouchPoints still serve the desktop version.
 * 2) window.tvDevice: a small API for web pages to control the device volume:
 *
 *      await tvDevice.volumeUp();        // -> {level, max, percent, muted}
 *      await tvDevice.volumeDown();
 *      await tvDevice.toggleMute();
 *      await tvDevice.setVolume(40);     // percent 0..100
 *      await tvDevice.getVolume();
 *
 *    Also available through postMessage for iframes / CSP-strict pages:
 *      window.postMessage({ tvDevice: "volumeUp" }, "*")
 *      -> reply: { tvDeviceResult: {...}, request: "volumeUp" }
 */
(() => {
  "use strict";
  const NATIVE_APP = "tvbridge";
  const page = window.wrappedJSObject;

  // ---------- 1) Desktop fingerprint ----------
  try {
    const navProto = page.Navigator.prototype;
    const spoof = (name, value) => {
      Object.defineProperty(navProto, name, {
        get: exportFunction(() => value, window),
        configurable: true,
        enumerable: true,
      });
    };
    spoof("platform", "Win32");
    spoof("oscpu", "Windows NT 10.0; Win64; x64");
    spoof("maxTouchPoints", 0);
  } catch (e) {
    /* page may be a privileged/about: document; ignore */
  }

  // ---------- 2) Device API ----------
  const ACTIONS = ["volumeUp", "volumeDown", "toggleMute", "setVolume", "getVolume"];

  function callNative(action, value) {
    return browser.runtime.sendNativeMessage(NATIVE_APP, { action, value });
  }

  try {
    const api = new page.Object();
    for (const action of ACTIONS) {
      exportFunction(
        function (value) {
          return new page.Promise(
            exportFunction((resolve, reject) => {
              callNative(action, value).then(
                (res) => resolve(cloneInto(res, page)),
                (err) => reject(new page.Error(String(err)))
              );
            }, page)
          );
        },
        api,
        { defineAs: action }
      );
    }
    Object.defineProperty(page, "tvDevice", {
      value: api,
      writable: false,
      configurable: false,
      enumerable: false,
    });
  } catch (e) {
    /* ignore */
  }

  window.addEventListener("message", (event) => {
    const data = event.data;
    if (!data || typeof data.tvDevice !== "string" || !ACTIONS.includes(data.tvDevice)) return;
    callNative(data.tvDevice, data.value).then(
      (res) => event.source && event.source.postMessage({ tvDeviceResult: res, request: data.tvDevice }, "*"),
      (err) => event.source && event.source.postMessage({ tvDeviceError: String(err), request: data.tvDevice }, "*")
    );
  });
})();
