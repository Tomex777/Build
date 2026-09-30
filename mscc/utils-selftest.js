import assert from 'node:assert/strict'
import {
  normalizeJid,
  jidUser,
  isGroupJid,
  isTrackableJid,
  maskedPhone,
  commandText,
  nativeFlowSelection,
  sanitizeNativeFlowSections,
  sendSingleSelect,
  sendNativeFlowSelectors,
  startProgress,
  buildMediaPayload,
} from './utils/whatsapp/index.js'
import { brandedTitle } from './utils/media/branding.js'
import { brandedFilename, sanitizeFilename } from './utils/media/filenames.js'
import { chunkText, humanBytes, humanDuration } from './utils/text/formatting.js'
import { createLimiter } from './utils/async.js'

assert.equal(jidUser(normalizeJid('234000000001:4@s.whatsapp.net')), '234000000001')
assert.equal(isGroupJid('12345@g.us'), true)
assert.equal(isTrackableJid('12345@lid'), true)
assert.equal(maskedPhone('234000000001'), '234••••0001')

assert.equal(commandText({ conversation:'.ping' }), '.ping')
assert.equal(commandText({
  listResponseMessage:{ singleSelectReply:{ selectedRowId:'.anime Bleach' } },
}), '.anime Bleach')

const nested = { screen:{ result:{ selected_id:'.source anime alpha' } } }
assert.equal(nativeFlowSelection(nested), '.source anime alpha')
assert.equal(commandText({
  interactiveResponseMessage:{
    nativeFlowResponseMessage:{ paramsJson:JSON.stringify(nested) },
  },
}), '.source anime alpha')

const sections = sanitizeNativeFlowSections({
  sections:[
    { title:'A', rows:Array.from({length:700},(_,i)=>({title:'A'+i,id:'a'+i})) },
    { title:'B', rows:Array.from({length:700},(_,i)=>({title:'B'+i,id:'b'+i})) },
  ],
})
assert.equal(sections.length, 2)
assert.equal(sections.reduce((sum,section)=>sum+section.rows.length,0), 1000)

const sent = []
const fakeSock = {
  async sendMessage(chat, payload, options) {
    const result = { key:{ remoteJid:chat, id:'m'+(sent.length+1) } }
    sent.push({ chat, payload, options })
    return result
  },
}

await sendSingleSelect({
  sock:fakeSock,
  chat:'234000000001@s.whatsapp.net',
  title:'Sources',
  text:'Choose',
  rows:[{ title:'One', description:'First', id:'.pick one' }],
})
assert.equal(sent.at(-1).payload.nativeFlow[0].sections[0].rows[0].id, '.pick one')

await sendNativeFlowSelectors({
  sock:fakeSock,
  chat:'234000000001@s.whatsapp.net',
  text:'Separate selectors',
  selectors:[
    { text:'Quality', rows:[{ title:'1080p', id:'.quality 1080' }] },
    { text:'Format', rows:[{ title:'Document', id:'.format document' }] },
  ],
})
assert.equal(sent.at(-1).payload.nativeFlow.length, 2)

const progress = await startProgress(fakeSock, '234000000001@s.whatsapp.net', 'Searching…')
await progress.update('Found it')
assert.equal(sent.at(-1).payload.edit?.id, 'm3')
assert.equal(sent.at(-1).payload.text, 'Found it')

const media = buildMediaPayload({
  source:'https://example.invalid/video.mp4',
  mimetype:'video/mp4',
  fileName:'Nami - Episode 1.mp4',
})
assert.equal(media.video.url, 'https://example.invalid/video.mp4')
assert.equal(media.fileName, 'Nami - Episode 1.mp4')

assert.equal(brandedTitle('AnimePahe - Bleach', {
  botName:'Nami',
  sourceName:'AnimePahe',
}), 'Nami - Bleach')
assert.equal(brandedFilename('AnimePahe: Bleach?.mp4', {
  botName:'Nami',
  sourceName:'AnimePahe',
}), 'Nami Bleach.mp4')
assert.equal(sanitizeFilename('bad:name?.mp4'), 'bad name.mp4')

assert.deepEqual(chunkText('a'.repeat(10), 4), ['aaaa','aaaa','aa'])
assert.equal(humanBytes(1024), '1.00 KB')
assert.equal(humanDuration(61000), '1m 1s')

let concurrent = 0
let maxConcurrent = 0
const limiter = createLimiter(2)
await Promise.all(Array.from({length:6}, (_, index) => limiter.run(async () => {
  concurrent += 1
  maxConcurrent = Math.max(maxConcurrent, concurrent)
  await new Promise(resolve => setTimeout(resolve, 2 + (index % 2)))
  concurrent -= 1
})))
assert.equal(maxConcurrent, 2)

console.log('MSCC shared utilities self-test OK')
