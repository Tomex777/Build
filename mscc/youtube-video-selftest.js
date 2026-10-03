import {
  buildVideoCandidates,
  parseYouTubeVideoId,
  selectVideoCandidate,
  youtubeSearchItems,
} from './youtube-video.js'

const fixture = {
  contents:[
    {
      videoRenderer:{
        videoId:'abcdefghijk',
        title:{ runs:[{ text:'Example Video' }] },
        lengthText:{ simpleText:'4:20' },
        ownerText:{ runs:[{ text:'Example Channel' }] },
        viewCountText:{ simpleText:'1M views' },
        publishedTimeText:{ simpleText:'1 year ago' },
        thumbnail:{ thumbnails:[{ url:'https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg' }] },
      },
    },
  ],
}

const search = youtubeSearchItems(fixture)
if (search.length !== 1) throw new Error('YouTube search fixture did not produce one video')
if (search[0].id !== 'abcdefghijk') throw new Error('YouTube video id was not preserved')
if (search[0].channel !== 'Example Channel') throw new Error('YouTube channel was not preserved')
if (search[0].duration !== '4:20') throw new Error('YouTube duration was not preserved')


const urlCases = new Map([
  ['https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'dQw4w9WgXcQ'],
  ['https://youtu.be/dQw4w9WgXcQ', 'dQw4w9WgXcQ'],
  ['https://www.youtube.com/shorts/dQw4w9WgXcQ', 'dQw4w9WgXcQ'],
  ['https://www.youtube.com/live/dQw4w9WgXcQ?feature=share', 'dQw4w9WgXcQ'],
  ['https://www.youtube.com/embed/dQw4w9WgXcQ', 'dQw4w9WgXcQ'],
])
for (const [url, expected] of urlCases) {
  if (parseYouTubeVideoId(url) !== expected) throw new Error('YouTube URL parsing failed for ' + url)
}
if (parseYouTubeVideoId('https://example.com/watch?v=dQw4w9WgXcQ')) throw new Error('Non-YouTube URL should not parse')

const formats = [
  {
    itag:18,
    url:'https://example.invalid/360.mp4',
    mimeType:'video/mp4',
    codecs:'avc1, mp4a',
    container:'mp4',
    height:360,
    bitrate:500000,
    hasVideo:true,
    hasAudio:true,
  },
  {
    itag:136,
    url:'https://example.invalid/720.mp4',
    mimeType:'video/mp4',
    codecs:'avc1',
    container:'mp4',
    height:720,
    bitrate:1500000,
    hasVideo:true,
    hasAudio:false,
  },
  {
    itag:137,
    url:'https://example.invalid/1080.mp4',
    mimeType:'video/mp4',
    codecs:'avc1',
    container:'mp4',
    height:1080,
    bitrate:3000000,
    hasVideo:true,
    hasAudio:false,
  },
  {
    itag:140,
    url:'https://example.invalid/audio.m4a',
    mimeType:'audio/mp4',
    codecs:'mp4a',
    container:'mp4',
    bitrate:128000,
    hasVideo:false,
    hasAudio:true,
  },
]

const candidates = buildVideoCandidates(formats)
if (candidates.map(item => item.height).join(',') !== '360,720,1080') {
  throw new Error('YouTube candidate qualities are incorrect')
}
if (candidates[0].kind !== 'progressive') throw new Error('360p should use progressive media')
if (candidates[1].kind !== 'adaptive') throw new Error('720p should use adaptive video + audio')
if (!candidates[1].audio) throw new Error('Adaptive YouTube quality is missing audio')

if (selectVideoCandidate(candidates, 'best')?.height !== 1080) throw new Error('Best quality selection failed')
if (selectVideoCandidate(candidates, '720p')?.height !== 720) throw new Error('Exact quality selection failed')
if (selectVideoCandidate(candidates, '480')?.height !== 360) throw new Error('Quality fallback should choose the nearest lower quality')
if (selectVideoCandidate(candidates, '4k')?.height !== 1080) throw new Error('4K fallback should choose the best available quality')

console.log('PASS YouTube search, quality selection, progressive and adaptive pairing')
