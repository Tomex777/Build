// Negative sample: every line marked @ts-expect-error must be a compile error. An unused directive is itself an error.
async function bad(): Promise<void> {
  // @ts-expect-error text must be a string
  await annie.android.tts.speak(42);
  // @ts-expect-error queue must be "add" | "flush"
  await annie.android.tts.speak("x", { queue: "insert" });
  // @ts-expect-error unknown option key
  await annie.android.tts.speak("x", { volume: 1 });
  // @ts-expect-error title is required
  await annie.android.notifications.post({ text: "x" });
  // @ts-expect-error url is required
  await annie.downloads.start({ title: "x" });
  // @ts-expect-error completionAction.action is required
  await annie.downloads.start({ url: "https://example.com", completionAction: { payload: 1 } });
  // @ts-expect-error message needs a type
  await annie.messages.send({ title: "no type" });
  // @ts-expect-error update needs the handle id first
  await annie.messages.update({ type: "text" });
  // @ts-expect-error unknown operation
  await annie.android.tts.shout("x");
  // @ts-expect-error not a registered error code
  const code: AnnieErrorCode = "PERMISSION_DENIED";
  void code;
}
void bad();
