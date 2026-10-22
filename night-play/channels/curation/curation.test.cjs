'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {normalizeMeme,normalizeNews,chooseFourMemes,chooseNews,memeScore,isDuplicate,hashNear,safeUrl}=require('./core.cjs');
const {boundedGet,redditAdapter,rssNewsAdapter,parseFeed}=require('./sources.cjs');
const NOW=Date.parse('2026-10-22T16:00:00Z');
function meme(id,overrides={}){
 return normalizeMeme({externalId:'t3_'+id,url:'https://www.reddit.com/r/funny/comments/'+id+'/post/?utm_source=x',
 sourceUrl:'https://www.reddit.com/r/funny/comments/'+id+'/post/',mediaUrl:'https://i.redd.it/'+id+'.jpg',
 mime:'image/jpeg',width:900,height:850,createdAt:'2023-10-22T16:00:00Z',category:'relatable',
 upvotes:300,upvoteRatio:.93,caption:'Good joke',...overrides},{id:'fixture-'+id,provider:'fixture',rightsVerified:true});
}
test('HTTPS canonical URL strips trackers; localhost, IP, non-HTTPS and userinfo rejected',()=>{
 assert.equal(safeUrl('https://example.com/p?utm_source=x&b=2&a=1#x'),'https://example.com/p?a=1&b=2');
 for(const url of ['javascript:alert(1)','http://example.com','https://localhost/x','https://127.0.0.1/x','https://u:p@example.com/','https://[::1]/'])assert.equal(safeUrl(url),null);
});
test('Reddit/social discovery does NOT license redistribution',()=>{
 const record={externalId:'x',url:'https://reddit.com/a',sourceUrl:'https://reddit.com/a',mediaUrl:'https://i.redd.it/x.png',
 mime:'image/png',createdAt:new Date(NOW).toISOString(),rights:'cleared'};
 const item=normalizeMeme(record,{id:'reddit',provider:'reddit',rightsVerified:false});
 assert.equal(item.source.rights,'unknown');assert.equal(item.publishable,false);
 assert.equal(meme('approved',{rights:'cleared'}).publishable,true);
});
test('bad dates, GIFs, NSFW, video and galleries excluded',()=>{
 for(const edit of [{mime:'image/gif'},{over18:true},{isGallery:true},{isVideo:true},{createdAt:'yesterday'}])assert.equal(meme('bad',edit),null);
});
test('old but genuinely funny memes outrank new weak images',()=>{
 const old=meme('old',{createdAt:'2021-10-22T16:00:00Z',upvotes:1600,legibility:1,originality:1});
 const weak=meme('new',{createdAt:'2026-10-21T16:00:00Z',upvotes:2,legibility:.05,originality:.05});
 assert(memeScore(old,{categories:['relatable'],now:NOW,feedback:{relatable:1}})>memeScore(weak,{categories:['relatable'],now:NOW,feedback:{relatable:0}}));
 assert.equal(chooseFourMemes([weak,old],{enabled:true,now:NOW})[0].id,old.id);
});
test('module is OFF by default; dark opt-in and four-item diversity hold',()=>{
 const items=[...Array.from({length:6},(_,i)=>meme('r'+i)),
 ...Array.from({length:3},(_,i)=>meme('a'+i,{category:'animals'})),meme('dark',{category:'dark'})];
 assert.deepEqual(chooseFourMemes(items),[]);
 const four=chooseFourMemes(items,{enabled:true,now:NOW});
 assert.equal(four.length,4);assert(four.filter(x=>x.category==='relatable').length<=2);
 assert(four.every(x=>x.category!=='dark'));
 assert.equal(chooseFourMemes(items,{enabled:true,categories:['dark']}).length,0);
 assert.equal(chooseFourMemes(items,{enabled:true,categories:['dark'],allowDark:true}).length,1);
});
test('perceptual near-duplicate and previously used checks',()=>{
 const a=meme('a',{perceptualHash:'0000000000000000'}),b=meme('b',{perceptualHash:'0000000000000003'}),
 c=meme('c',{perceptualHash:'ffffffffffffffff'});
 assert.equal(isDuplicate(a,b),true);assert.equal(hashNear(a.media.perceptualHash,c.media.perceptualHash),false);
 assert.equal(chooseFourMemes([a,b,c],{enabled:true}).length,2);
 assert.equal(chooseFourMemes([a,b,c],{enabled:true,used:[a]}).length,1);
});
test('news validates fresh trusted exact-domain official announcements',()=>{
 const source={id:'studio',name:'Studio',trustedHost:'studio.example',official:true};
 const good={title:'Confirmed premiere date for Aurora drama',url:'https://studio.example/story/aurora',
 publishedAt:'2026-10-21T12:00:00Z',show:'Aurora',event:'premiere date',tags:['releases'],confirmed:true};
 const item=normalizeNews(good,source,{now:NOW});assert.equal(item.autoEligible,true);
 assert.equal(item.verification,'official');assert(!item.summary.includes(item.title));
 assert.equal(normalizeNews({...good,url:'https://other.example/story'},source,{now:NOW}),null);
 assert.equal(normalizeNews({...good,publishedAt:'2025-01-01T00:00:00Z'},source,{now:NOW}),null);
 assert.equal(normalizeNews({...good,rumor:true},source,{now:NOW}).requiresReview,true);
 assert.equal(normalizeNews({...good,confirmed:false},source,{now:NOW}).autoEligible,false);
});
test('K-drama interest filtering and cross-source dedupe prefer verified official',()=>{
 const official={id:'x',name:'Studio',trustedHost:'studio.example',official:true};
 const fan={id:'y',name:'Fan',trustedHost:'fans.example',official:false};
 const data={title:'Aurora renewed for another season',publishedAt:'2026-10-21T16:00:00Z',
 show:'Aurora',event:'renewal',storyKey:'aurora:renewal:2026',tags:['renewals']};
 const a=normalizeNews({...data,url:'https://fans.example/a'},fan,{now:NOW});
 const b=normalizeNews({...data,url:'https://studio.example/a',confirmed:true},official,{now:NOW});
 assert.equal(chooseNews([a,b],{enabled:true,interests:['renewals']})[0].verification,'official');
 assert.equal(chooseNews([a,b],{enabled:true,interests:['renewals']}).length,1);
 assert.deepEqual(chooseNews([a,b],{}),[]);
 assert.equal(chooseNews([b],{enabled:true,interests:['casting']}).length,0);
 assert.equal(chooseNews([b],{enabled:true,shows:['Aurora']}).length,1);
});
test('RSS and Atom safe parsing including CDATA, block DTD/entities',()=>{
 const feed='<rss><item><title><![CDATA[Drama &amp; TV]]></title><link>https://studio.example/1</link><pubDate>Wed, 21 Oct 2026 12:00:00 GMT</pubDate></item></rss>';
 assert.equal(parseFeed(feed)[0].title,'Drama & TV');
 assert.equal(parseFeed('<feed><entry><title>New</title><link href="https://studio.example/2"/><updated>2026-10-21T12:00:00Z</updated></entry></feed>')[0].url,'https://studio.example/2');
 assert.throws(()=>parseFeed('<!DOCTYPE x [<!ENTITY x SYSTEM "file:///etc/passwd">]>'),/unsafe/);
});
test('RSS adapter fetches approved host only, uses template summary, TTL cache',async()=>{
 let calls=0;
 const adapter=rssNewsAdapter({id:'s',name:'Studio',url:'https://studio.example/rss',official:true,now:()=>NOW,
 fetchImpl:async()=>{calls++;return new Response('<rss><item><title>Aurora renewal announced</title><link>https://studio.example/a</link><pubDate>Wed, 21 Oct 2026 12:00:00 GMT</pubDate></item></rss>')},
 interpreter:()=>({show:'Aurora',event:'renewal',tags:['renewals'],confirmed:true})});
 assert.equal((await adapter.discover()).items[0].autoEligible,true);
 assert.equal((await adapter.discover()).items.length,1);assert.equal(calls,1);
});
test('invalid redirects and oversized responses fail closed',async()=>{
 await assert.rejects(boundedGet('https://studio.example/rss',{allowedHosts:['studio.example'],fetchImpl:async()=>new Response('',{status:302})}),e=>e.status===302);
 await assert.rejects(boundedGet('https://studio.example/rss',{allowedHosts:['studio.example'],maxBytes:3,fetchImpl:async()=>new Response('abcdef')}),/size limit/);
 await assert.rejects(boundedGet('https://evil.example/rss',{allowedHosts:['studio.example']}),/allowlisted/);
});
test('Reddit OAuth allowlist, historical top, unknown rights, throttle',async()=>{
 let request;const mocked={data:{after:'t3_abc123',children:[{data:{name:'t3_abc123',permalink:'/r/funny/comments/abc123/x/',
 url_overridden_by_dest:'https://i.redd.it/abc123.jpg',created_utc:NOW/1000,author:'poster',title:'Funny one',
 ups:100,upvote_ratio:.9,preview:{images:[{source:{width:700,height:900}}]}}}]}};
 const adapter=redditAdapter({tokenProvider:async()=> 'auth-token-abc123',subreddits:['funny'],
 fetchImpl:async(url,opts)=>{request={url,opts};return new Response(JSON.stringify(mocked));}});
 await assert.rejects(adapter.discover({subreddit:'notApproved',now:NOW}),/authorized/);
 const out=await adapter.discover({subreddit:'funny',now:NOW});
 assert.equal(out.items.length,1);assert.equal(out.items[0].publishable,false);
 assert.equal(out.after,'t3_abc123');assert.match(request.url,/t=all/);
 assert.match(request.opts.headers.authorization,/^Bearer /);
 await assert.rejects(adapter.discover({subreddit:'funny',now:NOW+500}),e=>e.status===429);
});
test('OAuth is mandatory; silent fallback to scraping is prohibited',async()=>{
 const adapter=redditAdapter({subreddits:['funny'],tokenProvider:async()=>''});
 await assert.rejects(adapter.discover({subreddit:'funny',now:NOW}),/Missing authorized/);
});

test('single selected humour category still fills four distinct review cards',()=>{
 const cards=Array.from({length:7},(_,i)=>meme('only'+i));
 assert.equal(chooseFourMemes(cards,{enabled:true,categories:['relatable'],now:NOW}).length,4);
});
