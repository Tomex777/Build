function clean(value) {
  return String(value || '').trim()
}

function usageOf(command, prefix) {
  const raw = clean(command?.usage || (prefix + command?.name))
  return raw.startsWith('.') && prefix !== '.' ? prefix + raw.slice(1) : raw
}

function aliasesOf(command, prefix) {
  return (command?.aliases || [])
    .map(alias => clean(alias))
    .filter(Boolean)
    .map(alias => prefix + alias)
}

const HELP = {
  anime:{
    title:'Anime',
    flow:[
      'Search or browse anime, choose a title, then reply with episode numbers.',
      'Episode selections accept ranges and mixed sets such as 1-10, 1,3,4,7, or 1-10,13,15-18.',
      'A Manga instant reply appears only when AniList confirms a manga counterpart.',
      'Add to Library appears only when that anime is not already saved. Once saved, the button disappears and the result shows In Library.',
      'After selecting episodes, MSCC uses your saved delivery preference or asks for quality and delivery.',
    ],
    examples:['.anime Bleach','.anime Frieren','.anime Bleach --source kayoanime'],
    sourceCapability:'anime',
    deliveryCapability:'anime',
    related:['manga','library','source','sources','delivery'],
  },
  manga:{
    title:'Manga',
    flow:[
      'Search manga, choose a title, then reply with chapter numbers.',
      'Chapter selections use the same range syntax as anime: 1-10, 1,3,4,7, or mixed ranges.',
      'An Anime instant reply appears only when AniList confirms an anime adaptation/counterpart.',
      'Add to Library appears only when that manga is not already saved.',
      'Download options are shown after the chapter selection unless a delivery default is saved.',
    ],
    examples:['.manga Bleach','.manga Frieren'],
    sourceCapability:'manga',
    deliveryCapability:'manga',
    related:['anime','library','source','sources','delivery'],
  },
  movie:{
    title:'Movies',
    flow:[
      'Search for a movie, choose the result, then choose quality and delivery.',
      'A TV Series instant reply appears only when a verified related TV counterpart exists.',
      'Add to Library appears only when the movie is not already saved.',
      'Related book/novel actions remain available when a screen-to-book relationship is found.',
      'A saved movie delivery preference skips the quality/delivery picker.',
    ],
    examples:['.movie The Matrix','.movie Dune'],
    sourceCapability:'movies',
    deliveryCapability:'movies',
    related:['tv','library','book','source','sources','delivery'],
  },
  tv:{
    title:'TV Series',
    flow:[
      'Search for a TV series, choose the title and season, then reply with episode numbers.',
      'Single-season shows skip the unnecessary season picker.',
      'Episode selections accept 1-10, 1,3,4,7, and mixed ranges.',
      'A Movie instant reply appears only when a verified related movie counterpart exists.',
      'Add to Library appears only when the series is not already saved.',
      'Related book/novel actions remain available when found.',
    ],
    examples:['.tv Breaking Bad','.tv The Last of Us'],
    sourceCapability:'tv',
    deliveryCapability:'tv',
    related:['movie','library','book','source','sources','delivery'],
  },
  library:{
    title:'Library',
    flow:[
      'One Library stores Anime, Manga, Movies, and TV Series for each user.',
      'Open the full Library or filter it by media type.',
      'Every saved item receives a stable Library number. Opening that number returns to the normal media flow.',
      'Use remove to delete an item. Use watch/unwatch to control release watching.',
      'The Add to Library instant reply is dynamic and disappears once the item is saved.',
    ],
    examples:['.library','.library anime','.library manga','.library movie','.library tv','.library 3','.library remove 3','.watch 3','.unwatch 3'],
    related:['anime','manga','movie','tv','watch','unwatch'],
  },
  source:{
    title:'Source defaults',
    flow:[
      'View or save a persistent source choice for a source-backed command folder.',
      'Folders with managed fallback chains cannot be manually pinned; .sources shows their primary/fallback order.',
      'For user-selectable folders, the saved source follows you across groups and bot sessions.',
      'Use clear or auto to remove your saved choice.',
    ],
    examples:['.source anime','.source manga <source>','.source movies <source>','.source tv <source>','.source anime clear'],
    related:['sources','delivery'],
  },
  sources:{
    title:'Installed sources',
    flow:[
      'Lists installed source adapters for one folder or every source-backed folder.',
      'Managed folders show primary/fallback roles. User-selectable folders show the current default.',
    ],
    examples:['.sources','.sources anime','.sources manga','.sources movies','.sources tv','.sources android','.sources courses'],
    related:['source','delivery'],
  },
  delivery:{
    title:'Download defaults',
    flow:[
      'Save the default quality/format and WhatsApp delivery style for a source-backed folder.',
      'Video-capable folders accept source/360/480/720/1080/1440/2160 and video or document delivery.',
      'Books use a book format such as epub or pdf with document delivery.',
      'Clear a saved default when you want MSCC to ask each time.',
    ],
    examples:['.delivery anime 720 document','.delivery movies 1080 video','.delivery youtube 720 document','.delivery books epub document','.delivery anime clear'],
    related:['source','sources'],
  },
  youtube:{
    title:'YouTube',
    flow:[
      'Search YouTube by text or paste a YouTube URL/video ID directly.',
      'Search results can be selected by number or from the result picker.',
      'After choosing a video, pick an available quality unless a YouTube delivery default is saved.',
      'Use --doc / --document to force file delivery or --video for inline video.',
    ],
    examples:['.youtube Kendrick Lamar HUMBLE','.yt https://youtu.be/dQw4w9WgXcQ','.yt <url> --doc'],
    deliveryCapability:'youtube',
    related:['delivery'],
  },
  apk:{
    title:'Android APK',
    flow:[
      'Search Android apps, choose the app, then choose a version and APK variant.',
      'The flow uses installed Android/APK sources and source fallback where configured.',
    ],
    examples:['.apk Firefox','.apk VLC'],
    sourceCapability:'android',
    related:['source','sources'],
  },
  course:{
    title:'Courses',
    flow:[
      'Search for a course/topic, choose a course, then reply with the part number or number range you want.',
      'Course sources are discovered from the installed source registry.',
    ],
    examples:['.course python','.course blender'],
    sourceCapability:'courses',
    related:['source','sources'],
  },
  book:{
    title:'Books & novels',
    flow:[
      'Search by title or author, choose the matching book/edition, then choose an available format.',
      'Books can also appear as verified related actions from movie/TV results.',
    ],
    examples:['.book Dune','.book George Orwell'],
    sourceCapability:'books',
    deliveryCapability:'books',
    related:['movie','tv','source','sources','delivery'],
  },
  song:{
    title:'Music',
    flow:[
      'Search for a song, then reply with the result number you want.',
      'The music flow can use configured providers and saved delivery preferences where supported.',
    ],
    examples:['.song Blinding Lights','.music Billie Jean','.play Numb Linkin Park'],
    sourceCapability:'music',
    deliveryCapability:'music',
    related:['lyrics','mimi','source','sources','delivery'],
  },
  lyrics:{
    title:'Lyrics',
    flow:[
      'Search for lyrics by song/title information and choose the matching track when needed.',
      'Track result instant replies return directly into the lyrics command.',
    ],
    examples:['.lyrics Numb Linkin Park','.lyrics Blinding Lights'],
    related:['song','mimi'],
  },
  web:{
    title:'Web search',
    flow:[
      'Search the web and return concise website results with title, domain, snippet, and URL.',
      'Up to eight concise results are returned.',
    ],
    examples:['.web Android 16 changes','.web best CSS grid guide'],
    related:['image','youtube'],
  },
  image:{
    title:'Image search',
    flow:[
      'Search the web for images and send the usable matching pictures into WhatsApp.',
      'The optional final number controls how many images to send, from 1 to 20. The default is 6.',
    ],
    examples:['.image Bleach','.image Lagos skyline 10'],
    related:['web'],
  },
  nami:{
    title:"Nami's menu",
    flow:[
      'Opens Nami’s own Anime + Manga command menu.',
      'This is a personality menu, not an alias of .help or .menu.',
      'Nami uses her own two-image shuffled menu pool.',
    ],
    examples:['.nami'],
    related:['anime','manga'],
  },
  mimi:{
    title:"MiMi's menu",
    flow:[
      'Opens MiMi’s own Music + Movies + TV command menu.',
      'This is a personality menu, separate from Josia’s .menu and Nami’s .nami.',
      'MiMi uses her own two-image shuffled menu pool.',
    ],
    examples:['.mimi'],
    related:['song','movie','tv'],
  },
  menu:{
    title:"Josia's menu",
    flow:[
      'Opens Josia’s general public command menu.',
      'This is separate from .help. It uses Josia’s own two-image shuffled menu pool.',
      'Nami and MiMi have their own menus: .nami and .mimi.',
    ],
    examples:['.menu'],
    related:['nami','mimi','help'],
  },
}

