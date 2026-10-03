import assert from 'node:assert/strict'
import profileCommand, { profileText } from './commands/general/profile.js'

const rendered = profileText({
  displayName:'Ada',
  role:'Admin',
  library:{ total:6, anime:2, manga:1, movie:2, tv:1, watching:2 },
})
assert(rendered.includes('*Ada*'))
assert(rendered.includes('Role: Admin'))
assert(rendered.includes('6 saved'))
assert(rendered.includes('Anime 2'))
assert(rendered.includes('Manga 1'))
assert(rendered.includes('Movies 2'))
assert(rendered.includes('TV 1'))
assert(rendered.includes('Watching releases: 2'))

const empty = profileText({
  displayName:'No Library',
  library:{ total:0 },
})
assert(empty.includes('*Library*'))
assert(empty.includes('Empty'))

const replies = []
const sent = []
const ctx = {
  args:[],
  publicPrefix:'.',
  userKey:'2348000000000',
  message:{ key:{ remoteJid:'test@g.us' } },
  account:{
    sock:{
      async sendMessage(chat, payload, options) {
        sent.push({ chat, payload, options })
        return { key:{ id:'sent' } }
      },
    },
  },
  reply:async value => { replies.push(String(value)); return value },
  resolveCommandTarget:async () => ({ phoneNumber:'', source:'' }),
  getPublicUserProfile:async phone => ({
    phoneNumber:phone,
    displayName:'Self User',
    mentionJid:phone + '@s.whatsapp.net',
    role:'Member',
    photoUrl:'',
    library:{ total:3, anime:1, manga:1, movie:1, tv:0, watching:0 },
  }),
}

await profileCommand.run(ctx)
assert.equal(replies.length, 1)
assert(replies[0].includes('*Self User*'))
assert(replies[0].includes('3 saved'))
assert(!replies[0].includes('2348000000000'))

replies.length = 0
ctx.args = ['@friend']
ctx.resolveCommandTarget = async raw => {
  assert.equal(raw, '@friend')
  return { phoneNumber:'2348111111111', source:'mention' }
}
ctx.getPublicUserProfile = async phone => ({
  phoneNumber:phone,
  displayName:'Friend',
  mentionJid:'2348111111111@s.whatsapp.net',
  role:'Admin',
  photoUrl:'https://example.invalid/photo.jpg',
  library:{ total:2, anime:0, manga:0, movie:1, tv:1, watching:1 },
})

await profileCommand.run(ctx)
assert.equal(replies.length, 0)
assert.equal(sent.length, 1)
assert.equal(sent[0].chat, 'test@g.us')
assert.equal(sent[0].payload.image.url, 'https://example.invalid/photo.jpg')
assert(sent[0].payload.caption.includes('*Friend*'))
assert(sent[0].payload.caption.includes('Movies 1'))
assert(sent[0].payload.caption.includes('TV 1'))
assert(!sent[0].payload.caption.includes('2348111111111'))

console.log('PASS public profile card selftest')
