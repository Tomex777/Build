'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {normalizeMeme,normalizeNews,chooseFourMemes,chooseNews,memeScore,isDuplicate,hashNear,safeUrl}=require('./core.cjs');
const {boundedGet,redditAdapter,rssNewsAdapter,licensedCatalogAdapter,parseFeed}=require('./sources.cjs');
const NOW=Date.parse('2026-10-22T16:00:00Z');
const TEST_REDDIT_UA='web:night.channels.tests:v1.0.0 (by /u/fixtureoperator)';
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
 const adapter=redditAdapter({userAgent:TEST_REDDIT_UA,claimQuota:async()=>true,tokenProvider:async()=> 'auth-token-abc123',subreddits:['funny'],
 fetchImpl:async(url,opts)=>{request={url,opts};return new Response(JSON.stringify(mocked));}});
 await assert.rejects(adapter.discover({subreddit:'notApproved',now:NOW}),/authorized/);
 const out=await adapter.discover({subreddit:'funny',now:NOW});
 assert.equal(out.items.length,1);assert.equal(out.items[0].publishable,false);
 assert.equal(out.after,'t3_abc123');assert.match(request.url,/t=all/);
 assert.match(request.opts.headers.authorization,/^Bearer /);
 await assert.rejects(adapter.discover({subreddit:'funny',now:NOW+500}),e=>e.status===429);
});
test('OAuth is mandatory; silent fallback to scraping is prohibited',async()=>{
 const adapter=redditAdapter({userAgent:TEST_REDDIT_UA,claimQuota:async()=>true,subreddits:['funny'],tokenProvider:async()=>''});
 await assert.rejects(adapter.discover({subreddit:'funny',now:NOW}),/Missing authorized/);
});

test('single selected humour category still fills four distinct review cards',()=>{
 const cards=Array.from({length:7},(_,i)=>meme('only'+i));
 assert.equal(chooseFourMemes(cards,{enabled:true,categories:['relatable'],now:NOW}).length,4);
});

test('licensed catalog requires server-side rights verification, not a caller claim',async()=>{
 const base={assetId:'asset1',licenseEvidenceId:'license1',externalId:'artist1',url:'https://studio.example/post/1',
 sourceUrl:'https://studio.example/post/1',mediaUrl:'https://studio.example/meme.png',mime:'image/png',
 createdAt:new Date(NOW).toISOString(),category:'wholesome'};
 const refused=licensedCatalogAdapter({loadRecords:async()=>[base],verifyRights:async()=>false});
 assert.equal((await refused.discover()).items.length,0);
 const approved=licensedCatalogAdapter({loadRecords:async()=>[base],verifyRights:async(id,evidence)=>id==='asset1'&&evidence==='license1'});
 const result=(await approved.discover()).items;
 assert.equal(result.length,1);assert.equal(result[0].publishable,true);
 const noEvidence=licensedCatalogAdapter({loadRecords:async()=>[{...base,licenseEvidenceId:null}],verifyRights:async()=>true});
 assert.equal((await noEvidence.discover()).items.length,0);
});

const {interpretKDramaEntry}=require('./kdrama.cjs');
test('K-drama topic and spoiler classification avoids claiming rumour as confirmation',()=>{
 const cast=interpretKDramaEntry({title:'Actress reportedly in talks to join “Starlight” cast',guid:'one'});
 assert(cast.tags.includes('casting'));assert.equal(cast.show,'Starlight');
 assert.equal(cast.rumor,true);assert.equal(cast.confirmed,false);
 const another=interpretKDramaEntry({title:'“Aurora” finale ending explained: surprise twist',guid:'x'});
 assert.equal(another.spoiler,true);
 const approved=interpretKDramaEntry({title:'Netflix reveals premiere date for Aurora season 2',guid:'two'},
 {showAliases:['Aurora'],confirmedBySource:{two:{verified:true,show:'Aurora',event:'premiere date',storyKey:'aurora:premiere:2026'}}});
 assert.equal(approved.confirmed,true);assert.equal(approved.storyKey,'aurora:premiere:2026');
 assert(approved.tags.includes('platforms'));
});
test('news spoilers are excluded until explicitly opted into',()=>{
 const source={id:'studio',name:'Studio',trustedHost:'studio.example',official:true};
 const approved=normalizeNews({title:'Aurora finale ending explained',url:'https://studio.example/ending',
 publishedAt:new Date(NOW).toISOString(),show:'Aurora',tags:['other'],confirmed:true,spoiler:true},source,{now:NOW});
 assert.equal(chooseNews([approved],{enabled:true}).length,0);
 assert.equal(chooseNews([approved],{enabled:true,includeSpoilers:true}).length,1);
});
test('cannot mark speculation as official despite stale confirmation',()=>{
 const row=interpretKDramaEntry({title:'Rumored casting for Aurora',guid:'old'},
 {confirmedBySource:{old:{verified:true}}});
 assert.equal(row.confirmed,false);assert.equal(row.rumor,true);
});