const aliasToCanonical = {
  movies:'movie',
  film:'movie',
  series:'tv',
  show:'tv',
  shows:'tv',
  music:'song',
  play:'song',
  yt:'youtube',
  lib:'library',
  commands:'help',
}

function sourceLines(ctx, capability) {
  if (!capability || typeof ctx?.listSources !== 'function') return []
  const sources = ctx.listSources(capability) || []
  if (!sources.length) return []
  const mode = typeof ctx.sourceMode === 'function' ? ctx.sourceMode(capability) : ''
  const current = mode === 'user-choice' && typeof ctx.getSourceDefault === 'function'
    ? ctx.getSourceDefault(capability)
    : ''
  const lines = ['Sources']
  for (const source of sources) {
    const tags = []
    if (mode === 'managed') tags.push(source.primary ? 'primary' : 'fallback')
    if (current && current === source.id) tags.push('default')
    lines.push('• ' + source.name + (tags.length ? ' (' + tags.join(', ') + ')' : ''))
  }
  return lines
}

function deliveryLines(ctx, capability) {
  if (!capability || typeof ctx?.getDeliveryDefault !== 'function') return []
  const saved = ctx.getDeliveryDefault(capability)
  if (!saved) return []
  return ['Saved download preference', '• ' + saved.quality + ' · ' + saved.delivery]
}

