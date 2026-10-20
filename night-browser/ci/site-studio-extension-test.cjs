/* Execute with: node --test night-browser/ci/site-studio-extension-test.cjs
 *
 * This tests the actual packaged content script in a mocked DOM. It does not
 * assert emulator compatibility or GeckoView extension installation.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const contentScript = fs.readFileSync(
  path.join(__dirname, '../app/src/main/assets/site_studio/content.js'), 'utf8'
);
const manifest = JSON.parse(fs.readFileSync(
  path.join(__dirname, '../app/src/main/assets/site_studio/manifest.json'), 'utf8'
));

async function runCase({
  pageOrigin = 'https://example.com',
  pageProtocol = 'https:',
  pageHost = 'example.com',
  topLevel = true,
  reply = { origin: 'https://example.com', css: '', javascript: '' },
  reject = false,
  beforeReply = null
} = {}) {
  const appended = [];
  const messages = [];
  let resolveMessage;
  let rejectMessage;
  const location = { origin: pageOrigin, protocol: pageProtocol, host: pageHost };
  const document = {
    createElement: tagName => ({
      tagName,
      attributes: {},
      textContent: '',
      removed: false,
      setAttribute(key, value) { this.attributes[key] = value; },
      remove() { this.removed = true; }
    }),
    head: {
      appendChild(element) { appended.push(element); }
    }
  };
  document.documentElement = document.head;
  const sendNativeMessage = (appName, message) => {
    messages.push({ appName, message });
    return new Promise((resolve, rejectMessageFn) => {
      resolveMessage = resolve;
      rejectMessage = rejectMessageFn;
    });
  };
  const top = {};
  const window = topLevel ? top : { top };
  if (topLevel) window.top = window;

  vm.runInNewContext(contentScript, {
    window, location, document, browser: { runtime: { sendNativeMessage } }
  }, { filename: 'content.js', timeout: 1_000 });

  if (messages.length > 0) {
    if (beforeReply) beforeReply(location);
    if (reject) rejectMessage(new Error('unavailable'));
    else resolveMessage(reply);
  }
  await Promise.resolve();
  await Promise.resolve();
  return { appended, messages };
}

test('built-in extension manifest has the necessary restricted permissions', () => {
  assert.equal(manifest.manifest_version, 2);
  const permissions = new Set(manifest.permissions);
  for (const name of ['nativeMessaging', 'nativeMessagingFromContent', 'geckoViewAddons']) {
    assert.ok(permissions.has(name));
  }
  assert.equal(manifest.content_scripts[0].all_frames, false);
  assert.ok(manifest.content_scripts[0].matches.includes('https://*/*'));
  assert.ok(manifest.content_scripts[0].matches.includes('http://*/*'));
});

test('native messaging identifier conforms to documented GeckoView app-name syntax', async () => {
  const { messages } = await runCase();
  assert.equal(messages.length, 1);
  assert.match(messages[0].appName, /^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)*$/);
  assert.equal(messages[0].appName, 'night.site_studio');
  assert.equal(messages[0].message.type, 'rulesForPage');
});

test('site remains untouched when site modifications are disabled by default', async () => {
  const { appended } = await runCase();
  assert.equal(appended.length, 0);
});

test('site CSS only applies to a matching exact origin', async () => {
  const css = 'body { background: #222; }';
  const same = await runCase({ reply: { origin: 'https://example.com', css, javascript: '' } });
  assert.equal(same.appended.length, 1);
  assert.equal(same.appended[0].tagName, 'style');
  assert.equal(same.appended[0].textContent, css);
  const different = await runCase({ reply: {
    origin: 'https://sub.example.com', css, javascript: ''
  } });
  assert.equal(different.appended.length, 0);
});

test('same host over HTTP is a distinct origin', async () => {
  const { appended } = await runCase({
    pageOrigin: 'http://example.com', pageProtocol: 'http:',
    reply: { origin: 'https://example.com', css: 'body { opacity:0 }', javascript: '' }
  });
  assert.equal(appended.length, 0);
});

test('a redirect during native-message round trip cannot inherit earlier rule', async () => {
  const { appended } = await runCase({
    reply: { origin: 'https://example.com', css: 'body { color:red }', javascript: '' },
    beforeReply: loc => {
      loc.origin = 'https://different.test';
      loc.host = 'different.test';
    }
  });
  assert.equal(appended.length, 0);
});

test('javascript inserts a temporary page element instead of eval in extension context', async () => {
  const code = 'document.title="modified"';
  const { appended } = await runCase({
    reply: { origin: 'https://example.com', css: '', javascript: code }
  });
  assert.equal(appended.length, 1);
  assert.equal(appended[0].tagName, 'script');
  assert.equal(appended[0].textContent, code);
  assert.equal(appended[0].removed, true);
});

test('frames, unsupported protocols, and denied native messaging stay inert', async () => {
  const iframe = await runCase({ topLevel: false });
  assert.equal(iframe.messages.length, 0);
  const internal = await runCase({ pageProtocol: 'file:' });
  assert.equal(internal.messages.length, 0);
  const denied = await runCase({ reject: true });
  assert.equal(denied.appended.length, 0);
});

test('oversized style and script payloads do not get injected', async () => {
  const { appended } = await runCase({
    reply: { origin: 'https://example.com', css: 'x'.repeat(32769),
      javascript: 'y'.repeat(16385) }
  });
  assert.equal(appended.length, 0);
});