test('unlicensed meme candidates must display explicit rights block, never appear publish-ready',()=>{
 const unknown=normalizeMeme({externalId:'u',url:'https://reddit.com/r/funny/u',sourceUrl:'https://reddit.com/r/funny/u',
 mediaUrl:'https://i.redd.it/u.jpg',mime:'image/jpeg',createdAt:new Date(NOW).toISOString()},
 {id:'reddit',provider:'reddit',rightsVerified:false});
 const found=chooseFourMemes([unknown],{enabled:true});
 assert.equal(found.length,1);assert.equal(found[0].publishable,false);
 assert.equal(found[0].reviewOnly,true);assert.equal(found[0].publishBlockedReason,'rights_unverified');
});
test('malformed RSS tags and interests never abort whole news selection',()=>{
 const source={id:'s',name:'Studio',trustedHost:'studio.example',official:true};
 const row={title:'Aurora series announcement from Studio',url:'https://studio.example/article',
 publishedAt:new Date(NOW).toISOString(),confirmed:true,tags:'renewals'};
 const item=normalizeNews(row,source,{now:NOW});
 assert.deepEqual(item.tags,[]);
 assert.deepEqual(chooseNews([item],{enabled:true,interests:'renewals',shows:null}),[item]);
});

test('corrupt Reddit post timestamps skip one post without failing entire batch',async()=>{
 const raw={data:{after:null,children:[
 {data:{name:'t3_a',created_utc:99999999999999999,permalink:'/r/funny/a',url_overridden_by_dest:'https://i.redd.it/a.jpg'}},
 {data:{name:'t3_b',created_utc:NOW/1000,permalink:'/r/funny/b',url_overridden_by_dest:'https://i.redd.it/b.jpg'}}]}};
 const source=redditAdapter({userAgent:TEST_REDDIT_UA,claimQuota:async()=>true,subreddits:['funny'],tokenProvider:async()=> 'authorized-access-token',
 fetchImpl:async()=>new Response(JSON.stringify(raw))});
 assert.equal((await source.discover({subreddit:'funny',now:NOW})).items.length,1);
});
test('external app-wide quota hook can deny outbound Reddit API requests',async()=>{
 const source=redditAdapter({userAgent:TEST_REDDIT_UA,subreddits:['funny'],tokenProvider:async()=> 'authorized-access-token',claimQuota:async()=>false});
 await assert.rejects(source.discover({subreddit:'funny',now:NOW}),e=>e.status===429);
});

const {toReviewCard}=require('./contracts.cjs');
test('four-card UI bridge flattens safe provenance but denies external image hotlinks',()=>{
 const item=meme('licensed',{rights:'cleared'});
 const ready=toReviewCard(item,{previewUrl:'/api/channel-media/preview/abc123'});
 assert.equal(ready.publishable,true);
 assert.equal(ready.rightsStatus,'verified','Must match Agent A license gate enum');
 assert.equal(ready.sourceRights,'cleared');
 assert.equal(ready.sourceUrl,item.source.url);
 assert.equal(ready.previewUrl,'/api/channel-media/preview/abc123');
 assert.throws(()=>toReviewCard(item,{previewUrl:'https://i.redd.it/unverified.jpg'}),/no external hotlink/);
 const unknown=normalizeMeme({externalId:'u',url:'https://reddit.com/r/funny/u',
 sourceUrl:'https://reddit.com/r/funny/u',mediaUrl:'https://i.redd.it/u.jpg',
 mime:'image/jpeg',createdAt:new Date(NOW).toISOString()},
 {id:'reddit:r/funny',provider:'reddit',rightsVerified:false});
 const blocked=toReviewCard(unknown,{previewUrl:'/api/channel-media/preview/unverified'});
 assert.equal(blocked.reviewOnly,true);assert.equal(blocked.rightsStatus,'unverified');assert.equal(blocked.publishBlockedReason,'rights_unverified');
});

