/* NIGHT Site Studio content bridge (shipped only inside this browser).
 * This script does NOTHING to a website until a native exact-origin,
 * explicitly enabled site rule is returned. It does not collect page text,
 * cookies, credentials, searches or visit history.
 */
(() => {
  "use strict";
  if (window !== window.top || !/^https?:$/.test(location.protocol)) return;

  browser.runtime.sendNativeMessage("night.site_studio", {
    type: "rulesForPage"
  }).then((rule) => {
    if (!rule || typeof rule !== "object") return;
    // A second origin check: Java validates the trusted Gecko sender URL;
    // this protects against a document redirect while the response is pending.
    // Gecko's canonical location.origin includes the effective scheme/host/port.
    // Never fall back to a reconstructed host: an inconsistent origin must fail
    // closed, even if the navigation changed during the async native round trip.
    if (rule.origin !== location.origin) return;
    if (typeof rule.css === "string" && rule.css.length <= 32768 && rule.css) {
      const style = document.createElement("style");
      style.setAttribute("data-night.site_studio", "user-style");
      style.textContent = rule.css;
      (document.head || document.documentElement).appendChild(style);
    }
    if (typeof rule.javascript === "string" &&
        rule.javascript.length <= 16384 && rule.javascript) {
      // This is *user-authored* code and requires an explicit per-site
      // security opt-in in the native editor. It executes only when the
      // site's Content Security Policy allows inline page scripts.
      // Never bypass the site's CSP and never eval in extension context.
      const script = document.createElement("script");
      script.setAttribute("data-night.site_studio", "user-script");
      script.textContent = rule.javascript;
      (document.head || document.documentElement).appendChild(script);
      script.remove();
    }
  }).catch(() => {
    // Extension unavailable or disabled: leave the page untouched.
  });
})();
