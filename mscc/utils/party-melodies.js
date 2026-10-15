// Synthesized short clips of traditional / public-domain melodies only.
// Never download or republish commercial recordings.
import { mkdtemp,readFile,writeFile,rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { runFfmpeg } from './media-conversion.js'
import { SONGS } from './party-games.js'

const RATE=16000
const SCALE={C:0,'C#':1,Db:1,D:2,'D#':3,Eb:3,E:4,F:5,'F#':6,Gb:6,G:7,'G#':8,Ab:8,A:9,'A#':10,Bb:10,B:11}
const clipCache=new Map()

export function noteFrequency(note) {
  const match=/^([A-G](?:#|b)?)([3-6])$/.exec(note)
  if (!match || SCALE[match[1]]===undefined) throw new Error('Invalid melody note '+note)
  const midi=12*(Number(match[2])+1)+SCALE[match[1]]
  return 440*Math.pow(2,(midi-69)/12)
}
export function synthesizeMelody(notes,{rate=RATE,duration=0.34}={}) {
  if (!Array.isArray(notes) || notes.length<1 || notes.length>32) throw new Error('Invalid notes')
  const each=Math.round(rate*duration)
  const bytes=Buffer.allocUnsafe(44+notes.length*each*2)
  bytes.write('RIFF',0)
  bytes.writeUInt32LE(bytes.length-8,4)
  bytes.write('WAVEfmt ',8)
  bytes.writeUInt32LE(16,16)
  bytes.writeUInt16LE(1,20)
  bytes.writeUInt16LE(1,22)
  bytes.writeUInt32LE(rate,24)
  bytes.writeUInt32LE(rate*2,28)
  bytes.writeUInt16LE(2,32)
  bytes.writeUInt16LE(16,34)
  bytes.write('data',36)
  bytes.writeUInt32LE(notes.length*each*2,40)
  notes.forEach((note,j)=>{
    const freq=noteFrequency(note)
    for(let i=0;i<each;i++){
      const t=i/rate
      const envelope=Math.min(1,i/(rate*.016))*Math.min(1,(each-i)/(rate*.07))
      const wave=(Math.sin(2*Math.PI*freq*t)+.23*Math.sin(2*Math.PI*freq*2*t))*envelope
      const sample=Math.max(-1,Math.min(1,wave*.49))
      bytes.writeInt16LE(Math.round(sample*32767),44+(j*each+i)*2)
    }
  })
  return bytes
}
export async function melodyAudio(songId) {
  if (clipCache.has(songId)) return clipCache.get(songId)
  const song=SONGS.find(x=>x.id===songId)
  if (!song) throw new Error('Unknown song round')
  const directory=await mkdtemp(join(tmpdir(),'night-guesssong-'))
  try {
    const input=join(directory,'melody.wav'),out=join(directory,'snippet.mp3')
    await writeFile(input,synthesizeMelody(song.notes.split(/\s+/)))
    await runFfmpeg(['-i',input,'-vn','-c:a','libmp3lame','-b:a','64k',out],{timeoutMs:25000})
    const output=await readFile(out)
    if (!output.length||output.length>600000) throw new Error('Invalid song snippet')
    if (clipCache.size>=20) clipCache.clear()
    clipCache.set(songId,output)
    return output
  } finally { await rm(directory,{recursive:true,force:true}).catch(()=>{}) }
}
