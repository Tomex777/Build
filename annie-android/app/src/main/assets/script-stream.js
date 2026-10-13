// One persistent host-owned message; updates pass through the normal package grant checks.
globalThis.annie.messages.stream = async function(initial = '') {
  let text = String(initial);
  let closed = false;
  let queue = Promise.resolve();
  const handle = await annie.messages.send({type:'text', text, streaming:true});
  function update(chunk, final, failed) {
    if (closed) return Promise.reject(new Error('Message stream is closed'));
    if (final) closed = true;
    const addition = String(chunk || '');
    queue = queue.then(async () => {
      text += addition;
      if (text.length > 8000) throw new Error('Message stream exceeds its size limit');
      await annie.messages.update(handle.id, {type:'text', text, streaming:!final, streamFailed:!!failed});
      return handle;
    });
    return queue;
  }
  return Object.freeze({
    handle,
    append: chunk => update(chunk, false, false),
    finish: (chunk = '') => update(chunk, true, false),
    cancel: () => update('', true, true)
  });
};
void 0;
