'use strict';
// Vetted public feed discovery references. Listing a feed is NOT a redistribution grant.
const {rssNewsAdapter}=require('./sources.cjs');
const {interpretKDramaEntry}=require('./kdrama.cjs');
const PUBLIC_FEEDS=Object.freeze({
 soompi:Object.freeze({id:'soompi',name:'Soompi',url:'https://www.soompi.com/feed',
   articleHosts:Object.freeze(['www.soompi.com']),official:false,
   reviewOnly:true,requiresOperatorApproval:true,
   attribution:'Link to the original Soompi article; never mirror article/image content.'}),
 koreaTimes:Object.freeze({id:'korea-times-entertainment',name:'The Korea Times — Entertainment',
   url:'https://feed.koreatimes.co.kr/k/entertainment.xml',
   articleHosts:Object.freeze(['www.koreatimes.co.kr','koreatimes.co.kr','feed.koreatimes.co.kr']),
   official:false,reviewOnly:true,requiresOperatorApproval:true,
   attribution:'Link to the original Korea Times article; never mirror article/image content.'})
});
function createKDramaFeed(profileId,{operatorApproved=false,fetchImpl,showAliases=[],now=()=>Date.now()}={}){
 const profile=PUBLIC_FEEDS[profileId];
 if(!profile)throw new TypeError('Unknown pre-vetted feed profile.');
 if(operatorApproved!==true)throw new Error('Operator approval and site/feed terms review are required.');
 if(!Array.isArray(showAliases))throw new TypeError('Expected array of followed show names.');
 return rssNewsAdapter({id:profile.id,name:profile.name,url:profile.url,
   articleHosts:[...profile.articleHosts],official:false,fetchImpl,now,
   interpreter:row=>{
    const parsed=interpretKDramaEntry(row,{showAliases,confirmedBySource:{}});
    // Headlines that cannot be associated with an actual drama are not pushed
    // to channel owners as "K-drama" just because the outlet covers K-pop.
    if(!parsed?.show||!parsed.tags.length)return null;
    return parsed;
   }
 });
}
module.exports={PUBLIC_FEEDS,createKDramaFeed};
