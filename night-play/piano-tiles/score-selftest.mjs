import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import vm from 'node:vm';
const box={window:{}};
for(const file of ['full-scores.js','song-library.js'])vm.runInNewContext(readFileSync(new URL(file,import.meta.url),'utf8'),box);
for(const [id,file,onsets] of [['bach-prelude','bach-prelude-c.mid',549],['fur-elise','fur-elise.mid',905]]){
 const score=box.window.NightPianoFullScores[id];
 const data=readFileSync(new URL('scores/'+file,import.meta.url));
 assert.equal(createHash('sha256').update(data).digest('hex'),score.sha256,'original MIDI retained unchanged');
 assert.equal(score.events.reduce((sum,e)=>sum+e.notes.length,0),onsets,'every source note retained');
 assert.ok(score.events.length>500&&score.duration>120,'complete composition, not a repeated theme');
 for(const [i,event] of score.events.entries()){assert.ok(event.notes.every(n=>Number.isInteger(n)&&n>=0&&n<=127));if(i)assert.ok(event.time>score.events[i-1].time)}
 const song=box.window.NightPianoSongLibrary.songs.find(s=>s.id===id),chart=box.window.NightPianoSongLibrary.arrangement(song);
 assert.equal(chart.length,score.events.length,'every onset creates a playable tile');
 assert.equal(chart.reduce((sum,n)=>sum+n.chord.length,0),onsets,'all bass and treble pitches reach the instrument');
}
assert.equal(box.window.NightPianoSongLibrary.songs.length,39);
console.log('PASS complete score provenance, all 1454 source notes, chords and 39 playable selections');
