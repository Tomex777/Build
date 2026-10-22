import { parseNumberSelection } from './number-selection.js'
import { lyricsInstantRows } from './lyrics-flow.js'

function displayDuration(item = {}) {
  const explicit = String(item?.duration || '').trim()
  if (explicit && !/^\d+(?:\.\d+)?$/.test(explicit)) return explicit
  const total = Math.max(0, Math.round(Number(item?.durationSeconds || explicit || 0) || 0))
  if (!total) return ''
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  return hours
    ? [hours, String(minutes).padStart(2,'0'), String(seconds).padStart(2,'0')].join(':')
    : [minutes, String(seconds).padStart(2,'0')].join(':')
}

function normalizeTrack(item, index) {
  return {
    id:String(item?.id ?? item?.url ?? item?.slug ?? index + 1),
    number:String(index + 1),
    title:String(item?.title || item?.name || `Result ${index + 1}`),
    artist:String(item?.artist || item?.author || item?.uploader || '').trim(),
    album:String(item?.album || '').trim(),
    cover:String(item?.cover || item?.artwork || item?.image || item?.thumbnail || '').trim(),
    duration:displayDuration(item),
    durationSeconds:Number(item?.durationSeconds || 0) || 0,
    description:String(item?.description || '').trim(),
    raw:item,
  }
}

function musicFailureText(outcome) {
  if (outcome?.status === 'no-sources') return 'No music sources are installed yet.'
  if (outcome?.status === 'source-error') return `${outcome.source?.name || 'The music source'} could not deliver that track. Please try again.`
  if (outcome?.status === 'all-failed') return 'Music download failed across all configured sources. Try another track or try again later.'
  return 'Could not complete that music request. Please try again.'
}

function outcomeError(ctx, outcome) {
  if (outcome?.error) {
    console.warn('MSCC music request failed:', {
      status:outcome.status,
      source:outcome.source?.id || '',
      reason:outcome.error?.code || outcome.error?.name || 'Error',
    })
  }
  return ctx.reply(musicFailureText(outcome))
}

function searchTextVariants(query = '') {
  const raw = String(query || '').trim()
  if (!raw) return []
  const spaced = raw.replace(/[-_.,/]+/g, ' ').replace(/\s+/g, ' ').trim()
  const collapsed = raw.replace(/[^\p{L}\p{N}]+/gu, '')
  const singleLettersCollapsed = raw.replace(/\b([A-Za-z])(?:\s*[-.]\s*|\s+)(?=[A-Za-z]\b)/g, '$1')
  return [...new Set([raw, spaced, singleLettersCollapsed, collapsed].filter(Boolean))]
}

function effectiveMusicQuery(query = '') {
  const raw = String(query || '').trim()
  // A very common speech-to-text typo in the Pitch Perfect 2 soundtrack.
  // Do not autocorrect unrelated, legitimately named tracks.
  return /\bpitch\s+perfect\s*2\b/i.test(raw) && /\bfleshlight\b/i.test(raw)
    ? raw.replace(/\bfleshlight\b/ig, 'Flashlight')
    : raw
}

