/* NIGHT Play repertoire: public-domain/traditional melodies and distinctly
 * labelled original NIGHT piano studies. These are note-playable arrangements,
 * not claimed recordings or licensed contemporary commercial tracks.
 */
(()=>{'use strict';
const classics=[{"id":"mary","title":"Mary Had a Little Lamb","artist":"Traditional","genre":"Traditional","difficulty":"Easy","notes":["E4","D4","C4","D4","E4","E4","E4","D4","D4","D4","E4","G4","G4","E4","D4","C4","D4","E4","E4","E4","E4","D4","D4","E4","D4","C4"]},{"id":"ode","title":"Ode to Joy","artist":"Ludwig van Beethoven","genre":"Classical","difficulty":"Easy","notes":["E4","E4","F4","G4","G4","F4","E4","D4","C4","C4","D4","E4","E4","D4","D4","E4","E4","F4","G4","G4","F4","E4","D4","C4","C4","D4","E4","D4","C4","C4"]},{"id":"jingle","title":"Jingle Bells","artist":"James Lord Pierpont","genre":"Traditional","difficulty":"Easy","notes":["E4","E4","E4","E4","E4","E4","E4","G4","C4","D4","E4","F4","F4","F4","F4","F4","E4","E4","E4","E4","D4","D4","E4","D4","G4","E4","E4","E4","E4","E4","E4","E4","G4","C4","D4","E4"]},{"id":"bridge","title":"London Bridge","artist":"English folk tune","genre":"Traditional","difficulty":"Easy","notes":["G4","A4","G4","F4","E4","F4","G4","D4","E4","F4","E4","F4","G4","G4","A4","G4","F4","E4","F4","G4","D4","G4","E4","C4"]},{"id":"row","title":"Row, Row, Row Your Boat","artist":"English folk song","genre":"Traditional","difficulty":"Easy","notes":["C4","C4","C4","D4","E4","E4","D4","E4","F4","G4","C5","C5","C5","G4","G4","G4","E4","E4","E4","C4","C4","C4","G4","F4","E4","D4","C4"]},{"id":"cross","title":"Hot Cross Buns","artist":"English folk tune","genre":"Traditional","difficulty":"Easy","notes":["E4","D4","C4","E4","D4","C4","C4","C4","C4","C4","D4","D4","D4","D4","E4","D4","C4","E4","D4","C4","C4","E4","G4","G4","E4","D4","C4"]},{"id":"baa","title":"Baa Baa Black Sheep","artist":"English folk tune","genre":"Traditional","difficulty":"Easy","notes":["C4","C4","G4","G4","A4","A4","G4","F4","F4","E4","E4","D4","D4","C4","G4","G4","F4","F4","E4","E4","D4","G4","G4","F4","F4","E4","E4","D4","C4","C4","G4","G4","A4","A4","G4","F4","F4","E4","E4","D4","D4","C4"]},{"id":"oldmac","title":"Old MacDonald Had a Farm","artist":"American folk song","genre":"Traditional","difficulty":"Easy","notes":["G4","G4","G4","D4","E4","E4","D4","B4","B4","A4","A4","G4","D4","G4","G4","G4","D4","E4","E4","D4","B4","B4","A4","A4","G4"]},{"id":"saints","title":"When the Saints Go Marching In","artist":"American spiritual","genre":"Traditional","difficulty":"Normal","notes":["C4","E4","F4","G4","C4","E4","F4","G4","C4","E4","F4","G4","E4","C4","E4","D4","C4","E4","G4","G4","F4","E4","F4","G4","E4","C4","D4","C4"]},{"id":"amazing","title":"Amazing Grace","artist":"John Newton / Traditional tune","genre":"Traditional","difficulty":"Normal","notes":["G4","C5","E5","C5","E5","D5","C5","A4","G4","C5","E5","C5","E5","D5","G5","E5","G5","E5","C5","A4","G4","C5","E5","C5","E5","D5","C5"]},{"id":"joy","title":"Joy to the World","artist":"George Frideric Handel / Lowell Mason","genre":"Classical","difficulty":"Normal","notes":["C5","B4","A4","G4","F4","E4","D4","C4","G4","A4","A4","B4","B4","C5","C5","C5","B4","A4","G4","F4","E4","D4","C4"]},{"id":"auld","title":"Auld Lang Syne","artist":"Traditional Scottish","genre":"Traditional","difficulty":"Normal","notes":["G4","C5","C5","C5","E5","D5","C5","D5","E5","C5","C5","E5","G5","A5","G5","E5","E5","C5","D5","E5","D5","C5","D5","G4","C5"]},{"id":"yankee","title":"Yankee Doodle","artist":"American traditional","genre":"Traditional","difficulty":"Normal","notes":["C4","C4","D4","E4","C4","E4","D4","G4","C4","C4","D4","E4","C4","B3","D4","C4","G4","C4","C4","D4","E4","F4","E4","D4","C4","B3","G4","A4","B4","C5"]},{"id":"cuckoo","title":"The Cuckoo","artist":"Traditional folk motif","genre":"Traditional","difficulty":"Normal","notes":["G4","E4","G4","E4","G4","A4","G4","E4","F4","D4","F4","D4","G4","E4","D4","C4","G4","E4","G4","E4","A4","G4","F4","E4","D4","C4"]}];
const originals=[{"id":"glass","title":"Glass Harbor","artist":"NIGHT Originals","genre":"Original","difficulty":"Normal","notes":["C4","E4","G4","B4","A4","G4","E4","D4","C4","E4","A4","G4","F4","E4","D4","C4"]},{"id":"moonlit","title":"Moonlit Steps","artist":"NIGHT Originals","genre":"Original","difficulty":"Easy","notes":["A3","C4","E4","A4","G4","E4","C4","B3","A3","C4","D4","E4","C4","B3","A3","G3"]},{"id":"solstice","title":"Solstice","artist":"NIGHT Originals","genre":"Original","difficulty":"Hard","notes":["D4","F4","A4","D5","C5","A4","F4","E4","D4","A4","C5","D5","C5","A4","G4","F4"]},{"id":"dawn","title":"Echoes at Dawn","artist":"NIGHT Originals","genre":"Original","difficulty":"Normal","notes":["E4","G4","B4","C5","B4","G4","E4","D4","C4","E4","G4","E5","D5","B4","G4","E4"]},{"id":"cloudline","title":"Cloudline","artist":"NIGHT Originals","genre":"Original","difficulty":"Normal","notes":["F4","A4","C5","E5","D5","C5","A4","G4","F4","C5","D5","A4","G4","A4","F4","E4"]},{"id":"silver","title":"Silver Current","artist":"NIGHT Originals","genre":"Original","difficulty":"Hard","notes":["G4","B4","D5","G5","F5","D5","B4","A4","G4","B4","C5","D5","B4","A4","G4","E4"]},{"id":"afterglow","title":"Afterglow","artist":"NIGHT Originals","genre":"Original","difficulty":"Easy","notes":["C4","D4","E4","G4","A4","G4","E4","D4","F4","A4","G4","E4","D4","C4","D4","C4"]},{"id":"northern","title":"Northern Lights","artist":"NIGHT Originals","genre":"Original","difficulty":"Hard","notes":["E4","F4","A4","C5","E5","C5","A4","F4","G4","B4","D5","E5","D5","B4","G4","E4"]},{"id":"firefly","title":"Firefly Waltz","artist":"NIGHT Originals","genre":"Original","difficulty":"Normal","notes":["G3","B3","D4","G4","A4","G4","D4","B3","C4","E4","G4","C5","B4","G4","E4","C4"]},{"id":"quiet","title":"Quiet Street","artist":"NIGHT Originals","genre":"Original","difficulty":"Easy","notes":["A3","D4","F4","A4","G4","F4","D4","C4","A3","C4","E4","A4","F4","E4","D4","A3"]},{"id":"horizon","title":"Last Horizon","artist":"NIGHT Originals","genre":"Original","difficulty":"Hard","notes":["C4","G4","C5","E5","D5","C5","G4","E4","F4","A4","C5","F5","E5","C5","A4","F4"]},{"id":"rain","title":"Gentle Rain","artist":"NIGHT Originals","genre":"Original","difficulty":"Easy","notes":["D4","F4","A4","F4","E4","D4","C4","A3","D4","E4","G4","F4","E4","D4","C4","D4"]},{"id":"coast","title":"Coastal Drift","artist":"NIGHT Originals","genre":"Original","difficulty":"Normal","notes":["F4","G4","A4","C5","D5","C5","A4","G4","F4","A4","C5","E5","C5","A4","G4","F4"]},{"id":"nightfall","title":"Nightfall","artist":"NIGHT Originals","genre":"Original","difficulty":"Hard","notes":["E4","B4","G4","E4","D4","F4","A4","C5","B4","G4","F4","E4","B3","D4","E4","G4"]},{"id":"star","title":"Starbound","artist":"NIGHT Originals","genre":"Original","difficulty":"Expert","notes":["C4","E4","G4","C5","E5","G5","E5","C5","D5","B4","G4","E4","F4","A4","C5","D5"]},{"id":"velvet","title":"Velvet Skies","artist":"NIGHT Originals","genre":"Original","difficulty":"Hard","notes":["A3","C4","E4","G4","B4","A4","G4","E4","F4","A4","C5","E5","D5","C5","A4","F4"]},{"id":"paper","title":"Paper Lanterns","artist":"NIGHT Originals","genre":"Original","difficulty":"Normal","notes":["G4","A4","B4","D5","C5","B4","A4","G4","E4","G4","B4","E5","D5","B4","G4","E4"]},{"id":"neon","title":"Fading Neon","artist":"NIGHT Originals","genre":"Original","difficulty":"Expert","notes":["D4","A4","F4","D4","F4","A4","C5","B4","G4","B4","D5","F5","E5","D5","A4","F4"]},{"id":"willow","title":"Willow Song","artist":"NIGHT Originals","genre":"Original","difficulty":"Easy","notes":["C4","D4","F4","G4","A4","G4","F4","D4","E4","G4","B4","C5","B4","G4","E4","C4"]},{"id":"bluehour","title":"Blue Hour","artist":"NIGHT Originals","genre":"Original","difficulty":"Normal","notes":["F4","A4","E5","D5","C5","A4","F4","E4","D4","F4","A4","C5","A4","G4","F4","D4"]}];
const songs=[
 {id:"endless",title:"Endless Piano",artist:"NIGHT Originals",genre:"Endless",difficulty:"Normal",infinite:true},
 {id:"frere",title:"Frère Jacques",artist:"French folk song",genre:"Traditional",difficulty:"Easy",legacy:true},
 {id:"twinkle",title:"Twinkle, Twinkle",artist:"English folk song",genre:"Traditional",difficulty:"Easy",legacy:true},
 {id:"bach-prelude",title:"Prelude in C major, BWV 846",artist:"Johann Sebastian Bach",genre:"Classical",difficulty:"Hard",score:window.NightPianoFullScores?.["bach-prelude"]},
 {id:"fur-elise",title:"Für Elise, WoO 59",artist:"Ludwig van Beethoven",genre:"Classical",difficulty:"Expert",score:window.NightPianoFullScores?.["fur-elise"]},
 ...classics,...originals
];
// Complete familiar melody forms; the remaining repertoire is explicitly
// presented as short melody studies rather than full classical compositions.
const completeMelodies={
 frere:['C4 D4 E4 C4 C4 D4 E4 C4 E4 F4 G4 E4 F4 G4 G4 A4 G4 F4 E4 C4 G4 A4 G4 F4 E4 C4 C4 G3 C4 C4 G3 C4', [10,13,19,25,28,31]],
 twinkle:['C4 C4 G4 G4 A4 A4 G4 F4 F4 E4 E4 D4 D4 C4 G4 G4 F4 F4 E4 E4 D4 G4 G4 F4 F4 E4 E4 D4 C4 C4 G4 G4 A4 A4 G4 F4 F4 E4 E4 D4 D4 C4', [6,13,20,27,34,41]],
 ode:['E4 E4 F4 G4 G4 F4 E4 D4 C4 C4 D4 E4 E4 D4 D4 E4 E4 F4 G4 G4 F4 E4 D4 C4 C4 D4 E4 D4 C4 C4 D4 D4 E4 C4 D4 E4 F4 E4 C4 D4 E4 F4 E4 D4 C4 D4 G3 E4 E4 F4 G4 G4 F4 E4 D4 C4 C4 D4 E4 D4 C4 C4', [14,29,46,61]],
 mary:['E4 D4 C4 D4 E4 E4 E4 D4 D4 D4 E4 G4 G4 E4 D4 C4 D4 E4 E4 E4 E4 D4 D4 E4 D4 C4', [6,9,12,25]],
 bridge:['G4 A4 G4 F4 E4 F4 G4 D4 E4 F4 E4 F4 G4 G4 A4 G4 F4 E4 F4 G4 D4 G4 E4 C4', [6,9,12,19,23]],
 baa:['C4 C4 G4 G4 A4 A4 G4 F4 F4 E4 E4 D4 D4 C4 G4 G4 F4 F4 E4 E4 D4 G4 G4 F4 F4 E4 E4 D4 C4 C4 G4 G4 A4 A4 G4 F4 F4 E4 E4 D4 D4 C4', [6,13,20,27,34,41]],
 row:['C4 C4 C4 D4 E4 E4 D4 E4 F4 G4 C5 C5 C5 G4 G4 G4 E4 E4 E4 C4 C4 C4 G4 F4 E4 D4 C4', [4,9,26]]
};
for(const song of songs){
 const complete=completeMelodies[song.id];
 if(complete){song.notes=complete[0].split(' ');song.holdIndices=complete[1];song.form='Complete melody';song.legacy=false;}
 else song.form=song.score?'Complete score · adapted':song.infinite?'Endless':song.genre==='Original'?'Original study':'Melody excerpt';
}
const tones={C:0,D:2,E:4,F:5,G:7,A:9,B:11};
function hz(token){
 const match=/^([A-G])([#b]?)([2-6])$/.exec(token);
 if(!match)throw Error("Invalid playable note "+token);
 const midi=(Number(match[3])+1)*12+tones[match[1]]+(match[2]==="#"?1:match[2]==="b"?-1:0);
 return 440*Math.pow(2,(midi-69)/12);
}
function arrangement(song){
 if(song?.score){
  return song.score.events.map((event,i,all)=>{
   const gap=(all[i+1]?.time??song.score.duration)-event.time;
   const hold=gap>.65;
   const chord=event.notes.map(midi=>440*Math.pow(2,(midi-69)/12));
   return {lane:[0,2,1,3,0,3,1,2][i%8],freq:chord.at(-1),chord,at:1.35+event.time,
    duration:hold?Math.min(3,gap-.35):0,hold,state:"waiting",pointer:null,start:0,scoreGiven:false,voice:null,flash:0};
  });
 }

 if(!song?.notes?.length)throw Error("Missing playable song arrangement");
 // Traditional songs follow their named melody; original arrangements have
 // evolving phrases and cadences so they aren't one endlessly cloned bar.
 const tune=song.notes.map(hz),original=song.genre==="Original";
 // Develop original studies with a contrasting middle phrase, then reprise
 // the opening and resolve on the tonic. Named melodies play their actual form.
 const bridge=original?tune.map((_,i)=>tune[(i+8)%tune.length]):[];
 const full=original?[...tune,...bridge,...tune]:tune;
 const patterns=[0,2,1,3,0,3,1,2,0,2,3,1,2,0,3,1];
 let prev=-1;
 return full.map((freq,i)=>{
  let lane=patterns[(i+song.id.length*3)%patterns.length];
  if(lane===prev)lane=(lane+1)%4;
  prev=lane;
  const hold=song.holdIndices?song.holdIndices.includes(i):i>3 && (i+song.id.length)%17===16;
  return {lane,freq,at:1.35+i*.59,duration:hold?.68:0,hold,state:"waiting",pointer:null,start:0,scoreGiven:false,voice:null,flash:0};
 });
}
window.NightPianoSongLibrary=Object.freeze({songs:Object.freeze(songs),arrangement});
})();
