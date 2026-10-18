/* NIGHT Memory Match artwork: original SVG sets and curated Unsplash photographic sets.
 * Photo CDN is optional; locally drawn distinctive fallback art keeps every pair
 * recognizable even without a connection. Do not substitute branded character IP. */
(function(root){
"use strict";
const photos={
 wildlife:[
 ["Lion","1546182990-dffeafbe841d"],["Panda","1564349683136-77e08dba1ef7"],["Dog","1517849845537-4d257902454a"],["Kitten","1511044568932-338cba0ad803"],["Fox","1474511320723-9a56873867b5"],["Cat","1533738363-b7f9aef128ce"],["Elephant","1549366021-9f761d450615"],["Puppy","1560807707-8cc77767d783"],["Retriever","1558788353-f76d92427f16"],["Parrot","1551415923-a2297c7fda79"],
 ["Safari","1547721064-da6cfb341d50"],["Deer","1484406566174-9da000fda645"],["Raven","1524024973431-2ad916746881"],["Cattle","1570042225831-d98fa7577f1e"],["Highland","1472396961693-142e6e269027"],["Herd","1561948955-570b270e7c36"],["Lemur","1693740571898-47128f049166"],["Lioness","1769470683197-119f690c01ea"],["Dolphin","1507146426996-ef05306b995a"],["Whiskers","1518791841217-8f162f1e1131"],["Tabby","1573865526739-10659fec78a5"],["Macaw","1552728089-57bdde30beb3"],["Husky","1535930891776-0c2dfb7fda1a"],["Savannah","1516026672322-bc52d61a55d5"],["Otter","1542038784456-1ea8e935640e"],["Fawn","1437622368342-7a3d73a34c8f"],["Kingfisher","1500534623283-312aade485b7"],["Rabbit","1534177616072-ef7dc120449d"],["Flamingo","1551986782-d0169b3f8fa7"],["Wildlife","1511497584788-876760111969"]
 ],
 cars:[
 ["Porsche","1503376780353-7e6692767b70"],["Red sportscar","1552519507-da3b142c6e3d"],["Vintage coupe","1493238792000-8113da705763"],["Convertible","1507136566006-cfc505b114fc"],["Classic roadster","1489824904134-891ab64532f1"],["Sport sedan","1525609004556-c46c7d6cf023"],["Street car","1533473359331-0135ef1b58bf"],["Electric coupe","1502161254066-6c74afbf7dd0"],["Performance car","1492144534655-ae79c964c9d7"],["Retro Mustang","1544896478-d5b709d413c5"],
 ["Blue classic","1520608760-eff2c44afa2b"],["Vintage touring","1741779456831-e2cd573a874d"],["Roadster","1709050939134-4bdf2875debf"],["Track machine","1549317661-bd32c8ce0db2"],["Grand tourer","1494905998402-395d579af36f"],["City classic","1486496572940-2bb2341fdbdf"],["Highway","1519641471654-76ce0107ad1b"],["American classic","1494976388531-d1058494cdd8"],["Vehicle","1565043666747-69f6646db940"],["Rally car","1523983388277-336a66bf9bcd"],["Coupe","1544829099-b9a0c07fad1a"],["Night drive","1597404294360-feeeda04612e"],["Classic","1571607388263-1044f9ea01dd"],["Supercar","1532581140115-3e355d1ed1de"],["Sport","1517524008697-84bbe3c3fd98"],["Red Ferrari","1583121274602-3e2820c69888"],["Turbo","1553440569-bcc63803a83d"],["Yellow coupe","1550355291-bbee04a92027"],["Car culture","1621007947382-bb3c3994e3fb"],["Speed","1520052205864-92d242b3a76b"]
 ],
 food:[
 ["Fresh produce","1498837167922-ddd27525d352"],["Brunch","1476224203421-9ac39bcb3327"],["Garden salad","1540189549336-e6e99c3679fe"],["Rice bowl","1546069901-ba9599a7e63c"],["Cake","1565958011703-44f9829ba187"],["Sweet treats","1551024506-0bccd828d307"],["Dinner","1504674900247-0877df9cc836"],["Pizza","1565299624946-b28f40a0ae38"],["Healthy dish","1547592180-85f173990554"],["Vegetables","1512621776951-a57141f2eefd"],
 ["Sandwich","1511690743698-d9d85f2fbf38"],["Street food","1555939594-58d7cb561ad1"],["Pancakes","1567620905732-2d1ec7ab7445"],["Breakfast","1482049016688-2d3e1b311543"],["Noodles","1559847844-5315695dadae"],["Burger","1565299507177-b0ac66763828"],["Rice plate","1589302168068-964664d93dc0"],["Grill","1529042410759-befb1204b468"],["Bread","1509440159596-0249088772ff"],["Ingredients","1543353071-873f17a7a088"],
 ["Seafood","1519708227418-c8fd9a32b7a2"],["Hot pizza","1574071318508-1cdbab80d002"],["Cheeseburger","1568901346375-23c9450c58cd"],["Milk","1550583724-b2692b85b150"],["Pasta","1608897013039-887f21d8c804"],["Honey","1587049352851-8d4e89133924"],["Restaurant","1547573854-74d2a71d0826"],["Cherries","1534939561126-855b8675edd7"],["Frosting","1578985545062-69928b1d9587"],["Dessert","1588195538326-c5b1e5d4b6b8"]
 ]
};
const themes=[
 {id:"wildlife",name:"Wildlife",caption:"Nature, close up",type:"photo",color:"#a4bfa3"},
 {id:"cars",name:"Cars",caption:"Every kind of ride",type:"photo",color:"#a6b5c4"},
 {id:"storybook",name:"Storybook",caption:"Original fantasy characters",type:"art",color:"#bda9cc"},
 {id:"food",name:"Food",caption:"Good things to eat",type:"photo",color:"#dabba0"},
 {id:"shapes",name:"Shapes",caption:"Pure colour & form",type:"art",color:"#9faac8"}
];
const shades=["#2f6471","#b47367","#ad8241","#6b729e","#60947c","#bd7794","#8c6eaf","#b88d47","#466b86","#ad5e59","#668466","#7e6999","#bd7862","#477b78","#b8814c"];
const geometry=[
 '<circle cx="40" cy="40" r="25"/>',
 '<path d="M40 8 72 40 40 72 8 40Z"/>',
 '<path d="M40 8 74 69H6Z"/>',
 '<rect x="15" y="15" width="50" height="50" rx="9"/>',
 '<path d="M20 12H60L76 40 60 68H20L4 40Z"/>',
 '<path d="M40 7 49 29H74L54 45 62 71 40 55 18 71 26 45 6 29H31Z"/>',
 '<path d="M15 24q25-35 50 0t-50 33q-24-17 0-33Z"/>',
 '<path d="M27 10h26v20h19v20H53v20H27V50H8V30h19Z"/>',
 '<path d="M11 20Q40-4 69 20V61Q40 85 11 61Z"/>',
 '<path d="M14 10Q76 10 66 49Q59 72 24 67Q1 59 14 10Z"/>'
];
function escapeText(v){return String(v).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));}
function geometryArt(index){
 const base=index%geometry.length,stage=Math.floor(index/geometry.length);
 const a=shades[(index*3+1)%shades.length],bg=["#e6f0ea","#edf0f7","#f4e9e3"][stage],angle=stage===1?25:stage===2?-22:0;
 return '<svg class="memory-face-svg" viewBox="0 0 80 100" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">'+
 '<rect width="80" height="100" fill="'+bg+'"/><circle cx="63" cy="18" r="27" fill="'+a+'" opacity=".07"/>'+
 '<g transform="translate(0 10) rotate('+angle+' 40 40)" fill="'+a+'" stroke="#ffffffaa" stroke-width="2.4">'+geometry[base]+'</g>'+
 (stage===1?'<circle cx="40" cy="50" r="8" fill="#f7f4ed" opacity=".95"/>':stage===2?'<g fill="none" stroke="#fff" stroke-width="3"><circle cx="40" cy="50" r="13"/></g>':'')+
 '</svg>';
}
// Individually varied original fantasy portraits. All designs belong to NIGHT Play;
// these are not Disney artwork, likenesses or branded characters.
const figureNames=["Amara","Leora","Selene","Riven","Maris","Yara","Thorne","Nyra","Cassia","Rowan","Astra","Elden","Zaria","Liora","Taryn","Soren","Mira","Kairo","Iris","Elian","Veda","Arden","Sable","Cleo","Rhea","Orion","Lyra","Sol","Isolde","Fen"];
function storybookArt(i){
 const skin=["#d59c78","#a36e50","#edc6a1","#815745","#c78a69","#e1b391"][i%6],
 hair=["#211e31","#5b342f","#a66d42","#e7c77e","#242f3e","#6a4d66","#342119"][i%7],
 cloak=shades[(i*7+3)%shades.length],back=["#cdd5df","#e5d9d2","#bdced0","#d7c9df","#d3dfd5"][i%5],
 accent=["#f7db90","#f5eddf","#f4d2bb"][i%3],type=i%6;
 const crown=type===0||type===4?'<path d="m22 37 5-15 12 9 12-15 11 15 11-9 5 15-6 5H28Z" fill="'+accent+'" stroke="#8d6842" stroke-width="1.4"/><circle cx="51" cy="34" r="3" fill="'+cloak+'"/>':type===1?'<path d="M22 42Q50 8 77 42" fill="none" stroke="'+accent+'" stroke-width="5"/><path d="M41 24 50 11 59 24" fill="'+accent+'"/>':type===2?'<path d="M21 37Q50 4 80 37l-8 9H29Z" fill="'+cloak+'" stroke="'+accent+'" stroke-width="3"/>':type===3?'<path d="M45 19H55V35H45Z" fill="'+accent+'"/><path d="M34 27H66V35H34Z" fill="'+accent+'"/>':'<path d="M25 39 50 17 75 39" stroke="'+accent+'" stroke-width="4" fill="none"/>';
 return '<svg class="memory-face-svg" viewBox="0 0 100 122" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">'+
 '<defs><linearGradient id="portrait'+i+'" x2="1" y2="1"><stop stop-color="'+back+'"/><stop offset="1" stop-color="#f9f2dc"/></linearGradient></defs>'+
 '<rect width="100" height="122" fill="url(#portrait'+i+')"/><path d="M-8 120Q7 80 50 79Q93 80 108 120Z" fill="'+cloak+'"/>'+
 '<path d="M27 85q-10-20-6-44 5-30 28-30 27 0 30 31l-6 44Z" fill="'+hair+'"/>'+
 '<ellipse cx="50" cy="57" rx="25" ry="34" fill="'+skin+'"/><path d="M26 46Q27 13 50 16Q74 17 75 46Q59 36 56 25Q45 43 26 46" fill="'+hair+'"/>'+
 '<path d="M36 56h7m14 0h7" stroke="#302723" stroke-width="2.5" stroke-linecap="round"/>'+
 '<path d="M47 69q3 4 7 0" fill="none" stroke="#774c43" stroke-width="1.8" stroke-linecap="round"/>'+
 crown+
 '<path d="M34 91 49 107 65 91" fill="none" stroke="'+accent+'" stroke-width="4"/>'+
 (i%4===0?'<circle cx="50" cy="108" r="5" fill="'+accent+'"/>':i%4===1?'<path d="M49 102h4v14h-4Z" fill="'+accent+'"/>':'')+
 '</svg>';
}
function photoUrl(theme,id){
 const entry=photos[theme]?.[id];
 return entry?"https://images.unsplash.com/photo-"+entry[1]+"?auto=format&fit=crop&crop=entropy&w=260&h=330&q=76":null;
}
function fallbackArt(theme,id){
 const tone=shades[(id*7+(theme==="cars"?4:theme==="food"?2:0))%shades.length],label=photos[theme]?.[id]?.[0]||"NIGHT";
 const silhouette=theme==="cars"?
 '<path d="M17 61 26 42Q29 36 39 36H61Q71 36 77 49L86 61v17H14V65Q14 61 17 61Z" fill="'+tone+'"/><path d="M31 44h35l9 16H23Z" fill="#c9e7e8"/><circle cx="29" cy="78" r="9" fill="#273d41"/><circle cx="71" cy="78" r="9" fill="#273d41"/><circle cx="29" cy="78" r="4" fill="#eaeaea"/><circle cx="71" cy="78" r="4" fill="#eaeaea"/>':
 theme==="food"?
 '<circle cx="50" cy="55" r="37" fill="#faf7e8" stroke="'+tone+'" stroke-width="7"/><circle cx="50" cy="55" r="25" fill="'+tone+'" opacity=".27"/><path d="M37 46 52 39 65 51 57 65 38 61Z" fill="'+tone+'"/><circle cx="42" cy="51" r="3" fill="#fbefb1"/>':
 '<path d="M28 32q-19-22-18 4l9 16Q7 83 50 88Q92 82 81 52l9-16q1-26-18-4Q50 20 28 32Z" fill="'+tone+'"/><ellipse cx="50" cy="62" rx="24" ry="20" fill="#efded0"/><circle cx="40" cy="55" r="3.5" fill="#273631"/><circle cx="60" cy="55" r="3.5" fill="#273631"/><path d="m45 67 5 4 5-4" stroke="#55423b" stroke-width="2.5" fill="none"/>';
 return '<svg class="memory-face-svg" viewBox="0 0 100 122" aria-hidden="true"><rect width="100" height="122" fill="#e9efea"/>'+silhouette+'<rect x="4" y="97" width="92" height="19" rx="7" fill="#f7faf7e8"/><text x="50" y="110" fill="#314638" font-family="Arial,sans-serif" font-size="10.5" text-anchor="middle" font-weight="700">'+escapeText(label)+'</text></svg>';
}
function face(theme,id,loaded){
 if(theme==="shapes")return geometryArt(id);
 if(theme==="storybook")return storybookArt(id);
 const url=photoUrl(theme,id);
 const fallback=fallbackArt(theme,id);
 return '<span class="memory-photo-layer">'+fallback+(loaded&&url?'<img src="'+url+'" alt="" draggable="false" decoding="async" loading="eager">':'')+'</span>';
}
const defs={themes,photos,figureNames,photoUrl,face,escapeText};
root.NIGHTMemoryArt=defs;
})(typeof window!=="undefined"?window:globalThis);