test('corrupt Reddit children are isolated per candidate rather than aborting all discovery',async()=>{
 const raw={data:{children:[null,{kind:'t3',data:null},{data:{name:'t3_good',created_utc:NOW/1000,permalink:'/r/funny/good',url_overridden_by_dest:'https://i.redd.it/good.jpg'}}]}};
 const adapter=redditAdapter({userAgent:TEST_REDDIT_UA,claimQuota:async()=>true,subreddits:['funny'],tokenProvider:async()=> 'authorized-access-token',fetchImpl:async()=>new Response(JSON.stringify(raw))});
 const result=await adapter.discover({subreddit:'funny',now:NOW});assert.equal(result.items.length,1);
});
test('feed malformed HTML is rejected, not mistaken for zero news',()=>{
 assert.throws(()=>parseFeed('<html><body>Please enable JavaScript</body></html>'),/not an RSS\/Atom/);
});
test('one broken source-specific news row does not lose other verified news',async()=>{
 const xml='<rss><item><title>Invalid metadata row</title><link>https://studio.example/bad</link><pubDate>Wed, 21 Oct 2026 12:00:00 GMT</pubDate></item>'+
 '<item><title>Official Aurora renewal announced</title><link>https://studio.example/good</link><pubDate>Wed, 21 Oct 2026 12:00:00 GMT</pubDate></item></rss>';
 const adapter=rssNewsAdapter({url:'https://studio.example/news',id:'x',name:'Official studio',official:true,now:()=>NOW,
 fetchImpl:async()=>new Response(xml),interpreter:r=>{if(r.title==='Invalid metadata row')throw new Error('Bad row');return {show:'Aurora',event:'renewal',tags:['renewals'],confirmed:true}}});
 const batch=await adapter.discover();assert.equal(batch.items.length,1);assert.equal(batch.interpreterErrors,1);
});
test('all broken news interpretation fails closed without caching empty success',async()=>{
 const adapter=rssNewsAdapter({url:'https://studio.example/news',id:'x',now:()=>NOW,
 fetchImpl:async()=>new Response('<rss><item><title>Broken source item</title><link>https://studio.example/1</link><pubDate>Wed, 21 Oct 2026 12:00:00 GMT</pubDate></item></rss>'),interpreter:()=>{throw new Error('row');}});
 await assert.rejects(adapter.discover(),/metadata interpreter failed/);
});
test('K-drama interpreter accepts malformed show alias and null server confirmations',()=>{
 assert.equal(interpretKDramaEntry({title:'“Aurora” confirmed casting update',guid:'x'},{showAliases:null,confirmedBySource:null}).show,'Aurora');
});
test('confirmed spoiler articles are forced to human review and are not auto eligible',()=>{
 const item=normalizeNews({title:'Aurora finale ending explained',url:'https://studio.example/ending',publishedAt:new Date(NOW).toISOString(),
 show:'Aurora',tags:['other'],confirmed:true,spoiler:true},{id:'studio',name:'Studio',trustedHost:'studio.example',official:true},{now:NOW});
 assert.equal(item.verification,'official');assert.equal(item.requiresReview,true);assert.equal(item.autoEligible,false);
});
test('Retry-After accepts HTTP dates and reports source backoff',async()=>{
 const soon=new Date(Date.now()+125000).toUTCString();
 await assert.rejects(boundedGet('https://studio.example/rss',{allowedHosts:['studio.example'],fetchImpl:async()=>new Response('slow down',{status:429,headers:{'Retry-After':soon}})}),e=>e.status===429&&e.retryAfterSeconds>=119&&e.retryAfterSeconds<=126);
});

