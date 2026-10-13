import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import fs from 'node:fs';
function setup() {
  const updates = [];
  const context = vm.createContext({annie:{messages:{
    send:async message => { updates.push(message); return {id:'one'}; },
    update:async (handle,message) => { assert.equal(handle,'one'); updates.push(message); }
  }}});
  vm.runInContext(fs.readFileSync('annie-android/app/src/main/assets/script-stream.js','utf8'),context);
  return {updates, context};
}
test('stream publishes incremental responses into one handle in order',async () => {
  const {updates,context} = setup();
  const stream = await context.annie.messages.stream();
  await Promise.all([stream.append('Hello'),stream.append(' world')]);
  await stream.finish('!');
  assert.deepEqual(updates.map(m=>m.text),['','Hello','Hello world','Hello world!']);
  assert.deepEqual(updates.map(m=>m.streaming),[true,true,true,false]);
  await assert.rejects(stream.append('late'),/closed/);
});
test('cancellation retains partial text and ends the stream',async () => {
  const {updates,context} = setup();
  const stream = await context.annie.messages.stream('Partial');
  await stream.cancel();
  assert.equal(updates.at(-1).text,'Partial');
  assert.equal(updates.at(-1).streaming,false);
  assert.equal(updates.at(-1).streamFailed,true);
});
