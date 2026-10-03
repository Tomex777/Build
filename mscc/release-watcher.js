function num(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

function clean(value, max = 180) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, max)
}

export function releaseFingerprint(state = {}) {
  const kind = String(state?.kind || '')
  if (kind === 'episode') {
    return 'episode:' + num(state.season) + ':' + num(state.number)
  }
  if (kind === 'movie') {
    return 'movie:' + (num(state.number) > 0 ? 1 : 0) + ':' + num(state.releasedAtMs)
  }
  if (kind === 'chapter') {
    return 'chapter:' + num(state.number)
  }
  return ''
}

export function isNewerRelease(previous = {}, current = {}) {
  const prevKind = String(previous?.kind || '')
  const kind = String(current?.kind || '')
  if (!kind || prevKind !== kind) return false

  if (kind === 'episode') {
    const prevSeason = num(previous.season)
    const season = num(current.season)
    const prevNumber = num(previous.number)
    const number = num(current.number)

    if (season > prevSeason) return number > 0
    if (season < prevSeason) return false
    return number > prevNumber
  }

  if (kind === 'movie') {
    return num(previous.number) <= 0 && num(current.number) > 0
  }

  if (kind === 'chapter') {
    return num(current.number) > num(previous.number)
  }

  return false
}

export function releaseNotificationText(item = {}, state = {}) {
  const title = clean(item?.title || 'Untitled')
  const kind = String(state?.kind || '')

  if (kind === 'episode') {
    const episode = num(state.number)
    const season = num(state.season)
    if (!episode) return ''
    return season > 0
      ? 'Latest episode of ' + title + ': Season ' + season + ', Episode ' + episode
      : 'Latest episode of ' + title + ': Episode ' + episode
  }

  if (kind === 'chapter') {
    const chapter = num(state.number)
    return chapter > 0
      ? 'Latest chapter of ' + title + ': Chapter ' + chapter
      : ''
  }

  if (kind === 'movie' && num(state.number) > 0) {
    return title + ' is out now.'
  }

  return ''
}

export function createReleaseWatcher({
  storage,
  resolveReleaseState,
  sendDm,
  now = () => Date.now(),
} = {}) {
  if (!storage) throw new Error('Release watcher storage is required')
  if (typeof resolveReleaseState !== 'function') throw new Error('Release resolver is required')
  if (typeof sendDm !== 'function') throw new Error('Release DM sender is required')

  async function prime(item) {
    if (!item?.userKey || !item?.itemKey) return { ok:false, reason:'invalid' }
    const current = await resolveReleaseState(item)
    if (!current) return { ok:false, reason:'unavailable' }

    storage.setLibraryReleaseState(item.userKey, item.itemKey, {
      cursor:current,
      fingerprint:releaseFingerprint(current),
      primedAtMs:now(),
      checkedAtMs:now(),
      notifiedAtMs:0,
    })
    return { ok:true, state:current }
  }

  async function checkItem(item) {
    if (!item?.watchReleases || !item?.userKey || !item?.itemKey) {
      return { ok:false, reason:'not-watched' }
    }

    const current = await resolveReleaseState(item)
    if (!current) return { ok:false, reason:'unavailable' }

    const saved = storage.getLibraryReleaseState(item.userKey, item.itemKey)
    if (!saved || !saved.cursor) {
      storage.setLibraryReleaseState(item.userKey, item.itemKey, {
        cursor:current,
        fingerprint:releaseFingerprint(current),
        primedAtMs:now(),
        checkedAtMs:now(),
        notifiedAtMs:0,
      })
      return { ok:true, primed:true, notified:false }
    }

    if (!isNewerRelease(saved.cursor, current)) {
      storage.setLibraryReleaseState(item.userKey, item.itemKey, {
        ...saved,
        checkedAtMs:now(),
      })
      return { ok:true, notified:false }
    }

    const text = releaseNotificationText(item, current)
    if (!text) return { ok:false, reason:'no-message' }

    const sent = await sendDm(item, text, current)
    if (!sent) {
      return { ok:false, reason:'send-failed', notified:false }
    }

    storage.setLibraryReleaseState(item.userKey, item.itemKey, {
      cursor:current,
      fingerprint:releaseFingerprint(current),
      primedAtMs:Number(saved.primedAtMs || 0) || now(),
      checkedAtMs:now(),
      notifiedAtMs:now(),
    })
    return { ok:true, notified:true, text, state:current }
  }

  async function checkOnce() {
    const items = storage.listWatchedLibraryItems?.() || []
    const results = []
    for (const item of items) {
      try {
        results.push({ item, ...(await checkItem(item)) })
      } catch (error) {
        results.push({
          item,
          ok:false,
          reason:'error',
          error:String(error?.message || error),
        })
      }
    }
    return results
  }

  return {
    prime,
    checkItem,
    checkOnce,
  }
}
