import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { createCanvas, loadImage } from '@napi-rs/canvas'
import { renderCard } from './utils/artist-edition-renderer.js'
import {
  EDITIONS, parseCardRequest, lookupAnimeCharacter, imageAsset,
  editionDefinition, makeEditionSvg, renderEditionPng,
} from './utils/anime-collectible-cards.js'

async function main() {
  assert.deepEqual(EDITIONS, ['nocturne','tidelight','verdant'])
  assert.deepEqual(parseCardRequest(['Naruto','Uzumaki']), {
    query:'Naruto Uzumaki', edition:'all', side:'front', asFile:false, characterId:0,
  })
  assert.deepEqual(parseCardRequest(['All','Might','--nocturne','--file']), {
    query:'All Might', edition:'nocturne', side:'front', asFile:true, characterId:0,
  })
  assert.equal(parseCardRequest(['Gojo','Satoru','--back','--verdant']).side,'back')
  assert.equal(parseCardRequest(['--id=12','--tidelight']).characterId,12)
  assert.throws(()=>parseCardRequest(['Naruto','--magical']), /Unknown/)
  assert.throws(()=>parseCardRequest(['Naruto','--nocturne','--verdant']), /Choose one/)

  // Character lookups are deterministic in tests and need no external requests.
  const found = await lookupAnimeCharacter('Naruto Uzumaki', {
    fetchImpl:async()=>({
      status:200, ok:true,
      json:async()=>({data:{Page:{characters:[
        {id:33,name:{full:'Naruto generic'},favourites:5000,image:{large:'https://example.com/generic.jpg'}},
        {id:17,name:{full:'Naruto Uzumaki'},favourites:2,image:{large:'https://example.com/naruto.jpg'},siteUrl:'https://anilist.co/character/17'},
      ]}}}),
    }),
  })
  assert.equal(found.id,17,'full name outranks popularity for lookups')
  const baseImage = createCanvas(400,580)
  const painter = baseImage.getContext('2d')
  const grd = painter.createLinearGradient(0,0,400,580)
  grd.addColorStop(0,'#ffbf59');grd.addColorStop(1,'#3c1970')
  painter.fillStyle = grd;painter.fillRect(0,0,400,580)
  painter.fillStyle = '#f0e1cd';painter.beginPath();painter.arc(200,245,115,0,Math.PI*2);painter.fill()
  painter.fillStyle = '#192132';painter.fillRect(80,390,240,190)

  const artwork = await imageAsset(baseImage.toBuffer('image/png'),{sourceUrl:found.sourceUrl})
  assert.equal(artwork.artWidth,400)
  assert.equal(artwork.artHeight,580)
  assert.ok(artwork.art.startsWith('data:image/png;base64,'))
  const p = []
  for (const edition of EDITIONS) {
    const def = await editionDefinition(found,edition)
    assert.equal(def.edition.toLowerCase(),edition)
    assert.equal(def.name,'Naruto Uzumaki')
    const svg = await makeEditionSvg(found,edition,artwork)
    assert.match(svg,/NIGHT Collectibles 1.1/)
    assert.match(svg,/Naruto Uzumaki/)
    assert.match(svg,/<svg[^>]*viewBox="0 0 1000 1400"/)
    assert.ok(svg.includes('data:image/png;base64,'))
    const png = await renderEditionPng(found,edition,artwork,{width:500})
    assert.equal(png.subarray(0,8).toString('hex'),'89504e470d0a1a0a')
    const decoded = await loadImage(png)
    assert.equal(decoded.width,500)
    assert.equal(decoded.height,700)
    assert.ok(png.length > 10000,'rendered PNG must contain real card detail')
    p.push(png.toString('base64').slice(500,1500))
  }
  assert.equal(new Set(p).size,3,'the three preserved card editions must be visually different')

  // Regression from a real WhatsApp screenshot: original SVGs contained
  // base64 <image> tags, yet Skia rendered three beautifully EMPTY frames.
  // SVG-string assertions and overall PNG size cannot catch this. Rendering
  // the exact same card with RED vs BLUE character artwork must affect pixels
  // *inside the character region* for every edition (not just borders/text).
  const redCanvas=createCanvas(360,480), blueCanvas=createCanvas(360,480)
  redCanvas.getContext('2d').fillStyle='#ed2819'
  redCanvas.getContext('2d').fillRect(0,0,360,480)
  blueCanvas.getContext('2d').fillStyle='#118dd8'
  blueCanvas.getContext('2d').fillRect(0,0,360,480)
  const [redArt,blueArt] = await Promise.all([
    imageAsset(redCanvas.toBuffer('image/png'),{sourceUrl:found.sourceUrl}),
    imageAsset(blueCanvas.toBuffer('image/png'),{sourceUrl:found.sourceUrl}),
  ])
  assert.equal(redArt.opaque,true,'catalogue portraits without alpha are opaque')
  const noOpMask=await makeEditionSvg(found,'nocturne',redArt)
  assert.match(noOpMask,/night-nocturne-portrait-crop/,'opaque portraits keep Nocturne halo visible')
  const transparentCanvas=createCanvas(360,480)
  const transparentCtx=transparentCanvas.getContext('2d')
  transparentCtx.fillStyle='#fa4514'
  transparentCtx.beginPath(); transparentCtx.arc(180,210,125,0,Math.PI*2); transparentCtx.fill()
  const transparentArt=await imageAsset(transparentCanvas.toBuffer('image/png'),{sourceUrl:found.sourceUrl})
  assert.equal(transparentArt.opaque,false,'transparent artwork must keep its full pop-out silhouette')
  const floating=await makeEditionSvg(found,'nocturne',transparentArt)
  assert.doesNotMatch(floating,/night-nocturne-portrait-crop/,'transparent cutouts must not be forcibly clipped')
  for (const edition of EDITIONS) {
    const redPng=await renderEditionPng(found,edition,redArt,{width:500})
    const bluePng=await renderEditionPng(found,edition,blueArt,{width:500})
    const a=await loadImage(redPng), b=await loadImage(bluePng)
    const first=createCanvas(500,700), second=createCanvas(500,700)
    first.getContext('2d').drawImage(a,0,0)
    second.getContext('2d').drawImage(b,0,0)
    // Central artwork, far from the name, border and rarity typography.
    const pixelsA=first.getContext('2d').getImageData(170,170,160,190).data
    const pixelsB=second.getContext('2d').getImageData(170,170,160,190).data
    let changed=0, vivid=0
    for(let i=0;i<pixelsA.length;i+=4){
      const difference=Math.abs(pixelsA[i]-pixelsB[i]) +
        Math.abs(pixelsA[i+1]-pixelsB[i+1]) + Math.abs(pixelsA[i+2]-pixelsB[i+2])
      if(difference>65)changed++
      if(difference>170)vivid++
    }
    assert.ok(changed>1200 && vivid>250,
      edition+' image disappeared beneath the SVG ornaments: '+changed+' changed pixels, '+vivid+' vivid pixels')
    if (process.env.CARD_PREVIEW_DIR) {
      await mkdir(process.env.CARD_PREVIEW_DIR,{recursive:true})
      await writeFile(join(process.env.CARD_PREVIEW_DIR,edition+'-verified-artwork.png'),redPng)
    }
  }
  // Also verify the actual full-size production path, not only thumbnails.
  const full=await renderEditionPng(found,'tidelight',redArt)
  assert.equal(full.readUInt32BE(16),3000)
  assert.equal(full.readUInt32BE(20),4200)

  const back = await makeEditionSvg(found,'nocturne',artwork,{side:'back'})
  assert.match(back,/Source artwork/)
  assert.throws(()=>renderCard({},artwork),/Missing/)
  console.log('NIGHT collectible card tests passed: parse, mock lookup, archival templates, three rendered SVG/PNG cards, nonempty embedded artwork, reverse and dimensions.')
}
main().catch(err=>{console.error(err);process.exitCode=1})
