// Positive sample: uses every registry operation. Must compile with `tsc --noEmit`.
async function main(): Promise<void> {
  const info = await annie.android.deviceInfo();
  const platform: string = info.platform;
  const level: number = info.apiLevel;

  await annie.android.tts.speak("hello");
  await annie.android.tts.speak("hello", { language: "en-US", queue: "flush" });
  await annie.android.tts.status("utt-1");
  const stopped = await annie.android.tts.stop();
  const cancelled: number = stopped.cancelledUtterances;

  await annie.android.ocr.asset("assets/scan.png");
  await annie.android.stt.listen();
  await annie.android.stt.listen({ prompt: "Say something" });
  await annie.android.documents.pickText({ mimeType: "text/csv" });
  await annie.android.media.inspectAsset("assets/clip.mp3");
  await annie.android.notifications.post({ key: "k", title: "t", text: "x" });
  await annie.android.notifications.update({ key: "k", title: "t2", text: "x2" });
  await annie.android.notifications.cancel("k");

  const started = await annie.downloads.start({
    url: "https://example.com/a.bin",
    headers: { Accept: "*/*" },
    completionAction: { action: "downloaded", payload: { n: 1 } },
  });
  const status = await annie.downloads.status(started.id);
  const state: string = status.state;
  const items = await annie.downloads.list();
  const first: string | undefined = items[0]?.id;
  await annie.downloads.pause(started.id);
  await annie.downloads.resume(started.id);
  await annie.downloads.cancel(started.id);

  const handle = await annie.messages.send({ type: "progress", title: "Working", value: 0.1 });
  const sameHandle: AnnieMessageHandle = await annie.messages.update(handle.id, { type: "progress", title: "Working", value: 1 });
  const conversation: string = sameHandle.conversationId;

  try {
    await annie.android.tts.speak("x");
  } catch (error) {
    const e = error as AnnieError;
    const code: AnnieErrorCode = e.code;
    if (code === "RATE_LIMITED") console.log(e.retryAfterMs);
  }
  void [platform, level, cancelled, state, first, conversation];
}
void main();
declare const console: { log(...args: unknown[]): void };
