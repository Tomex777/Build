import { readdir } from 'node:fs/promises'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { pickLine } from './response-pools.js'

const ASSET_ROOT = fileURLToPath(new URL('./assets/personalities/', import.meta.url))
const assetBags = new Map()

const PROFILES = {
  josiah: {
    id:'josiah',
    displayName:'Josiah',
    title:'𝙹𝙾𝚂𝙸𝙰𝙷',
    mark:'◇',
    menuCapabilities:null,
    menuOpeners:[
      'You called?',
      "Everything's right here.",
      'Pick what you need.',
      'Looking for something?',
      "I'm listening.",
      'Go on, choose one.',
      'You know where to find me.',
      'What are we doing today?',
      'Take your pick.',
      'Need something?',
      "Let's see what we've got.",
      'Found your way back, huh?',
      'What are you after?',
      'Choose wisely.',
      'There you are.',
      'Ready when you are.',
      "Everything's in place.",
      'Go ahead.',
      "Let's get to it.",
      "You've got options.",
    ],
    firstIntros:[
      "Hey, everyone.\\n\\nI'm Josiah. Looks like I'll be around from now on.\\n\\n.menu whenever you need the directory. ◇",
      "Hi. Josiah.\\n\\nApparently I live here now.\\n\\n.menu shows you what I can do. Or just ask when you need me. ◇",
      "Well... looks like I'm here now.\\n\\nI'm Josiah. If you need something, ask. If you need the list, .menu.\\n\\nTry not to make this boring. ◇",
      "So this is the group.\\n\\nHi, everyone. I'm Josiah.\\n\\nYou don't need to memorize anything — .menu is there when you need it. ◇",
      "Hey. I'm Josiah — the general one.\\n\\nTools, searches, random requests... I'm usually a good place to start.\\n\\n.menu if you want the directory. ◇",
      "Looks like we're acquainted now.\\n\\nI'm Josiah. Mention me when you actually need me; .menu has the command side of things. ◇",
      "New group, noted.\\n\\nI'm Josiah. I'll keep up.\\n\\n.menu if you want to see what's available. ◇",
      "Right. I'm here.\\n\\nJosiah. General questions, useful tools, and the occasional rescue operation.\\n\\n.menu has the rest. ◇",
    ],
    returnIntros:[
      "Well, I'm back.\\n\\nMiss me?\\n\\n.menu if everyone somehow forgot what I do. ◇",
      "Back again.\\n\\nLet's pretend the dramatic exit never happened. .menu if you need the directory. ◇",
      "You got rid of me once. Interesting decision bringing me back. 😭\\n\\nAnyway — .menu. ◇",
      "And we're doing this again.\\n\\nHi. Josiah. Still know where .menu is? ◇",
      "Returned safely.\\n\\nI'll pick up from here. .menu if you need the list again. ◇",
      "Round two, apparently.\\n\\nI'm back. Mention me when you need me. ◇",
    ],
  },
  nami: {
    id:'nami',
    displayName:'Nami',
    title:'𝙽𝙰𝙼𝙸',
    mark:'✦',
    menuCapabilities:new Set(['anime','manga','general','group','core','tools']),
    menuOpeners:[
      'Alright, what are we watching?',
      'Anime first. Sensible choice.',
      'You came to the right person. ✦',
      "Okay, show me what you're looking for.",
      "Let's find you something good.",
      'Series, manga, chaos. Pick one.',
      "I was hoping you'd ask.",
      'Back for another title?',
      "Fine. Let's feed the watchlist.",
      'What kind of rabbit hole are we entering today?',
      'I have recommendations. Obviously.',
      "Let's see what deserves your time.",
      "Anime or manga? Either way, I'm listening.",
      'Please tell me this is not another “what should I watch?” with zero clues. 😭',
      'Alright. Give me a title, genre, mood — something.',
      'The list is ready. Your free time may not be.',
      'You want episodes? Manga? Recommendations? Go on.',
      'Good. We can work with this.',
      "Let's find the next obsession.",
      "I'm not saying your backlog needs help. But I am here. ✦",
    ],
    firstIntros:[
      "Yo. I'm Nami. ✦\\n\\nAnime and manga are my side of things. Recommendations, questions, obscure details — I'm listening.\\n\\n.menu if you want the command list.",
      "Okay, this group has an anime person now.\\n\\nI'm Nami. Manga counts too before somebody starts. 😭\\n\\nMention me when you want to talk; .menu has the tools. ✦",
      "Hey. Nami here. ✦\\n\\nIf the conversation turns into anime, manga, adaptations, arcs, studios, or “what should I watch next?”... that's probably me.\\n\\n.menu for the command side.",
      "So this is where I landed. Nice.\\n\\nI'm Nami — anime and manga. I can actually discuss the stuff, not just search titles.\\n\\n.menu when you want the tools. ✦",
      "Hi. I'm Nami.\\n\\nGive me a genre, a title, a character, an adaptation argument — whatever. Just don't ask for spoilers and complain when I answer. 😭\\n\\n.menu is there too. ✦",
      "Nami. Anime. Manga. Very normal amount of opinions. ✦\\n\\nMention me if you want to talk. Use .menu if you want the actual command list.",
      "Alright, I'm in.\\n\\nI'm Nami. If you need anime or manga help, I've got it. If you need something else, I might still have thoughts.\\n\\n.menu for the tools. ✦",
      "New group unlocked.\\n\\nI'm Nami — resident anime/manga specialist.\\n\\nAsk naturally, or use .menu when you want commands. ✦",
      "I'm Nami. ✦\\n\\nI handle anime and manga around here, which means yes, recommendations are allowed and no, “anything good” is not enough information. 😭\\n\\n.menu when you need it.",
      "Hey everyone. Nami here.\\n\\nAnime and manga are my lane. I'll keep up with the conversation, so you don't have to start from zero every time.\\n\\n.menu has the operational stuff. ✦",
      "Well, this looks promising.\\n\\nI'm Nami. Talk anime or manga to me and I'll probably have something to say.\\n\\n.menu if you're trying to actually find or download something. ✦",
      "Nami joined the chat. That sounded more dramatic than it needed to. 😭\\n\\nAnime + manga are mine. Mention me when you need me.\\n\\n.menu for commands. ✦",
    ],
    returnIntros:[
      "I'm back. ✦\\n\\nPlease tell me the watchlist did not improve while I was gone.\\n\\n.menu if you need the tools again.",
      'Round two. Nami again.\\n\\nAnime and manga department reopened. ✦',
      'You brought me back? Good decision.\\n\\nI had unfinished recommendations anyway. 😭✦',
      "Back in the group.\\n\\nSomeone catch me up on what terrible anime opinions happened while I was gone. ✦",
      'Nami, returning. ✦\\n\\nSame anime/manga brain. Same .menu. Probably more opinions.',
      "And I'm back.\\n\\nFine, I'll forgive the removal. Eventually. 😭\\n\\n.menu if you forgot the tools. ✦",
      'Re-entry complete. ✦\\n\\nWhat did I miss? Preferably something worth watching.',
      "I leave for five minutes and somehow I'm back.\\n\\nAlright. Nami reporting in. ✦",
    ],
  },
}

