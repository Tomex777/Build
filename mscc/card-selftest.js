import assert from 'node:assert/strict'
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
  const back = await makeEditionSvg(found,'nocturne',artwork,{side:'back'})
  assert.match(back,/Source artwork/)
  assert.throws(()=>renderCard({},artwork),/Missing/)
  console.log('NIGHT collectible card tests passed: parse, mock lookup, archival templates, three rendered SVG/PNG cards, reverse and dimensions.')
}
main().catch(err=>{console.error(err);process.exitCode=1})
