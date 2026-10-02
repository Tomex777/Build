import { readdir } from 'node:fs/promises'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { pickLine } from './response-pools.js'

const ASSET_ROOT = fileURLToPath(new URL('./assets/personalities/', import.meta.url))
const assetBags = new Map()

const PROFILES = {
  josiah: {
    id:'josiah',
    displayName:'Josia',
    title:'𝙹𝙾𝚂𝙸𝙰',
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
      "Hey, everyone.\\n\\nI'm Josia. Looks like I'll be around from now on.\\n\\n.menu whenever you need the directory. ◇",
      "Hi. Josia.\\n\\nApparently I live here now.\\n\\n.menu shows you what I can do. Or just ask when you need me. ◇",
      "Well... looks like I'm here now.\\n\\nI'm Josia. If you need something, ask. If you need the list, .menu.\\n\\nTry not to make this boring. ◇",
      "So this is the group.\\n\\nHi, everyone. I'm Josia.\\n\\nYou don't need to memorize anything — .menu is there when you need it. ◇",
      "Hey. I'm Josia — the general one.\\n\\nTools, searches, random requests... I'm usually a good place to start.\\n\\n.menu if you want the directory. ◇",
      "Looks like we're acquainted now.\\n\\nI'm Josia. Mention me when you actually need me; .menu has the command side of things. ◇",
      "New group, noted.\\n\\nI'm Josia. I'll keep up.\\n\\n.menu if you want to see what's available. ◇",
      "Right. I'm here.\\n\\nJosia. General questions, useful tools, and the occasional rescue operation.\\n\\n.menu has the rest. ◇",
    ],
    returnIntros:[
      "Well, I'm back.\\n\\nMiss me?\\n\\n.menu if everyone somehow forgot what I do. ◇",
      "Back again.\\n\\nLet's pretend the dramatic exit never happened. .menu if you need the directory. ◇",
      "You got rid of me once. Interesting decision bringing me back. 😭\\n\\nAnyway — .menu. ◇",
      "And we're doing this again.\\n\\nHi. Josia. Still know where .menu is? ◇",
      "Returned safely.\\n\\nI'll pick up from here. .menu if you need the list again. ◇",
      "Round two, apparently.\\n\\nI'm back. Mention me when you need me. ◇",
    ],
  },
  nami: {
    id:'nami',
    displayName:'Nami',
    title:'𝙽𝙰𝙼𝙸',
    mark:'✦',
    menuCapabilities:new Set(['anime','manga']),
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
      "Yo. I'm Nami. ✦\\n\\nAnime and manga are my side of things. Recommendations, questions, obscure details — I'm listening.\\n\\n.nami if you want the command list.",
      "Okay, this group has an anime person now.\\n\\nI'm Nami. Manga counts too before somebody starts. 😭\\n\\nMention me when you want to talk; .nami has the tools. ✦",
      "Hey. Nami here. ✦\\n\\nIf the conversation turns into anime, manga, adaptations, arcs, studios, or “what should I watch next?”... that's probably me.\\n\\n.nami for the command side.",
      "So this is where I landed. Nice.\\n\\nI'm Nami — anime and manga. I can actually discuss the stuff, not just search titles.\\n\\n.nami when you want the tools. ✦",
      "Hi. I'm Nami.\\n\\nGive me a genre, a title, a character, an adaptation argument — whatever. Just don't ask for spoilers and complain when I answer. 😭\\n\\n.nami is there too. ✦",
      "Nami. Anime. Manga. Very normal amount of opinions. ✦\\n\\nMention me if you want to talk. Use .nami if you want the actual command list.",
      "Alright, I'm in.\\n\\nI'm Nami. If you need anime or manga help, I've got it. If you need something else, I might still have thoughts.\\n\\n.nami for the tools. ✦",
      "New group unlocked.\\n\\nI'm Nami — resident anime/manga specialist.\\n\\nAsk naturally, or use .nami when you want commands. ✦",
      "I'm Nami. ✦\\n\\nI handle anime and manga around here, which means yes, recommendations are allowed and no, “anything good” is not enough information. 😭\\n\\n.nami when you need it.",
      "Hey everyone. Nami here.\\n\\nAnime and manga are my lane. I'll keep up with the conversation, so you don't have to start from zero every time.\\n\\n.nami has the operational stuff. ✦",
      "Well, this looks promising.\\n\\nI'm Nami. Talk anime or manga to me and I'll probably have something to say.\\n\\n.nami if you're trying to actually find or download something. ✦",
      "Nami joined the chat. That sounded more dramatic than it needed to. 😭\\n\\nAnime + manga are mine. Mention me when you need me.\\n\\n.nami for commands. ✦",
    ],
    returnIntros:[
      "I'm back. ✦\\n\\nPlease tell me the watchlist did not improve while I was gone.\\n\\n.nami if you need the tools again.",
      'Round two. Nami again.\\n\\nAnime and manga department reopened. ✦',
      'You brought me back? Good decision.\\n\\nI had unfinished recommendations anyway. 😭✦',
      "Back in the group.\\n\\nSomeone catch me up on what terrible anime opinions happened while I was gone. ✦",
      'Nami, returning. ✦\\n\\nSame anime/manga brain. Same .nami. Probably more opinions.',
      "And I'm back.\\n\\nFine, I'll forgive the removal. Eventually. 😭\\n\\n.nami if you forgot the tools. ✦",
      'Re-entry complete. ✦\\n\\nWhat did I miss? Preferably something worth watching.',
      "I leave for five minutes and somehow I'm back.\\n\\nAlright. Nami reporting in. ✦",
    ],
  },
  mimi: {
    id:'mimi',
    displayName:'MiMi',
    title:'𝙼𝙸𝙼𝙸',
    mark:'✧',
    menuCapabilities:new Set(['music','movies','tv']),
    menuOpeners:[
      'Okayyy, what are we playing? ✧',
      'Music, movie, or a series? Pick your poison.',
      'You came to me. Excellent taste.',
      "Let's find something worth your time.",
      'Tell me the vibe. I can work with that.',
      'Movie night? Playlist crisis? I got you.',
      "Don't just stare at the menu. Choose something.",
      'Fine. Entertain me while I entertain you. ✧',
      'What are we putting on?',
      'Give me a title, an artist, or at least a mood.',
      'We are not scrolling for forty minutes. Pick.',
      "Let's make this interesting.",
    ],
    firstIntros:[
      "Oh, hi. I'm MiMi. ✧\n\nMusic, movies, and TV are my side of things. Give me a title, an artist, a mood, or a very questionable description and we'll work with it.\n\n.mimi has my command list.",
      "Well, look who has a media person now.\n\nI'm MiMi — music, movies, TV. Yes, I have opinions. Obviously. ✧\n\nMention me when you want me; .mimi has the command side.",
      "MiMi here. ✧\n\nSongs, albums, films, shows, recommendations — that's my lane.\n\nYou can talk to me normally. .mimi is there when you want the actual commands.",
      "Hi, everyone. I'm MiMi.\n\nIf we're choosing what to watch or what to play, I have questions about your taste already. 😭\n\n.mimi when you need the tools. ✧",
      "Okay, introductions. Cute.\n\nI'm MiMi. Music, movies, TV series — bring me the good stuff or let me help you find it. ✧\n\n.mimi has the command list.",
      "So this is the group? Nice.\n\nI'm MiMi. I handle the soundtrack and the screen time around here.\n\nMention me when you need me. ✧",
      "MiMi joined. ✧\n\nI do music, movies, and TV. Recommendations, questions, finding things — all of that.\n\n.mimi if you want the operational stuff.",
      "Hi. MiMi.\n\nGive me a song you can't remember, a movie you barely described, or a series you need to obsess over next. We'll figure it out. ✧",
    ],
    returnIntros:[
      "I'm back. ✧\n\nTry to contain the excitement.\n\n.mimi if you forgot what I handle.",
      "And we're back.\n\nMiMi, music, movies, TV. You know the drill. ✧",
      "You brought me back? Correct decision.\n\nNow, what are we watching or playing? ✧",
      "Round two. MiMi again.\n\nI expect better entertainment choices this time. 😭",
      "Back in the group. ✧\n\nCatch me up later. First, who ruined the playlist?",
      "Missed me? Don't answer that.\n\nI'm back. .mimi if you need the list. ✧",
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

export async function chooseProfileAsset(profileId, kind, { returning = false } = {}) {
  const profile = presentationFor(profileId)
  const safeKind = ['menu','intro'].includes(String(kind)) ? String(kind) : ''
  if (!profile || !safeKind) return ''

  const dirs = safeKind === 'intro'
    ? [
        join(ASSET_ROOT, profile.id, 'intro', returning ? 'return' : 'first'),
        join(ASSET_ROOT, profile.id, 'intro'),
      ]
    : [join(ASSET_ROOT, profile.id, 'menu')]

  let dir = ''
  let names = []
  for (const candidate of dirs) {
    try {
      const found = (await readdir(candidate)).filter(supportedImage).sort()
      if (found.length) {
        dir = candidate
        names = found
        break
      }
    } catch {}
  }
  if (!dir || !names.length) return ''

  const key = profile.id + ':' + safeKind + (safeKind === 'intro' ? ':' + (returning ? 'return' : 'first') : '')
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