export function presentationFor(profileId) {
  return PROFILES[String(profileId || '').trim().toLowerCase()] || null
}

export function visibleCommandsForProfile(profileId, commands = []) {
  const profile = presentationFor(profileId)
  if (!profile?.menuCapabilities) return commands
  return commands.filter(command => profile.menuCapabilities.has(
    String(command?.capability || 'general').trim().toLowerCase() || 'general'
  ))
}

export function menuOpener(profileId) {
  const profile = presentationFor(profileId)
  if (!profile) return ''
  return pickLine(profile.id + ':menu-opener', profile.menuOpeners)
}

export function groupIntro(profileId, { returning = false, groupName = '' } = {}) {
  const profile = presentationFor(profileId)
  if (!profile) return ''
  const body = pickLine(
    profile.id + ':group-intro:' + (returning ? 'return' : 'first'),
    returning ? profile.returnIntros : profile.firstIntros,
  )
  if (!body) return ''

  const groupLine = groupName && Math.random() < 0.35
    ? '\\n\\nSo this is *' + String(groupName).trim().slice(0, 120) + '*.'
    : ''
  return body + groupLine
}

export function profileHeader(profileId) {
  const profile = presentationFor(profileId)
  if (!profile) return ''
  return [
    '╭─────────────────' + profile.mark,
    '│      ' + profile.title,
    '╰─────────────────' + profile.mark,
  ].join('\\n')
}

function supportedImage(name) {
  return /\\.(?:jpe?g|png|webp)$/i.test(String(name || ''))
}

export async function chooseProfileAsset(profileId, kind) {
  const profile = presentationFor(profileId)
  const safeKind = ['menu','intro'].includes(String(kind)) ? String(kind) : ''
  if (!profile || !safeKind) return ''

  const dir = join(ASSET_ROOT, profile.id, safeKind)
  let names = []
  try {
    names = (await readdir(dir)).filter(supportedImage).sort()
  } catch {
    return ''
  }
  if (!names.length) return ''

  const key = profile.id + ':' + safeKind
  let bag = assetBags.get(key)
  const signature = names.join('\\n')
  if (!bag || bag.signature !== signature || !bag.items.length) {
    const items = [...names]
    for (let i = items.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1))
      ;[items[i], items[j]] = [items[j], items[i]]
    }
    bag = { signature, items }
    assetBags.set(key, bag)
  }
  const name = bag.items.pop()
  return name ? join(dir, name) : ''
}
