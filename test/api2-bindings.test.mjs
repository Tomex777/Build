import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const bindings=[
 {id:'android.tts.speak',path:'android.tts.speak',positional:['text'],options:['language','queue'],spread:false},
 {id:'messages.send',path:'messages.send',positional:['message'],options:[],spread:false},
 {id:'android.notifications.post',path:'android.notifications.post',positional:[],options:[],spread:true}
];
function setup() {
 const calls=[];
 const context=vm.createContext({annie:{messages:{text:text=>({type:'text',text})}},annieAndroidBridge:async(id,input)=>{calls.push({id,input:JSON.parse(input)});return '{}';}});
 vm.runInContext(fs.readFileSync('annie-android/app/src/main/assets/api2-bindings.js','utf8'),context);
 context.__annieInstallApi2(bindings);
 return {context,calls};
}
test('API 2 preserves wrong types for host validation and keeps message constructors',async()=>{
 const {context,calls}=setup();
 await context.annie.android.tts.speak(42);
 assert.equal(calls[0].input.text,42);
 assert.equal(context.annie.messages.text('ok').text,'ok');
});
test('API 2 rejects extra positional arguments and unknown options',async()=>{
 const {context,calls}=setup();
 await assert.rejects(context.annie.android.tts.speak('hello',{text:'override'}),e=>e.code==='INVALID_ARGUMENT');
 await assert.rejects(context.annie.messages.send({type:'text',text:'hello'},'ignored'),e=>e.code==='INVALID_ARGUMENT');
 await assert.rejects(context.annie.android.tts.speak('hello',null),e=>e.code==='INVALID_ARGUMENT');
 assert.equal(calls.length,0);
});
test('spread bindings preserve unknown properties so strict host validation can reject them',async()=>{
 const {context,calls}=setup();
 await context.annie.android.notifications.post({title:'test',text:'hello',unknown:1});
 assert.equal(calls[0].input.unknown,1);
});