test('stale persisted news is rejected at selection time even after ingestion',()=>{
 const official={id:'official',name:'Studio',trustedHost:'studio.example',official:true};
 const record={title:'Aurora confirmed renewal',url:'https://studio.example/news',publishedAt:'2026-10-20T12:00:00Z',show:'Aurora',confirmed:true,tags:['renewals']};
 const item=normalizeNews(record,official,{now:NOW});assert(item);
 assert.equal(chooseNews([item],{enabled:true,now:NOW+20*86400000}).length,0);
 assert.equal(chooseNews([item],{enabled:true,now:NOW,maxAgeDays:14}).length,1);
});
test('verified only platform, region and genre metadata survives normalization',()=>{
 const row={title:'“Aurora” premiere announced by Studio',guid:'approved'};
 const official=interpretKDramaEntry(row,{confirmedBySource:{approved:{verified:true,genre:'Romance',platform:'Viki',availabilityRegion:'Nigeria',show:'Aurora'}}});
 const unverified=interpretKDramaEntry({...row,guid:'unknown'},{});
 assert.equal(official.platform,'Viki');assert.equal(official.availabilityRegion,'Nigeria');assert.equal(official.genre,'Romance');
 assert.equal(unverified.platform,'');assert.equal(unverified.availabilityRegion,'');
 const news=normalizeNews({...official,title:row.title,url:'https://studio.example/story',publishedAt:new Date(NOW).toISOString()},{id:'official',trustedHost:'studio.example',official:true},{now:NOW});
 assert.equal(news.genre,'Romance');assert.equal(news.platform,'Viki');assert.equal(news.availabilityRegion,'Nigeria');
});

test('candidate provenance is immutable and numeric abuse is bounded',()=>{
 const candidate=meme('imm',{width:Infinity,height:999999999,byteLength:Infinity,upvotes:Infinity,license:{fake:true}});
 assert.equal(candidate.media.width,0);assert.equal(candidate.media.height,16000);
 assert.equal(candidate.media.byteLength,0);assert.equal(candidate.metrics.upvotes,0);
 assert.equal(candidate.source.license,null);
 assert.equal(Object.isFrozen(candidate.media),true);assert.equal(Object.isFrozen(candidate.source),true);
 assert.equal(Object.isFrozen(candidate.metrics),true);
 assert.throws(()=>{candidate.source.rights='cleared'},TypeError);
});

const {PUBLIC_FEEDS,createKDramaFeed}=require('./feeds.cjs');
test('public Korean entertainment feeds are inert until explicit operator approval',()=>{
 assert.equal(PUBLIC_FEEDS.soompi.official,false);assert.equal(PUBLIC_FEEDS.koreaTimes.reviewOnly,true);
 assert.throws(()=>createKDramaFeed('soompi'),/Operator approval/);
 assert.throws(()=>createKDramaFeed('untrusted',{operatorApproved:true}),/Unknown/);
});
test('approved source preview links from separate allowlisted site work, others rejected',async()=>{
 const now=()=>NOW;const xml='<rss><item><title>“Aurora” actors join new romance cast</title>'+
 '<link>https://www.koreatimes.co.kr/entertainment/123</link><pubDate>Wed, 21 Oct 2026 12:00:00 GMT</pubDate></item>'+
 '<item><title>“Aurora” renewal announced</title><link>https://evil.example/news</link><pubDate>Wed, 21 Oct 2026 12:00:00 GMT</pubDate></item></rss>';
 const adapter=createKDramaFeed('koreaTimes',{operatorApproved:true,now,showAliases:['Aurora'],fetchImpl:async()=>new Response(xml)});
 const out=await adapter.discover();assert.equal(out.items.length,1);
 assert.equal(out.items[0].verification,'unverified');assert.equal(out.items[0].autoEligible,false);
 assert.match(out.items[0].source.url,/koreatimes.co.kr/);
});
test('K-pop-only feed entry is not pushed as a K-drama',async()=>{
 const now=()=>NOW;const xml='<rss><item><title>Idol returns with new song</title><link>https://www.soompi.com/news/123</link><pubDate>Wed, 21 Oct 2026 12:00:00 GMT</pubDate></item></rss>';
 const adapter=createKDramaFeed('soompi',{operatorApproved:true,now,showAliases:['Aurora'],fetchImpl:async()=>new Response(xml)});
 assert.equal((await adapter.discover()).items.length,0);
});

