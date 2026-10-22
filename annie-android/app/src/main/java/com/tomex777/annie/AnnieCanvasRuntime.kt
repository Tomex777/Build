package com.tomex777.annie

/**
 * Runs before any Canvas HTML or user JavaScript. All hooks stay inside this
 * Canvas WebView. No Android bridge and no global WebView.pauseTimers().
 *
 * Timers and frames created while paused remain queued; intervals do not
 * catch up missed ticks. rAF receives a virtual timestamp without the pause
 * duration so an animation doesn't jump on resume.
 */
internal val CANVAS_LIFECYCLE_BOOTSTRAP = """
(function () {
  'use strict';
  var originalTimeout = window.setTimeout.bind(window);
  var originalClearTimeout = window.clearTimeout.bind(window);
  var originalFrame = window.requestAnimationFrame.bind(window);
  var originalCancelFrame = window.cancelAnimationFrame.bind(window);
  var jobs = new Map();
  var nextId = 1000000000;
  var paused = window.__annieCanvasInitiallyPaused === true;
  var pausedAt = paused ? performance.now() : 0;
  var frameOffset = 0;
  var suspendedMedia = [];
  var suspendedAnimations = [];
  var deferredMedia = new Set();
  var trackedMedia = new Set();
  var observedMedia = new WeakSet();
  var audioContexts = new Set();
  var suspendedAudioContexts = [];
  var deferredAudioContexts = new Set();

  // Media created while paused must not bypass the host-level suspension.
  if (window.HTMLMediaElement && window.HTMLMediaElement.prototype) {
    var nativePlay = window.HTMLMediaElement.prototype.play;
    var nativePause = window.HTMLMediaElement.prototype.pause;
    window.HTMLMediaElement.prototype.play = function () {
      trackedMedia.add(this);
      if (!observedMedia.has(this)) {
        observedMedia.add(this);
        var media = this;
        this.addEventListener('ended', function () { trackedMedia.delete(media); });
        this.addEventListener('emptied', function () { trackedMedia.delete(media); });
      }
      if (paused) {
        deferredMedia.add(this);
        return Promise.resolve();
      }
      var media = this;
      var result = nativePlay.apply(this, arguments);
      if (result && result.then) {
        // A play() request can resolve after the user has already hit Pause.
        result.then(function () {
          if (paused && !media.paused) {
            deferredMedia.add(media);
            nativePause.call(media);
          }
        }, function () {
          // Rejected autoplay attempts must not retain unused media forever.
          if (media.paused) trackedMedia.delete(media);
        });
      }
      return result;
    };
    window.HTMLMediaElement.prototype.pause = function () {
      // User-initiated pause/cancel should not be undone by host Resume.
      trackedMedia.delete(this);
      deferredMedia.delete(this);
      suspendedMedia = suspendedMedia.filter(function (media) { return media !== this; }, this);
      return nativePause.apply(this, arguments);
    };
  }

  function suspendContext(context) {
    var action = context.suspend();
    if (action && action.then) {
      // If native suspension finishes *after* the host has resumed, restore
      // this previously-running context instead of leaving audio stuck.
      action.then(function () {
        if (!paused && context.state === 'suspended') {
          var restored = context.resume();
          if (restored && restored.catch) restored.catch(function () {});
        }
      }, function () {});
    }
  }

  function trackAudioConstructor(name) {
    var NativeAudio = window[name];
    if (typeof NativeAudio !== 'function') return;
    function TrackedAudioContext() {
      var context = Reflect.construct(NativeAudio, arguments, NativeAudio);
      audioContexts.add(context);
      try {
        var originalResume = context.resume.bind(context);
        context.resume = function () {
          if (paused) {
            deferredAudioContexts.add(context);
            return Promise.resolve();
          }
          return originalResume();
        };
      } catch (_) {
        // Some system WebViews expose a non-writable instance resume method.
      }
      try {
        var originalClose = context.close.bind(context);
        context.close = function () {
          audioContexts.delete(context);
          return originalClose();
        };
      } catch (_) {
        // A read-only close() must not prevent construction of the Canvas sound.
      }
      if (paused && context.state === 'running') {
        suspendedAudioContexts.push(context);
        suspendContext(context);
      }
      return context;
    }
    try {
      TrackedAudioContext.prototype = NativeAudio.prototype;
      Object.setPrototypeOf(TrackedAudioContext, NativeAudio);
      window[name] = TrackedAudioContext;
    } catch (_) {
      // Keep the rest of the Canvas scheduler alive on older WebViews whose
      // AudioContext constructor cannot be replaced.
    }
  }
  trackAudioConstructor('AudioContext');
  trackAudioConstructor('webkitAudioContext');

  function invoke(job, timestamp) {
    if (!jobs.has(job.id) || paused) return;
    if (job.kind === 'frame') {
      jobs.delete(job.id);
      job.callback(timestamp - frameOffset);
      return;
    }
    if (job.kind === 'timeout') jobs.delete(job.id);
    else { job.remaining = job.delay; schedule(job); }
    job.callback.apply(window, job.args);
  }

  function schedule(job) {
    if (paused) return;
    if (job.kind === 'frame') {
      job.nativeId = originalFrame(function (timestamp) { invoke(job, timestamp); });
    } else {
      job.due = performance.now() + job.remaining;
      job.nativeId = originalTimeout(function () { invoke(job, performance.now()); }, job.remaining);
    }
  }

  function addJob(kind, callback, delay, args) {
    if (typeof callback !== 'function') return 0;
    var interval = Math.max(0, Number(delay) || 0);
    var job = {
      id: nextId++, kind: kind, callback: callback, args: args || [],
      delay: interval, remaining: interval, nativeId: null, due: 0
    };
    jobs.set(job.id, job);
    schedule(job);
    return job.id;
  }

  function clearJob(id) {
    var job = jobs.get(Number(id));
    if (!job) return;
    if (job.nativeId !== null) {
      if (job.kind === 'frame') originalCancelFrame(job.nativeId);
      else originalClearTimeout(job.nativeId);
    }
    jobs.delete(job.id);
  }

  window.setTimeout = function (callback, delay) {
    return addJob('timeout', callback, delay, Array.prototype.slice.call(arguments, 2));
  };
  window.clearTimeout = clearJob;
  window.setInterval = function (callback, delay) {
    return addJob('interval', callback, Math.max(1, Number(delay) || 0),
      Array.prototype.slice.call(arguments, 2));
  };
  window.clearInterval = clearJob;
  window.requestAnimationFrame = function (callback) {
    return addJob('frame', callback, 0, []);
  };
  window.cancelAnimationFrame = clearJob;

  function pause() {
    if (paused) return;
    paused = true;
    pausedAt = performance.now();
    document.documentElement.classList.add('annie-host-paused');
    jobs.forEach(function (job) {
      if (job.nativeId === null) return;
      if (job.kind === 'frame') originalCancelFrame(job.nativeId);
      else {
        originalClearTimeout(job.nativeId);
        job.remaining = Math.max(0, job.due - pausedAt);
      }
      job.nativeId = null;
    });
    suspendedMedia = [];
    // new Audio('data:...') sounds may never appear in the DOM, yet must stop.
    var allMedia = new Set(trackedMedia);
    document.querySelectorAll('audio,video').forEach(function (media) { allMedia.add(media); });
    allMedia.forEach(function (media) {
      if (!media.paused) {
        suspendedMedia.push(media);
        if (nativePause) nativePause.call(media);
        else media.pause();
      }
    });
    suspendedAudioContexts = [];
    audioContexts.forEach(function (context) {
      if (context.state === 'running') {
        suspendedAudioContexts.push(context);
        suspendContext(context);
      }
    });
    suspendedAnimations = [];
    if (document.getAnimations) document.getAnimations().forEach(function (animation) {
      if (animation.playState === 'running') {
        animation.pause();
        suspendedAnimations.push(animation);
      }
    });
  }

  function resume() {
    if (!paused) return;
    frameOffset += performance.now() - pausedAt;
    paused = false;
    document.documentElement.classList.remove('annie-host-paused');
    jobs.forEach(schedule);
    suspendedAnimations.forEach(function (animation) {
      if (animation.playState === 'paused') animation.play();
    });
    suspendedAnimations = [];
    var shouldResume = new Set(suspendedAudioContexts);
    deferredAudioContexts.forEach(function (context) { shouldResume.add(context); });
    suspendedAudioContexts = [];
    deferredAudioContexts.clear();
    shouldResume.forEach(function (context) {
      if (context.state === 'suspended') {
        var action = context.resume();
        if (action && action.catch) action.catch(function () {});
      }
    });
    deferredMedia.forEach(function (media) { suspendedMedia.push(media); });
    deferredMedia.clear();
    suspendedMedia.forEach(function (media) {
      if (media.paused) {
        var promise = media.play();
        if (promise && promise.catch) promise.catch(function () {});
      }
    });
    suspendedMedia = [];
  }

  // Deliberately no JavascriptInterface; these methods are confined to the
  // sandboxed page and invoked on its own WebView from the native host.
  Object.defineProperty(window, '__annieCanvasHost', {
    value: Object.freeze({
      pause: pause, resume: resume,
      get paused() { return paused; },
      get pendingJobs() { return jobs.size; }
    }),
    configurable: false
  });
  if (paused) document.documentElement.classList.add('annie-host-paused');
})();
""".trimIndent()
