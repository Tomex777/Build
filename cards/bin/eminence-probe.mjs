#!/usr/bin/env node
import sharp from 'sharp';
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

for(const url of [
 'https://shadow-garden.jp/1st/assets/img/character/chara1_main2.png',
 'https://shadow-garden.jp/1st/assets/img/character/chara1_main1.png',
 'https://shadow-garden.jp/1st/assets/img/character/chara2_main1.png',
 'https://shadow-garden.jp/2nd/assets/img/character/chara2_alpha__main1.png',
 'https://shadow-garden.jp/2nd/assets/img/character/chara3_beta__main1.png',
 'https://shadow-garden.jp/2nd/assets/img/character/chara5_delta__main1.png',
 'https://shadow-garden.jp/2nd/assets/img/character/chara14_elisabeth__main1.png'
]){
 const response=await fetch(url,{signal:AbortSignal.timeout(15000)});
 const bytes=Buffer.from(await response.arrayBuffer());
 const png=bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]));
 console.log('FULL PORTRAIT',JSON.stringify({url,status:response.status,bytes:bytes.length,png,width:png?bytes.readUInt32BE(16):null,height:png?bytes.readUInt32BE(20):null,colorType:png?bytes[25]:null}));
}

for(const group of ['shadow-adult','sid-adult','alpha-adult','beta-adult','gamma-adult','delta-adult','epsilon-adult','zeta-adult','eta-adult','aurora','beatrix','alexia','rose','iris','clare']){
 for(const view of ['pc','sp']){
  const url='https://www.shadow-garden-mog.jp/wp-content/themes/officialsite/partials/page/dmg/assets/webp/'+view+'/chara/img_'+group+'.webp';
  const response=await fetch(url,{signal:AbortSignal.timeout(12000)}).catch(e=>({status:0,error:e.message,headers:new Map(),arrayBuffer:async()=>new ArrayBuffer(0)}));
  const bytes=Buffer.from(await response.arrayBuffer());
  try{
   const m=await sharp(bytes).metadata();
   console.log('WEBP PORTRAIT',JSON.stringify({name:group,view,url,status:response.status,bytes:bytes.length,width:m.width,height:m.height,alpha:m.hasAlpha,format:m.format}));
  }catch(e){
   console.log('WEBP PORTRAIT',JSON.stringify({name:group,view,url,status:response.status,bytes:bytes.length,error:e.message.slice(0,90)}));
  }
 }
}