test('only persisted human approve/decline events influence ranking feedback',()=>{
 const {deriveHumanFeedback}=require('./core.cjs');
 const approvals=Array.from({length:12},()=>({category:'animals',decision:'approved'}));
 const rejects=Array.from({length:12},()=>({category:'relatable',decision:'declined'}));
 const feedback=deriveHumanFeedback([...approvals,...rejects,{category:'dark',decision:'skipped'},{category:'evil',decision:'approved'}]);
 assert(feedback.animals>.7);assert(feedback.relatable<.3);assert.equal(feedback.dark,undefined);
 assert.equal(feedback.evil,undefined);assert.equal(Object.isFrozen(feedback),true);
 const animal=meme('favAnimal',{category:'animals',upvotes:90});const relatable=meme('notFunny',{category:'relatable',upvotes:90});
 assert(memeScore(animal,{categories:['animals','relatable'],feedback,now:NOW})>memeScore(relatable,{categories:['animals','relatable'],feedback,now:NOW}));
});

test('official metadata cannot auto-approve a speculative or spoiler headline',()=>{
 const studio={id:'studio',name:'Studio',trustedHost:'studio.example',official:true};
 const base={publishedAt:new Date(NOW).toISOString(),show:'Aurora',tags:['casting'],confirmed:true};
 const rumor=normalizeNews({...base,title:'Actress reportedly in talks to join Aurora cast',url:'https://studio.example/rumor'},studio,{now:NOW});
 const spoiler=normalizeNews({...base,title:'Aurora finale ending explained and reveals a twist',url:'https://studio.example/ending'},studio,{now:NOW});
 const official=normalizeNews({...base,title:'Aurora official casting announcement today',url:'https://studio.example/casting'},studio,{now:NOW});
 assert.equal(rumor.verification,'rumor');assert.equal(rumor.autoEligible,false);
 assert.equal(spoiler.spoiler,true);assert.equal(spoiler.autoEligible,false);
 assert.equal(official.autoEligible,true);
 assert.deepEqual(chooseNews([rumor,spoiler,official],{enabled:true,mode:'automatic',includeSpoilers:true,now:NOW}),[official]);
 assert.equal(chooseNews([rumor],{enabled:true,mode:'review',now:NOW}).length,1);
 assert.deepEqual(chooseNews([official],{enabled:true,mode:'invalid',now:NOW}),[]);
});
test('only a verified source ledger can attach a cross-outlet story identity',()=>{
 const {interpretKDramaEntry}=require('./kdrama.cjs');
 const row={guid:'news001',title:'“Aurora” official renewal announcement'};
 const unverified=interpretKDramaEntry(row,{confirmedBySource:{news001:{verified:false,storyKey:'aurora:renewal:2026'}}});
 const trusted=interpretKDramaEntry(row,{confirmedBySource:{news001:{verified:true,storyKey:'aurora:renewal:2026'}}});
 assert.equal(unverified.storyKey,null);assert.equal(trusted.storyKey,'aurora:renewal:2026');
});
test('different headlines for one drama story collapse, distinct seasons do not',()=>{
 const sourceA={id:'studio',name:'Studio',trustedHost:'studio.example',official:true};
 const sourceB={id:'press',name:'Press',trustedHost:'press.example',official:false};
 const common={publishedAt:new Date(NOW).toISOString(),show:'Aurora',event:'renewals',tags:['renewals']};
 const a=normalizeNews({...common,title:'Aurora renewal confirmed for season 2 by Studio',url:'https://studio.example/season2',confirmed:true},sourceA,{now:NOW});
 const b=normalizeNews({...common,title:'Studio confirms Aurora renewal for season 2',url:'https://press.example/season2'},sourceB,{now:NOW});
 const c=normalizeNews({...common,title:'Aurora renewal confirmed for season 3 by Studio',url:'https://studio.example/season3',confirmed:true},sourceA,{now:NOW});
 const out=chooseNews([b,a,c],{enabled:true,now:NOW,limit:4});
 assert.equal(out.length,2);assert(out.some(x=>x.id===a.id));assert(out.some(x=>x.id===c.id));
 const publishedNextWeek=normalizeNews({...common,publishedAt:new Date(NOW+8*86400000).toISOString(),title:'Studio confirms Aurora renewal for season 2',url:'https://press.example/season2later'},sourceB,{now:NOW+8*86400000});
 assert.equal(chooseNews([a,publishedNextWeek],{enabled:true,now:NOW+8*86400000,maxAgeDays:14}).length,2);
});
test('review card previews refuse encoded traversal, query strings and third-party media',()=>{
 const {toReviewCard}=require('./contracts.cjs');
 const item=meme('trust',{rights:'cleared'});
 for(const previewUrl of ['/api/channel-media/%2e%2e/secret','/api/channel-media/../../admin','/api/channel-media/preview/%2fmalicious','/api/channel-media/asset?redirect=https://evil.example','//evil.example/api/channel-media/asset']){
  assert.throws(()=>toReviewCard(item,{previewUrl}),/no external hotlink/);
 }
 assert.equal(toReviewCard(item,{previewUrl:'/api/channel-assets/preview/abc123.webp'}).publishable,true);
 assert.equal(safeUrl('https://sub.localhost/internal'),null);
});

