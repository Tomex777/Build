const ITUNES = 'https://itunes.apple.com'

function clean(value, max = 300) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, max)
}

async function json(url) {
  const response = await fetch(url, {
    headers:{ 'user-agent':'Night/2.3', accept:'application/json' },
    signal:AbortSignal.timeout(15000),
  })
  const data = await response.json().catch(() => null)
  if (!response.ok) throw new Error('Album catalog returned HTTP ' + response.status + '.')
  return data
}

export async function searchAlbums(query, limit = 10) {
  const term = clean(query, 180)
  if (!term) return []
  const params = new URLSearchParams({
    term,
    entity:'album',
    media:'music',
    limit:String(Math.max(1, Math.min(20, Number(limit) || 10))),
  })
  const data = await json(ITUNES + '/search?' + params)
  return (data?.results || []).map(row => ({
    id:String(row.collectionId || ''),
    title:clean(row.collectionName, 180),
    artist:clean(row.artistName, 140),
    artwork:String(row.artworkUrl100 || '').replace('100x100', '600x600'),
    trackCount:Number(row.trackCount || 0) || 0,
    releaseDate:String(row.releaseDate || ''),
    genre:clean(row.primaryGenreName, 80),
  })).filter(row => row.id && row.title)
}

export async function albumTracks(collectionId) {
  const id = String(collectionId || '').replace(/\D/g,'')
  if (!id) throw new Error('Album ID is missing.')
  const data = await json(ITUNES + '/lookup?id=' + encodeURIComponent(id) + '&entity=song&limit=250')
  const rows = Array.isArray(data?.results) ? data.results : []
  const albumRow = rows.find(row => row.wrapperType === 'collection')
  const trackRows = rows.filter(row => row.wrapperType === 'track' && row.kind === 'song')
  if (!albumRow || !trackRows.length) throw new Error('That album has no track list in the catalog.')

  return {
    id:String(albumRow.collectionId || id),
    title:clean(albumRow.collectionName, 180),
    artist:clean(albumRow.artistName, 140),
    artwork:String(albumRow.artworkUrl100 || '').replace('100x100', '600x600'),
    trackCount:Number(albumRow.trackCount || trackRows.length) || trackRows.length,
    releaseDate:String(albumRow.releaseDate || ''),
    genre:clean(albumRow.primaryGenreName, 80),
    tracks:trackRows
      .sort((a,b) => Number(a.trackNumber || 0) - Number(b.trackNumber || 0))
      .map((row,index) => ({
        id:String(row.trackId || index + 1),
        number:String(row.trackNumber || index + 1),
        title:clean(row.trackName, 180),
        artist:clean(row.artistName || albumRow.artistName, 140),
        album:clean(row.collectionName || albumRow.collectionName, 180),
        durationSeconds:Math.round(Number(row.trackTimeMillis || 0) / 1000) || 0,
        cover:String(row.artworkUrl100 || albumRow.artworkUrl100 || '').replace('100x100','600x600'),
      }))
      .filter(row => row.title),
  }
}
