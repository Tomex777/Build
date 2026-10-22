import assert from 'node:assert/strict';
import {replaceCaptionWords,composeChannelCaption} from './caption.mjs';
const rules=[
  {from:'what the fuck',to:'WTF'},{from:'bullshit',to:'BS'},
  {from:'shit',to:'shii'},{from:'fuck',to:'f*ck'}
];
assert.equal(replaceCaptionWords('bullshit, shit! what the fuck?! fuck.',{enabled:true,rules}), 'BS, shii! WTF?! f*ck.');
assert.equal(replaceCaptionWords('BULLSHIT + ShIt + WHAT   THE FuCk',{enabled:true,rules}), 'BS + shii + WTF');
assert.equal(replaceCaptionWords('This shittake shirt has skill and is bullshit-ish',{enabled:true,rules}), 'This shittake shirt has skill and is BS-ish');
assert.equal(replaceCaptionWords('WTF and BS stay as-is.',{enabled:true,rules}),'WTF and BS stay as-is.');
assert.equal(replaceCaptionWords('do not replace shit',{enabled:false,rules}),'do not replace shit');
assert.equal(replaceCaptionWords('Café shít; Arabic حقّ and हिन्दी ठीक',{enabled:true,rules}),'Café shít; Arabic حقّ and हिन्दी ठीक');
assert.equal(replaceCaptionWords('foo',{enabled:true,rules:[{from:'foo',to:'bar'},{from:'bar',to:'baz'}]}),'bar');
assert.equal(composeChannelCaption({text:'A little bullshit',replacements:{enabled:true,rules},poster:{enabled:true,name:'TOMEX'}}),'A little BS\n\n> TOMEX');
assert.equal(composeChannelCaption({text:'',poster:{enabled:true,name:'👩🏽‍💻 Ada'}}),'> 👩🏽‍💻 Ada');
assert.equal(composeChannelCaption({text:'Text',poster:{enabled:false,name:'Channel'}}),'Text');
assert.throws(()=>composeChannelCaption({poster:{enabled:true,name:'I\n> fake quote'}}),/Invalid poster/);
assert.throws(()=>composeChannelCaption({text:'🏆'.repeat(500),maxUtf8Bytes:300}),/byte budget/);
assert.throws(()=>replaceCaptionWords('Hello',{enabled:true,rules:[{from:'bad',to:'ok'},{from:'bad',to:'different'}]}),/Duplicate/);
assert.throws(()=>replaceCaptionWords('bad',{enabled:true,
  rules:[{from:'bad',to:'ok\n> forged author'}]}),/Invalid replacement/,
  'A configured word replacement must not insert an extra quoted poster line');
assert.throws(()=>replaceCaptionWords('bad',{enabled:true,
  rules:[{from:'bad',to:'ok\u202Eevil'}]}),/Invalid replacement/,
  'Do not let bidi controls impersonate the poster signature');
assert.throws(()=>replaceCaptionWords('bad',{enabled:true,
  rules:[{from:'bad\\u202E',to:'ok'}]}),/Invalid replacement/,
  'An unsafe bidirectional phrase pattern must not be accepted');
assert.throws(()=>composeChannelCaption({text:'a'.repeat(9000),replacements:{enabled:true,rules}}),
  /processing limit/,'Reject huge incoming scraper captions before Unicode/regex work');
assert.equal(composeChannelCaption({text:'short',maxUtf8Bytes:5}),'short');
console.log('Channel caption signature/replacement tests: PASS');