function relatedCommands(command, commands, spec, prefix) {
  const names = new Set((spec?.related || []).map(value => String(value).toLowerCase()))
  const capability = String(command?.capability || '').toLowerCase()
  for (const candidate of commands) {
    if (candidate === command) continue
    if (capability && String(candidate?.capability || '').toLowerCase() === capability) {
      names.add(String(candidate.name || '').toLowerCase())
    }
  }
  return [...names]
    .filter(Boolean)
    .map(name => prefix + name)
    .slice(0, 12)
}

export function resolveHelpCommand(commands = [], requested = '') {
  const wanted = clean(requested).replace(/^\./, '').toLowerCase()
  if (!wanted) return null
  const canonicalWanted = aliasToCanonical[wanted] || wanted
  return commands.find(command =>
    String(command?.name || '').toLowerCase() === canonicalWanted ||
    (command?.aliases || []).some(alias => String(alias || '').toLowerCase() === wanted)
  ) || null
}

export function commandHelpText(ctx, command, commands = []) {
  const prefix = ctx.publicPrefix || '.'
  const name = String(command?.name || '').toLowerCase()
  const spec = HELP[name] || {}
  const lines = [
    '*' + (spec.title || (prefix + name)) + '*',
    command?.description ? clean(command.description) : '',
    '',
    'Usage',
    usageOf(command, prefix),
  ].filter(value => value !== '')

  const aliases = aliasesOf(command, prefix)
  if (aliases.length) lines.push('', 'Aliases', aliases.join(', '))

  const flow = Array.isArray(spec.flow) ? spec.flow : []
  if (flow.length) {
    lines.push('', 'How it works')
    flow.forEach((step, index) => lines.push(String(index + 1) + '. ' + step))
  } else if (command?.help) {
    lines.push('', clean(command.help))
  }

  const examples = Array.isArray(spec.examples) ? spec.examples : []
  if (examples.length) {
    lines.push('', 'Examples')
    for (const example of examples) {
      lines.push('• ' + (prefix === '.' ? example : example.replace(/^\./, prefix)))
    }
  }

  const dynamicSources = sourceLines(ctx, spec.sourceCapability)
  if (dynamicSources.length) lines.push('', ...dynamicSources)

  const dynamicDelivery = deliveryLines(ctx, spec.deliveryCapability)
  if (dynamicDelivery.length) lines.push('', ...dynamicDelivery)

  const related = relatedCommands(command, commands, spec, prefix)
  if (related.length) lines.push('', 'Related', related.join(' · '))

  return lines.join('\n')
}

export function helpIndexText(ctx, commands = []) {
  const prefix = ctx.publicPrefix || '.'
  const visible = commands
    .filter(command => command?.hidden !== true)
    .sort((a,b) =>
      String(a?.capability || '').localeCompare(String(b?.capability || '')) ||
      String(a?.name || '').localeCompare(String(b?.name || ''))
    )

  const groups = new Map()
  for (const command of visible) {
    const capability = String(command?.capability || 'general').trim().toUpperCase() || 'GENERAL'
    if (!groups.has(capability)) groups.set(capability, [])
    groups.get(capability).push(command)
  }

  const lines = [
    '*MSCC Help*',
    'Use ' + prefix + 'help <command> for the complete help page for that command.',
    '',
    'Personality menus: ' + prefix + 'menu · ' + prefix + 'nami · ' + prefix + 'mimi',
  ]

  for (const [capability, group] of groups) {
    lines.push('', '*' + capability + '*')
    lines.push(group.map(command => prefix + command.name).join(' · '))
  }

  lines.push('', 'Example: ' + prefix + 'help anime')
  return lines.join('\n')
}
