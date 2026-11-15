// Trap 10 (Buzz): runs in the page's own context. Sites can no longer ask
// for notification permission, so there is nothing to buzz you back with.
(() => {
  try {
    const denied = () => Promise.resolve("denied");
    if (window.Notification) {
      Object.defineProperty(Notification, "permission", { get: () => "denied" });
      Notification.requestPermission = (cb) => { if (typeof cb === "function") cb("denied"); return denied(); };
    }
    if (navigator.vibrate) navigator.vibrate = () => false;
  } catch (e) { /* page locked the property; ignore */ }
})();