function normalizeSearchText(value = '') {
  return String(value || '')
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function editSimilarity(a, b) {
  const left = normalizeSearchText(a)
  const right = normalizeSearchText(b)
  if (!left || !right) return 0
  const previous = Array.from({ length:right.length + 1 }, (_, i) => i)
  for (let i = 1; i <= left.length; i += 1) {
    let diagonal = previous[0]
    previous[0] = i
    for (let j = 1; j <= right.length; j += 1) {
      const saved = previous[j]
      const cost = left[i - 1] === right[j - 1] ? 0 : 1
      previous[j] = Math.min(
        previous[j] + 1,
        previous[j - 1] + 1,
        diagonal + cost,
      )
      diagonal = saved
    }
  }
  const distance = previous[right.length]
  return 1 - distance / Math.max(left.length, right.length)
}

function scoreTrackQuery(query, track = {}) {
  const wanted = normalizeSearchText(query)
  const title = normalizeSearchText(track.title)
  const artist = normalizeSearchText(track.artist)
  if (!wanted) return 0
  if (title === wanted) return 100
  if (artist === wanted) return 92

  const wantedTokens = new Set(wanted.split(' ').filter(Boolean))
  const titleTokens = new Set(title.split(' ').filter(Boolean))
  const artistTokens = new Set(artist.split(' ').filter(Boolean))
  const overlap = [...wantedTokens].filter(token => titleTokens.has(token) || artistTokens.has(token)).length
  const tokenScore = wantedTokens.size ? overlap / wantedTokens.size : 0
  const textScore = Math.max(editSimilarity(wanted, title), editSimilarity(wanted, artist))
  const containment = title.includes(wanted) || artist.includes(wanted) ? 0.35 : 0
  let score = Math.round((tokenScore * 45) + (textScore * 45) + (containment * 10))
  const wantsLyrics = /\blyrics?\b/i.test(query)
  const wantsCover = /\bcover\b/i.test(query)
  // "Official" is preferable to fan lyric/cover uploads for a track search.
  if (/\bofficial\s+(?:music\s+)?(?:video|audio)\b/i.test(track.title)) score += 14
  if (!wantsLyrics && /\blyrics?|karaoke\b/i.test(track.title)) score -= 12
  if (!wantsCover && /\bcover|remix|sped\s*up|slowed\b/i.test(track.title)) score -= 8
  return Math.max(0, Math.min(120, score))
}

async function broadenMusicSearch(ctx, query, firstOutcome) {
  const variants = searchTextVariants(query)
  const candidates = []
  const seen = new Set()

  const collect = (outcome, searchedQuery) => {
    if (outcome?.status !== 'ok') return
    const rows = Array.isArray(outcome.result?.items)
      ? outcome.result.items
      : outcome.result?.item
        ? [outcome.result.item]
        : []
    for (const row of rows) {
      const track = normalizeTrack(row, candidates.length)
      const key = String(track.id || track.title || '').toLowerCase()
      if (!key || seen.has(key)) continue
      seen.add(key)
      candidates.push({
        track,
        score:scoreTrackQuery(query, track),
        searchedQuery,
        sourceId:outcome.source?.id || '',
        sourceName:outcome.source?.name || '',
      })
    }
  }

  collect(firstOutcome, query)
  const primary = firstOutcome?.source?.id || ''

  // Avoid 3 extra YouTube lookups on an already-good search result.
  if (candidates.length >= 3 && candidates.some(candidate => candidate.score >= 55)) {
    return candidates.sort((a, b) => b.score - a.score)
  }

  for (const variant of variants.slice(1, 4)) {
    if (variant === query) continue
    const outcome = await ctx.executeSource({
      capability:'music',
      pinnedSource:primary,
      payload:{ action:'search', query:variant },
    })
    collect(outcome, variant)
  }

  const best = candidates.sort((a, b) => b.score - a.score)
  if (best.length && best[0].score >= 55) return best

  const fallback = await ctx.executeSource({
    capability:'music',
    excludedSources:primary ? [primary] : [],
    payload:{ action:'search', query:variants[0] || query },
  })
  collect(fallback, variants[0] || query)

  return candidates.sort((a, b) => b.score - a.score)
}


function parseSearchArgs(args = []) {
  let delivery = 'audio'
  const queryParts = []
  for (const value of args) {
    const part = String(value || '').trim()
    if (part === '-d' || part === '--doc') {
      delivery = 'document'
      continue
    }
    if (part) queryParts.push(part)
  }
  return { query:queryParts.join(' ').trim(), delivery }
}

async function search(ctx, query, { delivery = 'audio' } = {}) {
  if (!query) return ctx.reply('Usage: .song <song name>')

  const providerQuery = effectiveMusicQuery(query)
  const outcome = await ctx.executeSource({
    capability:'music',
    payload:{ action:'search', query:providerQuery },
  })
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)

  const ranked = await broadenMusicSearch(ctx, providerQuery, outcome)
  const selectedSourceId = ranked[0]?.sourceId || outcome.source?.id || ''
  const sourceResults = ranked.filter(entry => !selectedSourceId || entry.sourceId === selectedSourceId)
  // When relevant tracks exist, do not pad the menu with unrelated videos.
  // Keep the fallback unchanged when a provider cannot produce confident scores.
  const bestScore = sourceResults[0]?.score || 0
  const relevanceFloor = bestScore >= 55 ? Math.max(38, bestScore - 35) : 0
  const tracks = sourceResults
    .filter(entry => entry.score >= relevanceFloor)
    .slice(0, 10)
    .map((entry, index) => ({
      ...entry.track,
      number:String(index + 1),
      searchScore:entry.score,
      rawSourceId:entry.sourceId,
    }))

  if (!tracks.length) {
    return ctx.reply(`No useful music results found for “${query}”. Try the artist, title, or both.`)
  }

  ctx.setCommandReplySession?.({
    kind:'number-selection',
    command:'song',
    capability:'music',
    sourceId:selectedSourceId || outcome.source.id,
    entries:tracks,
    unit:'song',
    query:providerQuery,
    requestedQuery:query,
    delivery,
    expiresAt:Date.now() + 30 * 60000,
  })

  const lines = [
    '🎵 *Song Search Results*',
    `Query: *${query}*`,
    ...(providerQuery !== query ? [`Showing matches for: *${providerQuery}*`] : []),
    '',
    ...tracks.map(track => {
      const artist = track.artist ? ` — ${track.artist}` : ''
      const duration = track.duration ? ` [${track.duration}]` : ''
      return `${track.number}. ${track.title}${artist}${duration}`
    }),
    '',
    '*Reply with the number(s) you want.*',
    'Examples: `1` • `1,3,5` • `1-4`',
    delivery === 'document'
      ? '> Delivery: document'
      : '> Add `-d` or `--doc` to your search to receive the selected track(s) as documents.',
  ]
  const text = lines.join('\n')
  // Expose an exact-track lyrics action when interactive WhatsApp lists are
  // supported. Numeric selection stays available in the same search message;
  // plain text remains the fallback for unsupported clients or send failures.
  const lyricsRows = lyricsInstantRows(tracks, {
    prefix:ctx.publicPrefix || '.',
    max:25,
  })
  if (lyricsRows.length && typeof ctx.replyList === 'function') {
    const coverUrl = tracks.find(track => /^https?:\/\//i.test(track.cover))?.cover || ''
    try {
      return await ctx.replyList({
        title:'Song Search Results',
        text,
        caption:text,
        buttonText:'Lyrics',
        rows:lyricsRows,
        ...(coverUrl ? { image:{ url:coverUrl } } : {}),
      })
    } catch (error) {
      console.warn('MSCC song lyrics interactive menu fallback:', error?.message || error)
    }
  }
  return ctx.reply(text)
}

function downloadResponse(outcome) {
  if (outcome.status !== 'ok') return { ok:false, outcome }
  const result = outcome.result
  if (result?.delivered === true) return { ok:true, silent:true }
  if (typeof result === 'string' && result.trim()) return { ok:true, text:result }
  if (result?.text) return { ok:true, text:String(result.text) }
  // Do not claim a download started when the provider sent nothing.
  return {
    ok:false,
    outcome:{
      ...outcome,
      status:'source-error',
      error:new Error('Music source returned without confirming delivery.'),
    },
  }
}

async function downloadTrack(ctx, { sourceId, track, delivery = 'audio' }) {
  const firstPayload = {
    action:'download',
    itemId:track.id,
    item:track.raw || track,
    track:track.raw || track,
    quality:'source',
    delivery,
  }
  const first = await ctx.executeSource({
    capability:'music',
    pinnedSource:sourceId,
    payload:firstPayload,
  })
  const firstDelivery = downloadResponse(first)
  if (firstDelivery.ok) return firstDelivery

  // A source can search successfully and still lose its media route before the
  // user selects a result. Walk the remaining managed chain until one source
  // both finds and delivers the same track.
  const query = [track.title, track.artist].filter(Boolean).join(' ').trim()
  if (!query) return firstDelivery

  const excluded = [sourceId]
  let lastOutcome = firstDelivery.outcome

  while (true) {
    const recovery = await ctx.executeSource({
      capability:'music',
      excludedSources:excluded,
      payload:{ action:'search', query },
    })
    if (recovery.status !== 'ok') return { ok:false, outcome:lastOutcome?.status ? lastOutcome : recovery }

    const result = recovery.result || {}
    const fallbackRaw = Array.isArray(result.items) ? result.items[0] : result.item
    if (!fallbackRaw) {
      excluded.push(recovery.source.id)
      lastOutcome = recovery
      continue
    }

    const fallbackTrack = normalizeTrack(fallbackRaw, 0)
    const fallbackDownload = await ctx.executeSource({
      capability:'music',
      pinnedSource:recovery.source.id,
      payload:{
        action:'download',
        itemId:fallbackTrack.id,
        item:fallbackTrack.raw || fallbackTrack,
        track:fallbackTrack.raw || fallbackTrack,
        quality:'source',
        delivery,
      },
    })
    const delivered = downloadResponse(fallbackDownload)
    if (delivered.ok) return delivered

    excluded.push(recovery.source.id)
    lastOutcome = delivered.outcome
  }
}

export async function downloadMusicQuery(ctx, query, {
  title = '',
  artist = '',
  album = '',
  delivery = 'audio',
} = {}) {
  const term = String(query || '').trim()
  if (!term) return { ok:false, outcome:{ status:'no-query' } }

  const outcome = await ctx.executeSource({
    capability:'music',
    payload:{ action:'search', query:term },
  })
  if (outcome.status !== 'ok') return { ok:false, outcome }

  const raw = Array.isArray(outcome.result?.items)
    ? outcome.result.items[0]
    : outcome.result?.item
  if (!raw) return { ok:false, outcome:{ status:'empty-search' } }

  const track = normalizeTrack({
    ...raw,
    title:raw?.title || title,
    artist:raw?.artist || artist,
    album:raw?.album || album,
  }, 0)

  return downloadTrack(ctx, {
    sourceId:outcome.source.id,
    track,
    delivery,
  })
}

async function handleNumbers(ctx) {
  const session = ctx.getCommandReplySession?.()
  if (
    !session ||
    session.kind !== 'number-selection' ||
    session.command !== 'song' ||
    !Array.isArray(session.entries)
  ) {
    return ctx.reply('That song selection expired. Run .song again.')
  }

  const spec = String(ctx.commandReplyInput || '').trim()
  const parsed = parseNumberSelection(spec, session.entries, {
    numberOf:track => track?.number,
    maxSelected:25,
  })
  if (!parsed.ok) {
    return ctx.reply('I could not match those song numbers. Try: 1 or 1,3,5 or 1-4')
  }

  if (parsed.selected.length === 1) {
    const chosen = parsed.selected[0]
    if (chosen?.cover && typeof ctx.sendImageUrl === 'function') {
      try {
        await ctx.sendImageUrl(chosen.cover, [
          `*${chosen.title}*`,
          chosen.artist ? `Artist: ${chosen.artist}` : '',
          chosen.album ? `Album: ${chosen.album}` : '',
        ].filter(Boolean).join('\n'))
      } catch (error) {
        console.warn('MSCC music cover failed:', error?.code || error?.name || 'Error')
      }
    }
  }

  // Progress messages make slow extractor/provider calls visible in WhatsApp.
  // One progress message is updated rather than sending per-source spam.
  let progress = null
  if (typeof ctx.progress === 'function') {
    try {
      progress = await ctx.progress(`🎵 Fetching ${parsed.selected.length === 1 ? parsed.selected[0].title : parsed.selected.length + ' songs'}…`)
    } catch (error) {
      console.warn('MSCC music progress failed:', error?.code || error?.name || 'Error')
    }
  }

  const fail = async outcome => {
    const message = musicFailureText(outcome)
    if (outcome?.error) {
      console.warn('MSCC music delivery failed:', {
        source:outcome.source?.id || '',
        status:outcome.status,
        reason:outcome.error?.code || outcome.error?.name || 'Error',
      })
    }
    if (progress) {
      try { await progress.fail(message); return true }
      catch (error) { console.warn('MSCC music failure progress unavailable:', error?.code || error?.name || 'Error') }
    }
    return ctx.reply(message)
  }

  const messages = []
  for (const track of parsed.selected) {
    let result
    try {
      result = await downloadTrack(ctx, {
        sourceId:session.sourceId,
        track,
        delivery:session.delivery || 'audio',
      })
    } catch (error) {
      return fail({ status:'source-error', source:{ id:session.sourceId, name:'Music' }, error })
    }
    if (!result.ok) return fail(result.outcome)
    if (result.text) messages.push(result.text)
  }

  // Keep one successful single-track choice available briefly for a retry
  // if WhatsApp did not actually display the attachment. A successful send
  // request is NOT proof that the recipient received or downloaded the file.
  if (parsed.selected.length === 1) {
    ctx.setCommandReplySession?.({
      ...session,
      lastSentAt:Date.now(),
      expiresAt:Date.now() + 5 * 60000,
    })
  } else {
    ctx.clearCommandReplySession?.()
  }
  if (progress) {
    try { await progress.done(`✅ ${parsed.selected.length === 1 ? 'Audio sent to WhatsApp. If missing, reply with the same number to retry.' : parsed.selected.length + ' audio files sent to WhatsApp.'}`) }
    catch (error) { console.warn('MSCC music success progress unavailable:', error?.code || error?.name || 'Error') }
  }

  if (messages.length === 1) return ctx.reply(messages[0])
  if (messages.length > 1) return ctx.reply(messages.join('\n'))
  if (parsed.selected.length > 1 && !progress) {
    return ctx.reply(`Sent ${parsed.selected.length} audio files to WhatsApp.`)
  }
  return true
}
export async function runSongCommand(ctx, { args = [] } = {}) {
  const first = String(args[0] || '')
  if (first === '~numbers') return handleNumbers(ctx)
  const parsed = parseSearchArgs(args)
  return search(ctx, parsed.query, { delivery:parsed.delivery })
}
