// Run on approved staging/Azure host after Sharp and Noto fonts are provisioned.
// Example: node mscc/channel-branding/font-visual-probe.mjs /tmp/channel-visual
// Never claim actual WhatsApp posting from these screenshots.
import {createRequire} from 'node:module';
import {existsSync,mkdirSync,writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {renderUnicodeTextLayer,watermarkStaticImage} from './watermark.mjs';
const require=createRequire(import.meta.url);
const sharp=require('sharp');
const canvas=require('@napi-rs/canvas');
const output=process.argv[2]||'/tmp/night-channel-branding-visual';
mkdirSync(output,{recursive:true});
const emojiFont=process.env.NIGHT_CHANNEL_EMOJI_FONT||'/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf';
if(existsSync(emojiFont)){
  if(!canvas.GlobalFonts.registerFromPath(emojiFont,'Noto Color Emoji')){
    throw new Error('Unable to register approved Noto Color Emoji font');
  }
} else {
  console.warn('No explicit color emoji font path; visual result must be reviewed.');
}
canvas.GlobalFonts.loadSystemFonts?.();
const exact='NIGHT 👩🏽‍💻  🌙  Ọ̀rẹ́  🇳🇬  قناة';
const textPng=await renderUnicodeTextLayer({displayName:exact,width:800,height:144,tone:'light',canvasLibrary:canvas});
writeFileSync(join(output,'native-transparent-watermark.png'),textPng);
const raw=await sharp(textPng).ensureAlpha().raw().toBuffer();
let colored=0,ink=0;
for(let i=0;i<raw.length;i+=4){
  const a=raw[i+3], r=raw[i],g=raw[i+1],b=raw[i+2];
  if(a>50){
    ink++;
    if(Math.max(r,g,b)-Math.min(r,g,b)>55)colored++;
  }
}
const colorEmojiProbe=colored>=15;
// Inspect both contrast modes using the actual server-side renderer, never
// a Chromium substitute. The source remains synthetic and is NOT a post.
const backgrounds=[
  {name:'dark',base:'#28404f',hill:'#122b35',ink:'#fafafa',position:'bottom-right'},
  {name:'light',base:'#e8efed',hill:'#c1d3c9',ink:'#24333b',position:'top-left'}
];
const savedFiles=['native-transparent-watermark.png'];
if(colorEmojiProbe){
  for(const sample of backgrounds){
    const background=Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="680">'+
      '<rect width="100%" height="100%" fill="'+sample.base+'"/>'+
      '<path d="M0 680L500 190L800 580L1050 310L1200 680Z" fill="'+sample.hill+'"/>'+
      '<text x="500" y="90" fill="'+sample.ink+'" font-family="Noto Sans" font-size="33">Original demo image — NOT posted</text></svg>');
    const image=await sharp(background).png().toBuffer();
    const result=await watermarkStaticImage({input:image,channelId:'120363456789012345@newsletter',
      displayName:exact,position:sample.position,opacity:.25,colorEmojiVerified:true,
      canvasLibrary:canvas,sharpLibrary:sharp});
    const filename='native-composited-'+sample.name+'.png';
    writeFileSync(join(output,filename),result.buffer);
    savedFiles.push(filename);
  }
}
const report={
  exactUnicode:exact, codepoints:[...exact].map(ch=>ch.codePointAt(0).toString(16)),
  nativeColorPixels:colored,nontransparentPixels:ink,
  colorEmojiProbe:colorEmojiProbe?'LIKELY_COLOR':'FAILED_OR_MISSING',
  note:'A color-pixel test is not full glyph/ZWJ/RTL visual acceptance. Inspect both PNGs on exact host and get authorized WhatsApp test before enabling.',
  files:savedFiles
};
writeFileSync(join(output,'font-probe-report.json'),JSON.stringify(report,null,2));
console.log(JSON.stringify(report,null,2));
if(!colorEmojiProbe)process.exitCode=1;
