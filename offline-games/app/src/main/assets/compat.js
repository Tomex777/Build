/* Offline WebView compatibility for Android 8+ (including older system WebViews).
   Keep this ES5 syntax: it must run before every bundled game engine. */
(function () {
  'use strict';
  // Keep JavaScript launch failures inspectable in the offline emulator tests.
  window.__nightGameErrors = [];
  window.addEventListener('error', function (event) {
    window.__nightGameErrors.push({
      message: event.message || 'script error',
      file: event.filename || '',
      line: event.lineno || 0
    });
  });
  window.addEventListener('unhandledrejection', function (event) {
    window.__nightGameErrors.push({ message: String(event.reason || 'unhandled rejection') });
  });
  function replaceChildren() {
    while (this.firstChild) this.removeChild(this.firstChild);
    for (var i = 0; i < arguments.length; i++) {
      var item = arguments[i];
      this.appendChild(item instanceof Node ? item : document.createTextNode(String(item)));
    }
  }
  [Element.prototype, Document.prototype, DocumentFragment.prototype].forEach(function (p) {
    if (!p.replaceChildren) Object.defineProperty(p, 'replaceChildren', {
      configurable: true, writable: true, value: replaceChildren
    });
  });
  // Android 8's bundled WebView may predate ES2019 Array.flat/flatMap.
  if (!Array.prototype.flat) Object.defineProperty(Array.prototype, 'flat', {
    configurable: true, writable: true, value: function (depth) {
      var result = [];
      var limit = depth === undefined ? 1 : Math.max(0, Number(depth) || 0);
      function append(items, level) {
        for (var i = 0; i < items.length; i++) {
          if (!(i in items)) continue;
          var item = items[i];
          if (Array.isArray(item) && level > 0) append(item, level - 1);
          else result.push(item);
        }
      }
      append(this, limit);
      return result;
    }
  });
  if (!Array.prototype.flatMap) Object.defineProperty(Array.prototype, 'flatMap', {
    configurable: true, writable: true, value: function (mapper, thisArg) {
      if (typeof mapper !== 'function') throw new TypeError('flatMap mapper required');
      var values = [];
      for (var i = 0; i < this.length; i++) {
        if (i in this) values.push(mapper.call(thisArg, this[i], i, this));
      }
      return values.flat(1);
    }
  });
  if (!Array.prototype.at) Object.defineProperty(Array.prototype, 'at', {
    configurable: true, writable: true, value: function (index) {
      var i = Math.trunc(Number(index) || 0);
      return this[i < 0 ? this.length + i : i];
    }
  });
  if (!String.prototype.replaceAll) Object.defineProperty(String.prototype, 'replaceAll', {
    configurable: true, writable: true, value: function (search, replacement) {
      if (search instanceof RegExp) {
        if (!search.global) throw new TypeError('replaceAll requires a global RegExp');
        return this.replace(search, replacement);
      }
      var term = String(search);
      if (term === '') return String(this).split('').join(String(replacement)).replace(/^|$/g, String(replacement));
      return String(this).split(term).join(String(replacement));
    }
  });
}());