test('Reddit discovery refuses misleading off-subreddit paths and external images',async()=>{
 const candidates=[
  {name:'t3_allowed',created_utc:NOW/1000,permalink:'/r/funny/comments/allowed/title',url_overridden_by_dest:'https://i.redd.it/allowed.jpg'},
  {name:'t3_unsafehost',created_utc:NOW/1000,permalink:'/r/funny/comments/unsafe/title',url_overridden_by_dest:'https://other.example/picture.jpg'},
  {name:'t3_othercommunity',created_utc:NOW/1000,permalink:'/r/private/comments/wrong/title',url_overridden_by_dest:'https://i.redd.it/wrong.jpg'},
  {name:'t3_dotpath',created_utc:NOW/1000,permalink:'/r/funny/../../admin',url_overridden_by_dest:'https://i.redd.it/path.jpg'},
  {name:'t3_external',created_utc:NOW/1000,permalink:'https://attacker.example/not-reddit',url_overridden_by_dest:'https://i.redd.it/external.jpg'}
 ];
 const source=redditAdapter({userAgent:TEST_REDDIT_UA,claimQuota:async()=>true,subreddits:['funny'],tokenProvider:async()=> 'authorized-access-token',fetchImpl:async()=>new Response(JSON.stringify({data:{children:candidates.map(data=>({data}))}}))});
 const out=await source.discover({subreddit:'funny',now:NOW});
 assert.equal(out.items.length,1);assert.equal(out.items[0].source.externalId,'t3_allowed');
 assert.equal(out.items[0].publishable,false);
});

test('Reddit adapter requires an honestly configured app identity instead of fake default',()=>{
 assert.throws(()=>redditAdapter({tokenProvider:async()=> 'fixture-access-token',subreddits:['funny']}),/registered Reddit app User-Agent/);
 assert.throws(()=>redditAdapter({tokenProvider:async()=> 'fixture-access-token',userAgent:'NIGHTBot/1.0',subreddits:['funny']}),/registered Reddit app User-Agent/);
 assert.throws(()=>redditAdapter({tokenProvider:async()=> 'fixture-access-token',userAgent:TEST_REDDIT_UA,subreddits:'funny'}),/array of approved subreddits/);
 assert.throws(()=>redditAdapter({tokenProvider:async()=> 'fixture-access-token',userAgent:TEST_REDDIT_UA,subreddits:['funny']}),/shared app-wide Reddit API quota/);
});

test('corrupt persisted batch history and optional scorer inputs fail closed without crashing',()=>{
 const first=meme('history-a',{rights:'cleared'});
 const second=meme('history-b',{rights:'cleared'});
 assert.deepEqual(chooseFourMemes([first,second],{enabled:true,used:[null,undefined,{},first.id],now:NOW}).map(x=>x.id),[second.id]);
 assert.doesNotThrow(()=>memeScore(second,{categories:{invalid:true},feedback:null,now:NOW}));
});

