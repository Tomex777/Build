#!/usr/bin/env node
/**
 * Deterministic host-only Canvas scheduler contract.
 *
 * Runs with Node 22, without Android, to check JS behavior before the much
 * slower WebView emulator tests. It extracts the exact JavaScript embedded in
 * AnnieCanvasRuntime.kt, so it cannot accidentally test a different copy.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';

const root = path.resolve(import.meta.dirname, '..');
const runtimePath = path.join(root, 'app/src/main/java/com/tomex777/annie/AnnieCanvasRuntime.kt');
const kotlin = fs.readFileSync(runtimePath, 'utf8');
const embedded = kotlin.match(/CANVAS_LIFECYCLE_BOOTSTRAP\s*=\s*"""([\s\S]*?)"""\.trimIndent\(\)/);
assert.ok(embedded, 'The Canvas runtime must remain a pre-HTML JS bootstrap');
const runtime = embedded[1].trim();
assert.doesNotMatch(runtime, /addJavascriptInterface\s*\(/, 'No privileged bridge allowed');

function realm(initiallyPaused = false) {
  let clock = 0;
  let id = 0;
  const pending = new Map();
  const css = new Set();
  const media = [];
  const animations = [];
  const contextInstances = [];

  function schedule(callback, delay) {
    const key = ++id;
    pending.set(key, { callback, due: clock + delay });
    return key;
  }
  class HTMLMediaElement {
    constructor() {
      this.paused = true;
      this.isConnected = false;
      this.plays = 0;
      this.listeners = new Map();
    }
    play() {
      this.plays++;
      // Real Chromium play() can activate after an asynchronous Promise turn.
      return Promise.resolve().then(() => { this.paused = false; });
    }
    pause() { this.paused = true; }
    addEventListener(name, handler) {
      const listeners = this.listeners.get(name) || [];
      listeners.push(handler);
      this.listeners.set(name, listeners);
    }
    dispatch(name) {
      for (const handler of this.listeners.get(name) || []) handler();
    }
  }
  class AudioContext {
    constructor() {
      this.state = 'running';
      contextInstances.push(this);
    }
    suspend() { this.state = 'suspended'; return Promise.resolve(); }
    resume() { this.state = 'running'; return Promise.resolve(); }
    close() { this.state = 'closed'; return Promise.resolve(); }
  }
  const window = {
    __annieCanvasInitiallyPaused: initiallyPaused,
    setTimeout: (callback, milliseconds) => schedule(callback, milliseconds || 0),
    clearTimeout: (key) => pending.delete(key),
    requestAnimationFrame: (callback) => schedule(callback, 16),
    cancelAnimationFrame: (key) => pending.delete(key),
    HTMLMediaElement, AudioContext,
  };
  const document = {
    documentElement: { classList: {
      add: (key) => css.add(key), remove: (key) => css.delete(key),
    } },
    querySelectorAll: () => media,
    getAnimations: () => animations,
  };
  const performance = { now: () => clock };
  vm.runInNewContext(runtime, { window, document, performance, Promise, Map, Set, WeakSet, Reflect });
  function advance(milliseconds) {
    const end = clock + milliseconds;
    for (let limit = 0; limit < 20_000; limit++) {
      const ready = [...pending.entries()]
        .filter(([, item]) => item.due <= end)
        .sort((a, b) => a[1].due - b[1].due)[0];
      if (!ready) { clock = end; return; }
      const [key, item] = ready;
      pending.delete(key);
      clock = item.due;
      item.callback(clock);
    }
    throw Error('Scheduler runaway');
  }
  return { window, media, animations, contextInstances, css, advance,
    get pendingJobs() { return pending.size; } };
}

const a = realm();
const b = realm();
let ticksA = 0, ticksB = 0, framesA = 0;
let frameTime = 0;
a.window.setInterval(() => ticksA++, 25);
b.window.setInterval(() => ticksB++, 25);
a.window.requestAnimationFrame(function frame(t) {
  framesA++;
  frameTime = t;
  a.window.requestAnimationFrame(frame);
});
const sound = new a.window.HTMLMediaElement();
sound.play(); // Sound created but never inserted in the document.
const audio = new a.window.AudioContext();
const animation = { playState: 'running', pause() { this.playState = 'paused'; },
  play() { this.playState = 'running'; } };
a.animations.push(animation);

a.advance(200);
b.advance(200);
const before = { ticksA, ticksB, framesA, frameTime };
a.window.__annieCanvasHost.pause();
assert.equal(a.window.__annieCanvasHost.paused, true);
assert.ok(a.css.has('annie-host-paused'));
assert.equal(animation.playState, 'paused');
assert.equal(sound.paused, true, 'Detached audio element should pause');
assert.equal(audio.state, 'suspended');
await audio.resume();
assert.equal(audio.state, 'suspended', 'Canvas JS must not bypass host pause via AudioContext.resume');
const nextSound = new a.window.HTMLMediaElement();
nextSound.play();
assert.equal(nextSound.paused, true, 'Sound created during pause may not play');
const nextAudio = new a.window.AudioContext();
assert.equal(nextAudio.state, 'suspended', 'WebAudio created during pause must suspend');
await nextAudio.resume();
assert.equal(nextAudio.state, 'suspended', 'New AudioContext must remain paused');

let delayed = 0;
a.window.setTimeout(() => delayed++, 75);
a.advance(500);
b.advance(500);
assert.equal(ticksA, before.ticksA, 'Host paused intervals must freeze');
assert.equal(framesA, before.framesA, 'Host paused frames must freeze');
assert.equal(delayed, 0, 'Timeouts scheduled during pause must freeze');
assert.ok(ticksB > before.ticksB, 'Other Canvas timers must remain independent');

a.window.__annieCanvasHost.resume();
a.advance(80);
assert.ok(ticksA > before.ticksA);
assert.ok(framesA > before.framesA);
assert.equal(delayed, 1);
assert.ok(frameTime - before.frameTime <= 96, 'rAF must not jump by paused duration');
assert.equal(animation.playState, 'running');
assert.equal(audio.state, 'running');
assert.equal(nextAudio.state, 'running');
await Promise.resolve();
await Promise.resolve();
assert.equal(nextSound.paused, false);
assert.equal(sound.paused, false, 'In-flight play requests should resume');
assert.ok(!a.css.has('annie-host-paused'));

await audio.close();
assert.equal(audio.state, 'closed');
const c = realm(true);
let startup = 0;
c.window.setTimeout(() => startup++, 30);
c.advance(100);
assert.equal(startup, 0, 'Initially paused Canvas must suppress code-created timers');
c.window.__annieCanvasHost.resume();
c.advance(30);
assert.equal(startup, 1);

console.log('PASS Canvas host timer/rAF, CSS, detached media, WebAudio, startup pause, and isolation');
