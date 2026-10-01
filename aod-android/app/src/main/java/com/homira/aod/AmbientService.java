package com.homira.aod;

import android.service.dreams.DreamService;

public final class AmbientService extends DreamService {
  private Surface surface;
  private Presentation controller;

  @Override
  public void onAttachedToWindow() {
    super.onAttachedToWindow();
    setInteractive(false);
    setFullscreen(true);
    setScreenBright(false);
    Store store = new Store(this);
    surface = new Surface(this, store.current(false));
    surface.ambient = true;
    surface.followSchedules = true;
    setContentView(surface);
    controller = new Presentation(this, getWindow(), surface);
  }

  @Override
  public void onDreamingStarted() {
    super.onDreamingStarted();
    if (surface != null) surface.resumeRuntime();
    if (controller != null) controller.start();
  }

  @Override
  public void onDreamingStopped() {
    if (surface != null) surface.pauseRuntime();
    if (controller != null) controller.stop();
    super.onDreamingStopped();
  }

  @Override
  public void onDetachedFromWindow() {
    if (surface != null) surface.pauseRuntime();
    if (controller != null) controller.stop();
    surface = null;
    controller = null;
    super.onDetachedFromWindow();
  }
}
