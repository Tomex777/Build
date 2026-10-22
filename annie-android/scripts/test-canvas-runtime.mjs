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
const nativeHost = fs.readFileSync(
  path.join(root, 'app/src/main/java/com/tomex777/annie/AnnieCanvasMessage.kt'), 'utf8');
const codeLines = nativeHost.split('\n').filter((line) => !/^\s*(?:\/\/|\*)/.test(line)).join('\n');
assert.doesNotMatch(codeLines, /\b(?:WebView|web|view)\s*\.\s*pauseTimers\s*\(/,
  'No Canvas action may pause global WebView timers');
assert.match(nativeHost, /blockNetworkLoads\s*=\s*true/,
  'Canvas must still disable network traffic');
assert.match(nativeHost, /allowFileAccess\s*=\s*false/,
  'Canvas must still disable filesystem access');


function realm(
  initiallyPaused = false, asyncAudio = false, nativeIdle = false, cancellationRace = false,
) {
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
  class Element {
    animate() {
      return {
        playState: 'running',
        pause() { this.playState = 'paused'; },
        play() { this.playState = 'running'; },
      };
    }
  }
  class AudioContext {
    constructor() {
      this.state = 'running';
      contextInstances.push(this);
    }
    suspend() {
      if (asyncAudio) return Promise.resolve().then(() => { this.state = 'suspended'; });
      this.state = 'suspended';
      return Promise.resolve();
    }
    resume() {
      if (asyncAudio) return Promise.resolve().then(() => { this.state = 'running'; });
      this.state = 'running';
      return Promise.resolve();
    }
    close() { this.state = 'closed'; return Promise.resolve(); }
  }
  const window = {
    __annieCanvasInitiallyPaused: initiallyPaused,
    setTimeout: (callback, milliseconds) => schedule(callback, milliseconds || 0),
    clearTimeout: (key) => { if (!cancellationRace) pending.delete(key); },
    requestAnimationFrame: (callback) => schedule(callback, 16),
    cancelAnimationFrame: (key) => { if (!cancellationRace) pending.delete(key); },
    HTMLMediaElement, AudioContext, Element,
  };
  if (nativeIdle) {
    window.requestIdleCallback = (callback) =>
      schedule(() => callback({ didTimeout: false, timeRemaining: () => 5 }), 25);
    window.cancelIdleCallback = (key) => pending.delete(key);
  }
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
const cancellation = a.window.setTimeout(() => { throw Error('Canceled timeout executed'); }, 40);
a.window.clearTimeout(cancellation);
const frameCancellation = a.window.requestAnimationFrame(
  () => { throw Error('Canceled rAF executed'); });
a.window.cancelAnimationFrame(frameCancellation);
const idleCancellation = a.window.requestIdleCallback(
  () => { throw Error('Canceled idle work executed'); });
a.window.cancelIdleCallback(idleCancellation);
let intervalCalls = 0;
const canceledInterval = a.window.setInterval(() => {
  intervalCalls++;
  if (intervalCalls === 2) a.window.clearInterval(canceledInterval);
}, 10);
let forwardedArgs = 0;
a.window.setTimeout((x, y) => { forwardedArgs = x + y; }, 12, 3, 4);

const sound = new a.window.HTMLMediaElement();
sound.play(); // Sound created but never inserted in the document.
const audio = new a.window.AudioContext();
const animation = { playState: 'running', pause() { this.playState = 'paused'; },
  play() { this.playState = 'running'; } };
a.animations.push(animation);

a.advance(200);
b.advance(200);
const before = { ticksA, ticksB, framesA, frameTime };
assert.equal(intervalCalls, 2, 'Cleared interval should stop after its callback');
assert.equal(forwardedArgs, 7, 'Timeout argument forwarding must be preserved');
a.window.__annieCanvasHost.pause();
assert.equal(a.window.__annieCanvasHost.paused, true);
assert.ok(a.css.has('annie-host-paused'));
assert.equal(animation.playState, 'paused');
const lateAnimation = new a.window.Element().animate();
assert.equal(lateAnimation.playState, 'paused',
  'New Web Animations API motion must be held during host Pause');
assert.equal(sound.paused, true, 'Detached audio element should pause');
assert.equal(audio.state, 'suspended');
await audio.resume();
assert.equal(audio.state, 'suspended', 'Canvas JS must not bypass host pause via AudioContext.resume');
const nextSound = new a.window.HTMLMediaElement();
nextSound.play();
assert.equal(nextSound.paused, true, 'Sound created during pause may not play');
const explicitlyStopped = new a.window.HTMLMediaElement();
explicitlyStopped.play();
explicitlyStopped.pause(); // Game canceled this sound; host Resume must not play it.
const nextAudio = new a.window.AudioContext();
assert.equal(nextAudio.state, 'suspended', 'WebAudio created during pause must suspend');
await nextAudio.resume();
assert.equal(nextAudio.state, 'suspended', 'New AudioContext must remain paused');

let delayed = 0;
let idleExecutions = 0;
a.window.setTimeout(() => delayed++, 75);
a.window.requestIdleCallback((deadline) => {
  assert.equal(typeof deadline.timeRemaining, 'function');
  idleExecutions++;
});
a.advance(500);
b.advance(500);
assert.equal(ticksA, before.ticksA, 'Host paused intervals must freeze');
assert.equal(framesA, before.framesA, 'Host paused frames must freeze');
assert.equal(delayed, 0, 'Timeouts scheduled during pause must freeze');
assert.equal(idleExecutions, 0, 'Idle callbacks must not burn CPU during host Pause');
assert.ok(ticksB > before.ticksB, 'Other Canvas timers must remain independent');

a.window.__annieCanvasHost.resume();
a.advance(80);
assert.ok(ticksA > before.ticksA);
assert.ok(framesA > before.framesA);
assert.equal(delayed, 1);
assert.equal(idleExecutions, 1, 'Suspended idle callbacks resume exactly once');
assert.ok(frameTime - before.frameTime <= 96, 'rAF must not jump by paused duration');
assert.equal(animation.playState, 'running');
assert.equal(lateAnimation.playState, 'running');
assert.equal(audio.state, 'running');
assert.equal(nextAudio.state, 'running');
await Promise.resolve();
await Promise.resolve();
assert.equal(nextSound.paused, false);
assert.equal(sound.paused, false, 'In-flight play requests should resume');
assert.equal(explicitlyStopped.paused, true,
  'A game-issued pause should cancel deferred playback');
assert.ok(!a.css.has('annie-host-paused'));

await audio.close();
assert.equal(audio.state, 'closed');

// A fast host Resume may precede Chromium's native suspend() completion.
// The completion callback must restore a context that was running at Pause.
const raced = realm(false, true);
const racedAudio = new raced.window.AudioContext();
raced.window.__annieCanvasHost.pause();
raced.window.__annieCanvasHost.resume();
await Promise.resolve();
await Promise.resolve();
await Promise.resolve();
await Promise.resolve();
assert.equal(racedAudio.state, 'running',
  'Late native AudioContext.suspend resolution must not leave Canvas audio stuck');

// WebView versions with a native requestIdleCallback use cancelIdleCallback,
// rather than the timeout fallback. Both pathways must freeze correctly.
const idleWebView = realm(false, false, true);
let nativeIdleRuns = 0;
idleWebView.window.requestIdleCallback(() => nativeIdleRuns++);
idleWebView.window.__annieCanvasHost.pause();
idleWebView.advance(100);
assert.equal(nativeIdleRuns, 0);
idleWebView.window.__annieCanvasHost.resume();
idleWebView.advance(30);
assert.equal(nativeIdleRuns, 1, 'Native idle callback should resume once');

// Native cancel calls can race JS callbacks already enqueued in Chromium.
// The resumed generation must execute only once, not duplicate ticks/frames.
const racedCancel = realm(false, false, false, true);
let resumedCallbacks = 0;
racedCancel.window.setTimeout(() => resumedCallbacks++, 25);
racedCancel.window.requestAnimationFrame(() => resumedCallbacks++);
racedCancel.window.__annieCanvasHost.pause();
racedCancel.window.__annieCanvasHost.resume();
racedCancel.advance(30);
assert.equal(resumedCallbacks, 2,
  'Stale timer/frame deliveries must be rejected after rapid Pause/Resume');



const c = realm(true);
let startup = 0;
c.window.setTimeout(() => startup++, 30);
c.advance(100);
assert.equal(startup, 0, 'Initially paused Canvas must suppress code-created timers');
c.window.__annieCanvasHost.resume();
c.advance(30);
assert.equal(startup, 1);

console.log('PASS Canvas host timer/rAF, CSS, detached media, WebAudio, startup pause, and isolation');
