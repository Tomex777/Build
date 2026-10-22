'use strict';
// Agent E independent conformance checks for B's *actual* curation source.
// Run with NIGHT_CURATION_ROOT pointing at B's isolated source checkout.
// No live third-party API request or unlicensed channel publishing occurs.
const assert=require('node:assert/strict'),path=require('node:path');
const root=path.resolve(process.env.NIGHT_CURATION_ROOT||path.join(__dirname,'../curation'));
const {normalizeMeme,normalizeNews,chooseFourMemes,chooseNews,isDuplicate,safeUrl}=require(path.join(root,'core.cjs'));
const {boundedGet,SourceError}=require(path.join(root,'sources.cjs'));
const now=Date.parse('2026-10-22T12:00:00.000Z');
const base={id:'permitted-fixture',provider:'fixture',rightsVerified:true};
const make=(id,{created='2023-10-01T10:00:00.000Z',category='relatable',rights='cleared',hash=null,votes=500}={})=>
 normalizeMeme({url:'https://example.org/items/'+id,externalId:id,mediaUrl:'https://images.example.org/'+id+'.png',
 sourceUrl:'https://example.org/items/'+id,category,createdAt:created,mime:'image/png',width:1200,height:850,
 byteLength:120000,perceptualHash:hash,legibility:.97,originality:.98,upvotes:votes,upvoteRatio:.97,
 rights},base);
const old=make('old_but_funny',{votes:10000});
const newer=make('newer',{created:'2026-10-21T12:00:00.000Z',votes:40});
const varied=[old,newer,make('wholesome',{category:'wholesome'}),
 make('anime',{category:'anime'}),make('animals',{category:'animals'})];
assert.ok(old.publishable,'Permitted fixture is publishable');
assert.equal(safeUrl('http://example.org/a'),null,'Require HTTPS');
assert.equal(safeUrl('https://127.0.0.1/admin'),null,'Block loopback literal');
assert.equal(safeUrl('https://test.local/api'),null,'Block local suffix');
const selected=chooseFourMemes(varied,{enabled:true,now,allowDark:false,
 categories:['relatable','wholesome','anime','animals']});
assert.equal(selected.length,4,'Four diverse publishable fixtures are offered');
assert.ok(selected.some(c=>c.id===old.id),'Genuinely strong three-year-old meme remains eligible');
assert.equal(chooseFourMemes(varied,{enabled:false,now}).length,0,'OFF means no discovery');
assert.equal(chooseFourMemes([make('dark',{category:'dark'})],
 {enabled:true,categories:['dark'],allowDark:false,now}).length,0,'Dark memes need explicit opt-in');
const duplicate=make('dupe',{hash:'aaaaaaaaaaaaaaaa'});
const near=make('near',{hash:'aaaaaaaaaaaaaaab'});
assert.ok(isDuplicate(duplicate,near),'Perceptual near-duplicate detection works');
assert.equal(chooseFourMemes([duplicate,near],{enabled:true,now}).length,1);

const unknown=make('unknown_rights',{rights:'unknown'});
assert.equal(unknown.publishable,false,'Unknown rights cannot authorize publishing');
const rightsReview=chooseFourMemes([unknown],{enabled:true,categories:['relatable'],now});
assert.ok(rightsReview.every(item=>item.publishable===false &&
 item.reviewOnly===true && item.publishBlockedReason==='rights_unverified'),
 'RIGHTS GATE: any displayed unlicensed discovery item must be explicitly review-only and unpublishable');

const newsSource={id:'official-demo',name:'Fixture Platform',official:true,
 trustedHost:'example.org',provider:'fixture'};
const news=(id,props={})=>normalizeNews({
 url:'https://example.org/news/'+id,title:'A verified series release announcement '+id,
 publishedAt:'2026-10-22T10:00:00Z',show:'Series A',event:'release',tags:['releases'],
 confirmed:true,...props
},newsSource,{now});
const announced=news('official');
assert.equal(announced.autoEligible,true);
const rumour=news('rumour',{rumor:true,confirmed:false});
assert.equal(rumour.autoEligible,false,'Rumours may never auto-post as verified fact');
assert.equal(chooseNews([announced,rumour],{enabled:false,interests:['releases']}).length,0);
assert.equal(news('foreign',{url:'https://different.org/news/fake'}),null,
 'News URLs must remain on their trusted provenance host');
assert.equal(news('old',{publishedAt:'2026-08-01T10:00:00Z'}),null,
 'Stale news must not become new breaking updates');
assert.doesNotThrow(()=>normalizeNews({
 url:'https://example.org/news/malformed',title:'Malformed RSS entry with tags',publishedAt:'2026-10-22T10:00:00Z',
 tags:'releases',confirmed:false
},newsSource,{now}), 'One unexpected tag type must not crash an entire feed');

(async()=>{
 let redirected=false;
 try {await boundedGet('https://evil.invalid/source',{
   allowedHosts:['example.org'],fetchImpl:()=>{redirected=true;throw Error('Unsafe request made')}
 });assert.fail('Non-allowlisted host did not fail');}
 catch(e){assert.ok(e instanceof SourceError);assert.equal(redirected,false);}
 console.log('PASS B curation: rights-safe four, older humour, interest/dark gate, provenance, news freshness, bounded hosts');
})().catch(e=>{console.error(e);process.exitCode=1;});
