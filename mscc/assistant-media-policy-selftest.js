import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { assistantInputPolicy } from './assistant-media-policy.js'

// Actual behavior reported in the WhatsApp screenshot: a person replied to
// Josia's last greeting with photo/sticker but no text. The quoted greeting
// is context, not a fresh question for another Groq completion.
for (const mediaType of ['image', 'sticker', 'video', 'audio', 'document']) {
  assert.deepEqual(
    assistantInputPolicy({ text:'', mediaType }),
    { respond:false, text:'', attachmentType:'' },
    'uncaptioned ' + mediaType + ' must not activate the chatbot',
  )
}
assert.equal(assistantInputPolicy({ text:'   ', mediaType:'image' }).respond, false)
assert.deepEqual(
  assistantInputPolicy({ text:'What do you think?', mediaType:'image' }),
  { respond:true, text:'What do you think?', attachmentType:'image' },
)
assert.deepEqual(
  assistantInputPolicy({ text:'What can you do?' }),
  { respond:true, text:'What can you do?', attachmentType:'' },
)
assert.equal(assistantInputPolicy({ text:'Help me' }).respond, true)
assert.equal(assistantInputPolicy({ text:'Josia' }).respond, true)

// Ensure the real WhatsApp pipeline uses this gate BEFORE assistantActivation
// and routes the attachment label into the text-only prompt.
const source = readFileSync(new URL('./index.js', import.meta.url), 'utf8')
const handler = source.slice(source.indexOf('async function handleProfileAssistant('), source.indexOf('function publicSudoNumbers('))
assert(handler.includes('assistantInputPolicy({'))
assert(handler.includes('if (!input.respond) return false'))
assert(handler.includes('attachmentType:input.attachmentType'))
assert(handler.indexOf('if (!input.respond) return false') < handler.indexOf('await assistantActivation('))
assert(!handler.includes('You were mentioned. Respond naturally.'))
console.log('PASS uncaptioned WhatsApp media remains silent; captioned media stays text-only')
