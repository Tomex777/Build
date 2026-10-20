#!/usr/bin/env node
const base='https://www.shadow-garden-mog.jp/wp-content/themes/officialsite/partials/page/dmg/assets/images/';
const art=['img_shadow-adult.png','img_sid-adult.png','img_alpha-adult.png','img_beta-adult.png','img_gamma-adult.png','img_delta-adult.png','img_epsilon-adult.png','img_zeta-adult.png','img_eta-adult.png','img_aurora.png','img_beatrix.png'];
const more=[
'https://shadow-garden.jp/2nd/assets/img/character/chara2_alpha__thumb.png',
'https://shadow-garden.jp/1st/assets/img/character/chara2_thumb.png',
'https://www.shadow-garden-mog.jp/wp-content/themes/officialsite/images/apocrypha/character1-image1.png'
];
for(const url of [...art.map(x=>base+x),...more]){
  try{
   const res=await fetch(url,{signal:AbortSignal.timeout(12000)});
   const mime=res.headers.get('content-type'),length=res.headers.get('content-length'),buf=Buffer.from(await res.arrayBuffer());
   const png=buf.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]));
   const info=png&&buf.length>26?{width:buf.readUInt32BE(16),height:buf.readUInt32BE(20),colorType:buf[25]}:{};
   console.log(JSON.stringify({url,status:res.status,mime,length,bytes:buf.length,png,...info}));
  }catch(e){console.log(JSON.stringify({url,error:e.message}));}
}

for(const edition of ['1st','2nd']){
 const url='https://shadow-garden.jp/'+edition+'/assets/js/jquery.character.js';
 const response=await fetch(url+'?1792536229',{signal:AbortSignal.timeout(15000)});
 const content=await response.text();
 console.log('MODAL SCRIPT',edition,response.status,content.length,content.slice(0,13000));
}

for(const url of [
 'https://shadow-garden.jp/2nd/character/data/chara2_alpha.php',
 'https://shadow-garden.jp/2nd/character/data/chara1_cid.php',
 'https://shadow-garden.jp/1st/character/chara1.php',
 'https://shadow-garden.jp/1st/character/chara2.php'
]){
 const response=await fetch(url,{signal:AbortSignal.timeout(15000)});
 const body=await response.text();
 console.log('CHARACTER MODAL',url,response.status,body.length,body.slice(0,4300));
}
