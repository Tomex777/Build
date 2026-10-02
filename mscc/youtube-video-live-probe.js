import {
  resolveYouTubeVideo,
  searchYouTubeVideos,
  selectVideoCandidate,
} from './youtube-video.js'
import { probeMedia } from './sources/music/_shared.js'

const videos = await searchYouTubeVideos('Rick Astley Never Gonna Give You Up', { limit:5 })
if (!videos.length) throw new Error('Live YouTube search returned no videos')

const selected = videos[0]
const resolved = await resolveYouTubeVideo(selected.id)
if (!resolved?.candidates?.length) throw new Error('Live YouTube resolve returned no usable video candidates')

const candidate = selectVideoCandidate(resolved.candidates, '720') ||
  selectVideoCandidate(resolved.candidates, 'best')
if (!candidate) throw new Error('Live YouTube resolve produced no selectable candidate')

const headers = { referer:resolved.details?.url || selected.url }
const videoProbe = await probeMedia(candidate.video.url, { headers, timeoutMs:20000 })
if (!videoProbe?.contentLength && !videoProbe?.contentRange) {
  throw new Error('Live YouTube video probe returned no length evidence')
}

let audioProbe = null
if (candidate.audio) {
  audioProbe = await probeMedia(candidate.audio.url, { headers, timeoutMs:20000 })
  if (!audioProbe?.contentLength && !audioProbe?.contentRange) {
    throw new Error('Live YouTube audio probe returned no length evidence')
  }
}

console.log(JSON.stringify({
  ok:true,
  videoId:selected.id,
  title:resolved.details?.title || selected.title,
  quality:candidate.height,
  kind:candidate.kind,
  video:{
    statusEvidence:videoProbe.contentRange || videoProbe.contentLength,
    mimetype:videoProbe.mimetype,
  },
  audio:audioProbe ? {
    statusEvidence:audioProbe.contentRange || audioProbe.contentLength,
    mimetype:audioProbe.mimetype,
  } : null,
}, null, 2))
