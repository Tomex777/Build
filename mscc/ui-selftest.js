import assert from 'node:assert/strict'
import { createWhatsAppUi, WHATSAPP_UI_MAX_ROWS } from './utils/whatsapp/ui.js'

assert.equal(WHATSAPP_UI_MAX_ROWS, 1000)

const sent = []
const sock = {
  async sendMessage(chat, payload, options = {}) {
    sent.push({ chat, payload, options })
    return { chat, payload, options }
  },
}

const ui = createWhatsAppUi({
  sock,
  chat:'123@g.us',
  quoted:{ key:{ id:'quoted' } },
  prefix:'.',
})

assert.equal(ui.prefix, '.')

await ui.bottomSheet({
  title:'Choose',
  text:'Pick one',
  buttonText:'Open',
  rows:[
    { title:'One', description:'First', id:'.one' },
    { title:'Two', description:'Second', id:'.two' },
  ],
})
assert.equal(sent.at(-1).payload.nativeFlow[0].text, 'Open')
assert.equal(sent.at(-1).payload.nativeFlow[0].sections[0].rows.length, 2)
assert.equal(sent.at(-1).payload.nativeFlow[0].sections[0].rows[1].id, '.two')

await ui.confirmCancel({
  title:'Delete file?',
  text:'This cannot be undone.',
  confirmId:'.file ~confirm 1',
  cancelId:'.file ~cancel 1',
})
let rows = sent.at(-1).payload.nativeFlow[0].sections[0].rows
assert.equal(rows[0].title, '✓ Confirm')
assert.equal(rows[0].id, '.file ~confirm 1')
assert.equal(rows[1].title, '✕ Cancel')
assert.equal(rows[1].id, '.file ~cancel 1')

await ui.acceptDecline({
  title:'Invitation',
  acceptId:'.invite ~accept 1',
  declineId:'.invite ~decline 1',
})
rows = sent.at(-1).payload.nativeFlow[0].sections[0].rows
assert.equal(rows[0].title, '✓ Accept')
assert.equal(rows[1].title, '✕ Decline')

await ui.joinCancel({
  title:'Chess challenge',
  joinId:'.chess ~join 1',
  cancelId:'.chess ~cancel 1',
  joinText:'♟️ Join game',
})
rows = sent.at(-1).payload.nativeFlow[0].sections[0].rows
assert.equal(rows[0].title, '♟️ Join game')
assert.equal(rows[0].id, '.chess ~join 1')

await ui.quickActions({
  title:'Song result',
  buttonText:'Lyrics',
  actions:[
    { title:'🎤 Lyrics', description:'Open lyrics', id:'.lyrics ~track abc' },
  ],
})
rows = sent.at(-1).payload.nativeFlow[0].sections[0].rows
assert.equal(rows.length, 1)
assert.equal(rows[0].id, '.lyrics ~track abc')

await ui.pagedPicker({
  title:'Results',
  rows:Array.from({ length:45 }, (_, i) => ({
    title:'Item ' + (i + 1),
    id:'.pick ' + (i + 1),
  })),
  page:2,
  pageSize:20,
  previousId:'.pick ~page 1',
  nextId:'.pick ~page 3',
})
rows = sent.at(-1).payload.nativeFlow[0].sections[0].rows
assert.equal(rows[0].title, 'Item 21')
assert.equal(rows[19].title, 'Item 40')
assert.equal(rows[20].id, '.pick ~page 1')
assert.equal(rows[21].id, '.pick ~page 3')

let attempt = 0
const fallbackSent = []
const fallbackUi = createWhatsAppUi({
  sock:{
    async sendMessage(chat, payload, options = {}) {
      attempt += 1
      fallbackSent.push({ chat, payload, options })
      if (attempt === 1 && payload.nativeFlow) throw new Error('native flow unavailable')
      return payload
    },
  },
  chat:'123@g.us',
})

await fallbackUi.singleSelect({
  title:'Fallback',
  text:'Choose',
  rows:[{ title:'One', id:'.one' }],
})
assert.equal(fallbackSent.length, 2)
assert.ok(fallbackSent[0].payload.nativeFlow)
assert.equal(fallbackSent[1].payload.buttonText, 'Choose')
assert.equal(fallbackSent[1].payload.sections[0].rows[0].id, '.one')

console.log('semantic WhatsApp UI toolkit self-test passed')