test('region targeting requires verified show availability, not an outlet guess',()=>{
 const official={id:'pressroom',trustedHost:'studio.example',official:true};
 const outlet={id:'newsroom',trustedHost:'paper.example',official:false};
 const base={title:'Aurora official season launch announcement',publishedAt:new Date(NOW).toISOString(),show:'Aurora',event:'releases',tags:['releases'],
  genre:'Romance, Suspense',platform:'Viki',availabilityRegion:'Nigeria, Ghana',confirmed:true};
 const confirmed=normalizeNews({...base,url:'https://studio.example/launch'},official,{now:NOW});
 const unconfirmed=normalizeNews({...base,url:'https://paper.example/launch'},outlet,{now:NOW});
 assert.equal(unconfirmed.verification,'unverified');assert.equal(unconfirmed.platform,'');
 assert.equal(unconfirmed.genre,'');assert.equal(unconfirmed.availabilityRegion,'');
 assert.deepEqual(chooseNews([unconfirmed,confirmed],{enabled:true,mode:'automatic',genres:['romance'],regions:['nigeria'],now:NOW}),[confirmed]);
 assert.deepEqual(chooseNews([confirmed],{enabled:true,genres:['suspense'],platforms:['viki'],regions:['ghana'],now:NOW}),[confirmed]);
 assert.equal(chooseNews([confirmed],{enabled:true,regions:['United States'],now:NOW}).length,0);
 assert.equal(chooseNews([unconfirmed],{enabled:true,regions:['nigeria'],now:NOW}).length,0);
});

test('feedback keeps funny source taste owner-scoped when the caller supplies validated reviews',()=>{
 const {deriveHumanFeedback}=require('./core.cjs');
 const liked=meme('source-liked',{category:'relatable',upvotes:100,upvoteRatio:.9});
 const disliked=meme('source-disliked',{category:'relatable',upvotes:100,upvoteRatio:.9});
 const history=[
  ...Array.from({length:18},()=>({category:'relatable',sourceId:liked.source.id,decision:'approved'})),
  ...Array.from({length:18},()=>({category:'relatable',sourceId:disliked.source.id,decision:'declined'}))
 ];
 const learned=deriveHumanFeedback(history);
 assert(learned.sources[liked.source.id]>.75);
 assert(learned.sources[disliked.source.id]<.25);
 assert(memeScore(liked,{categories:['relatable'],feedback:learned,now:NOW})>
   memeScore(disliked,{categories:['relatable'],feedback:learned,now:NOW}));
 assert(Object.isFrozen(learned.sources));
 assert.throws(()=>{learned.sources[liked.source.id]=0},TypeError);
});
test('source feedback is bounded against malformed identity flooding',()=>{
 const {deriveHumanFeedback}=require('./core.cjs');
 const history=Array.from({length:600},(_,index)=>({
  category:'other',decision:'approved',sourceId:'source:'+index
 }));
 const feedback=deriveHumanFeedback(history);
 assert.equal(Object.keys(feedback.sources).length,256);
 assert.equal(feedback.sources['source:0']>.5,true);
 assert.equal(feedback.sources['source:590'],undefined);
 assert.equal(feedback.sources['__proto__'],undefined);
});

test('Atom drama category metadata can rescue a legitimate review-only show feature',async()=>{
 const atom='<feed><entry><title>“Aurora” stars reflect on their new characters</title>'+
  '<link href="https://www.soompi.com/article/987654/aurora-stars"/>'+
  '<updated>2026-10-21T12:00:00Z</updated><category term="Drama"/></entry></feed>';
 const rows=parseFeed(atom);
 assert.equal(rows.length,1);
 assert.deepEqual(rows[0].categories,['Drama']);
 const adapter=createKDramaFeed('soompi',{operatorApproved:true,now:()=>NOW,showAliases:['Aurora'],fetchImpl:async()=>new Response(atom)});
 const out=await adapter.discover();
 assert.equal(out.items.length,1);
 assert(out.items[0].tags.includes('other'));
 assert.equal(out.items[0].show,'Aurora');
 assert.equal(out.items[0].autoEligible,false);
});

test('four preview cards are atomic and cannot repeat an approved candidate ID',()=>{
 const {toReviewBatch}=require('./contracts.cjs');
 const items=['a','b','c','d'].map(x=>meme('batch-'+x,{rights:'cleared'}));
 const media=Object.fromEntries(items.map(x=>[x.id,{previewUrl:'/api/channel-media/preview/'+x.id}]));
 const batch=toReviewBatch(items,media);
 assert.equal(batch.length,4);assert.equal(Object.isFrozen(batch),true);
 assert(batch.every(x=>x.rightsStatus==='verified'&&x.publishable===true));
 assert.throws(()=>toReviewBatch(items.slice(0,3),media),/Exactly four/);
 assert.throws(()=>toReviewBatch([items[0],items[0],items[2],items[3]],media),/must be distinct/);
 assert.throws(()=>toReviewBatch(items,Object.fromEntries(Object.entries(media).slice(1))),/missing a server-owned preview/);
});

