import { spawn } from 'node:child_process'
import { mkdtemp, rm, stat } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import {
  listEpisodes,
  listSeasons,
  qualityList,
  resolveStream,
  searchTitles,
} from './providers/streamingunity.js'

const UA='Mozilla/5.0 (Linux; Android 16; SM-A165F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36'
const HEADERS='Referer: https://vixcloud.co/\r\nOrigin: https://vixcloud.co\r\nUser-Agent: '+UA+'\r\n'

function run(command,args,timeoutMs=120000){
  return new Promise((resolve,reject)=>{
    const child=spawn(command,args,{stdio:['ignore','pipe','pipe']})
    let stdout='',stderr=''
    child.stdout.on('data',c=>{stdout+=String(c)})
    child.stderr.on('data',c=>{stderr+=String(c)})
    const timer=setTimeout(()=>{child.kill('SIGKILL');reject(new Error(command+' timed out'))},timeoutMs)
    child.once('error',e=>{clearTimeout(timer);reject(e)})
    child.once('close',code=>{
      clearTimeout(timer)
      if(code===0) resolve({stdout,stderr})
      else reject(new Error(command+' failed ('+code+'): '+stderr.slice(-1000)))
    })
  })
}

async function proveSample(label,stream){
  if(!stream.masterText.includes('#EXTM3U') || !stream.variants.length) {
    throw new Error(label+' returned no playable HLS variants')
  }
  const root=await mkdtemp(join(tmpdir(),'mscc-streamingunity-live-'))
  const sample=join(root,'sample.mkv')
  try{
    await run('ffmpeg',[
      '-hide_banner','-loglevel','error',
      '-headers',HEADERS,
      '-i',stream.masterUrl,
      '-map','0:v:0','-map','0:a:0?',
      '-t','8','-c','copy','-y',sample,
    ],120000)
    const size=(await stat(sample)).size
    if(size<4096) throw new Error(label+' sample remux was empty')
    const probe=await run('ffprobe',[
      '-v','error',
      '-show_entries','format=duration,size:stream=codec_type,codec_name,width,height',
      '-of','json',sample,
    ],30000)
    const json=JSON.parse(probe.stdout||'{}')
    const duration=Number(json?.format?.duration||0)
    if(!(duration>0) || !(json?.streams||[]).some(row=>row.codec_type==='video')) {
      throw new Error(label+' sample did not reopen')
    }
    return {
      variants:stream.variants.map(row=>({width:row.width,height:row.height,bandwidth:row.bandwidth})),
      qualities:qualityList(stream),
      sample:{bytes:size,duration,streams:json.streams},
    }
  }finally{
    await rm(root,{recursive:true,force:true}).catch(()=>{})
  }
}

const tvResults=await searchTitles('House','tv')
const house=tvResults.find(row=>row.title.toLowerCase()==='house') || tvResults[0]
if(!house) throw new Error('StreamingUnity search returned no House result')

const seasons=await listSeasons(house)
const season=seasons.find(row=>row.number===1) || seasons[0]
if(!season) throw new Error('StreamingUnity returned no House seasons')

const episodes=await listEpisodes(house,season.number)
const episode=episodes.find(row=>Number(row.number)===1) || episodes[0]
if(!episode) throw new Error('StreamingUnity returned no House episodes')

const tvStream=await resolveStream(house,episode.id)
const tvProof=await proveSample('StreamingUnity TV',tvStream)

const movieResults=await searchTitles('Night of the Living Dead','movie')
const night=movieResults.find(row=>
  row.title.toLowerCase()==='night of the living dead' && String(row.description)==='1968'
) || movieResults.find(row=>row.title.toLowerCase()==='night of the living dead') || movieResults[0]
if(!night) throw new Error('StreamingUnity search returned no Night of the Living Dead movie result')

const movieStream=await resolveStream(night)
const movieProof=await proveSample('StreamingUnity movie',movieStream)

console.log(JSON.stringify({
  tv:{
    searchResults:tvResults.length,
    selected:{id:house.id,title:house.title},
    seasons:seasons.length,
    episodes:episodes.length,
    episode:{id:episode.id,number:episode.number,title:episode.title},
    ...tvProof,
    complete:true,
  },
  movie:{
    searchResults:movieResults.length,
    selected:{id:night.id,title:night.title,year:night.description},
    ...movieProof,
    complete:true,
  },
  complete:true,
},null,2))