test('cached story arrays are immutable across feed refreshes and consumers',async()=>{
 const xml='<rss><item><title>Aurora official renewal announced</title><link>https://studio.example/story</link>'+
 '<pubDate>Wed, 21 Oct 2026 12:00:00 GMT</pubDate></item></rss>';
 let calls=0;
 const feed=rssNewsAdapter({id:'studio',name:'Studio',url:'https://studio.example/rss',official:true,now:()=>NOW,
  fetchImpl:async()=>{calls++;return new Response(xml)},
  interpreter:()=>({show:'Aurora',event:'renewals',tags:['renewals'],confirmed:true})});
 const one=await feed.discover();
 assert(Object.isFrozen(one));assert(Object.isFrozen(one.items));assert(Object.isFrozen(one.source));assert(Object.isFrozen(one.items[0].tags));
 assert.throws(()=>one.items[0].tags.push('casting'),TypeError);
 assert.throws(()=>one.items.push({kind:'news',autoEligible:true}),TypeError);
 assert.throws(()=>{one.source.official=false},TypeError);
 assert.equal((await feed.discover()).items.length,1);assert.equal(calls,1);
});

test('simultaneous owner polls share exactly one RSS request and an immutable result',async()=>{
 const xml='<rss><item><title>Aurora official renewal announced</title><link>https://studio.example/story</link>'+
 '<pubDate>Wed, 21 Oct 2026 12:00:00 GMT</pubDate></item></rss>';
 let calls=0,release;
 const wait=new Promise(resolve=>{release=resolve});
 const feed=rssNewsAdapter({id:'studio',url:'https://studio.example/feed',official:true,now:()=>NOW,
  fetchImpl:async()=>{calls++;await wait;return new Response(xml)},
  interpreter:()=>({show:'Aurora',event:'renewals',tags:['renewals'],confirmed:true})});
 const one=feed.discover(),two=feed.discover(),three=feed.discover();
 assert.equal(calls,1);
 release();
 const [a,b,c]=await Promise.all([one,two,three]);
 assert.equal(calls,1);assert.equal(a,b);assert.equal(b,c);assert(Object.isFrozen(a.items));
});

test('blank or unbounded GUID cannot inherit a server-confirmed announcement',()=>{
 const {interpretKDramaEntry}=require('./kdrama.cjs');
 const official={empty:{verified:true,storyKey:'aurora:fake:key',show:'Aurora',platform:'Viki'},'':{verified:true,storyKey:'aurora:blank:key',show:'Aurora'}};
 for(const guid of ['',null,undefined,' '.repeat(5),'x'.repeat(257)]){
  const data=interpretKDramaEntry({title:'“Aurora” confirmed renewal announced',guid}, {confirmedBySource:official});
  assert.equal(data.confirmed,false);
  assert.equal(data.storyKey,null);
  assert.equal(data.platform,'');
 }
 const good=interpretKDramaEntry({title:'“Aurora” renewal announced',guid:'valid'},
  {confirmedBySource:{valid:{verified:true,show:'Aurora',storyKey:'aurora:renew:season2'}}});
 assert.equal(good.confirmed,true);
});

test('GUID reused for another series cannot silently verify a different show',()=>{
 const {interpretKDramaEntry}=require('./kdrama.cjs');
 const ledger={reused:{verified:true,show:'Aurora',storyKey:'aurora:season2',platform:'Viki',availabilityRegion:'Nigeria',event:'renewal'}};
 const falseMatch=interpretKDramaEntry({title:'“Moonlight” actors join new series',guid:'reused'},
  {confirmedBySource:ledger,showAliases:['Moonlight']});
 assert.equal(falseMatch.show,'Moonlight');
 assert.equal(falseMatch.confirmed,false);
 assert.equal(falseMatch.storyKey,null);
 assert.equal(falseMatch.platform,'');
 assert.equal(falseMatch.availabilityRegion,'');
 assert.equal(falseMatch.event,'casting');
 const real=interpretKDramaEntry({title:'“Aurora” renewed for new season',guid:'reused'}, {confirmedBySource:ledger});
 assert.equal(real.confirmed,true);
 assert.equal(real.storyKey,'aurora:season2');
 assert.equal(real.platform,'Viki');
});
