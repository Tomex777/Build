/* NIGHT Memory Match artwork: original SVG sets and curated Unsplash photographic sets.
 * Photo CDN is optional; locally drawn distinctive fallback art keeps every pair
 * recognizable even without a connection. Do not substitute branded character IP. */
(function(root){
"use strict";
const photos={
 wildlife:[
 ["Lion","1546182990-dffeafbe841d"],["Panda","1564349683136-77e08dba1ef7"],["Dog","1517849845537-4d257902454a"],["Kitten","1511044568932-338cba0ad803"],["Fox","1474511320723-9a56873867b5"],["Cat","1533738363-b7f9aef128ce"],["Elephant","1549366021-9f761d450615"],["Puppy","1560807707-8cc77767d783"],["Retriever","1558788353-f76d92427f16"],["Penguins","1551415923-a2297c7fda79"],
 ["Giraffe","1547721064-da6cfb341d50"],["Deer","1484406566174-9da000fda645"],["Goat","1524024973431-2ad916746881"],["Cattle","1570042225831-d98fa7577f1e"],["Alpine deer","1472396961693-142e6e269027"],["Koala","1632104009230-4c927433490f"],["Lemur","1693740571898-47128f049166"],["Lioness","1769470683197-119f690c01ea"],["Eagle","1512988442538-a42600ac4634"],["Whiskers","1518791841217-8f162f1e1131"],["Tabby","1573865526739-10659fec78a5"],["Green parrot","1552728089-57bdde30beb3"],["Bookworm dog","1535930891776-0c2dfb7fda1a"],["Zebra","1709291083119-1840e0e59875"],["Otter","1705331061640-dc2eab910b74"],["Sea turtle","1437622368342-7a3d73a34c8f"],["Red panda","1729932203780-cc1b9a326be9"],["Rabbit","1713975192202-8144913ca3dc"],["King penguin","1551986782-d0169b3f8fa7"],["Desert giants","1513699227473-7ae8d4545d4e"]
 ],
 cars:[
 ["Black GT","1503376780353-7e6692767b70"],["Blue sportscar","1552519507-da3b142c6e3d"],["Red coupe","1493238792000-8113da705763"],["Red convertible","1507136566006-cfc505b114fc"],["Orange Beetle","1489824904134-891ab64532f1"],["Sport sedan","1525609004556-c46c7d6cf023"],["Street car","1533473359331-0135ef1b58bf"],["Blue coupe","1556213313-fc5be959c4a9"],["Performance car","1492144534655-ae79c964c9d7"],["Retro Mustang","1544896478-d5b709d413c5"],
 ["Blue classic","1520608760-eff2c44afa2b"],["Vintage touring","1741779456831-e2cd573a874d"],["Roadster","1709050939134-4bdf2875debf"],["Track machine","1549317661-bd32c8ce0db2"],["Grand tourer","1494905998402-395d579af36f"],["City classic","1486496572940-2bb2341fdbdf"],["Highway","1519641471654-76ce0107ad1b"],["American classic","1494976388531-d1058494cdd8"],["Vehicle","1565043666747-69f6646db940"],["Rally car","1523983388277-336a66bf9bcd"],["Coupe","1544829099-b9a0c07fad1a"],["Night drive","1597404294360-feeeda04612e"],["Classic","1571607388263-1044f9ea01dd"],["Supercar","1532581140115-3e355d1ed1de"],["Sport","1517524008697-84bbe3c3fd98"],["Red Ferrari","1583121274602-3e2820c69888"],["Turbo","1553440569-bcc63803a83d"],["Yellow coupe","1550355291-bbee04a92027"],["Car culture","1621007947382-bb3c3994e3fb"],["Blue racer","1646840779961-94868341a678"]
 ],
 food:[
 ["Fresh produce","1498837167922-ddd27525d352"],["Brunch","1476224203421-9ac39bcb3327"],["Garden salad","1540189549336-e6e99c3679fe"],["Rice bowl","1546069901-ba9599a7e63c"],["Cake","1565958011703-44f9829ba187"],["Sweet treats","1551024506-0bccd828d307"],["Dinner","1504674900247-0877df9cc836"],["Pizza","1565299624946-b28f40a0ae38"],["Healthy dish","1547592180-85f173990554"],["Vegetables","1512621776951-a57141f2eefd"],
 ["Sandwich","1511690743698-d9d85f2fbf38"],["Street food","1555939594-58d7cb561ad1"],["Pancakes","1567620905732-2d1ec7ab7445"],["Breakfast","1482049016688-2d3e1b311543"],["Noodles","1559847844-5315695dadae"],["Burger","1565299507177-b0ac66763828"],["Rice plate","1589302168068-964664d93dc0"],["Grill","1529042410759-befb1204b468"],["Bread","1509440159596-0249088772ff"],["Ingredients","1543353071-873f17a7a088"],
 ["Seafood","1519708227418-c8fd9a32b7a2"],["Hot pizza","1574071318508-1cdbab80d002"],["Cheeseburger","1568901346375-23c9450c58cd"],["Milk","1550583724-b2692b85b150"],["Pasta","1608897013039-887f21d8c804"],["Honey","1587049352851-8d4e89133924"],["Restaurant","1547573854-74d2a71d0826"],["Salsa","1534939561126-855b8675edd7"],["Frosting","1578985545062-69928b1d9587"],["Chocolate dessert","1662687923931-18c60d75b2fe"]
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
 const skin=["#d59c78","#a36e50","#edc6a1","#815745","#c78a69","#e1b391"][i%6];
 const hair=["#211e31","#5b342f","#a66d42","#e7c77e","#242f3e","#6a4d66","#342119"][i%7];
 const cape=shades[(i*7+3)%shades.length],back=["#b2c5d6","#dccabf","#b2ccc5","#cfb9d3","#d4d1b0"][i%5];
 const gold=["#f7db90","#f5eddf","#f4d2bb"][i%3],style=i%8,eyebrow=i%4;
 const id='night-portrait-'+i;
 const ears=style===4?'<path d="M27 56 14 43l5 24 11 2M73 56l13-13-5 24-11 2" fill="'+skin+'" stroke="#986956" stroke-width="1.2"/>':'';
 const hairBack=style===3?
  '<path d="M15 99Q8 57 21 27Q36 4 50 8Q74 7 81 29Q89 61 85 103L69 92L72 53L26 53L31 93Z" fill="'+cape+'" stroke="'+gold+'" stroke-width="2"/>':
  '<path d="M15 107Q14 60 21 34Q24 11 50 10Q75 11 80 38Q86 64 86 106L72 113Q73 77 67 62L30 63Q26 88 29 113Z" fill="url(#'+id+'-hair)" stroke="#241b1c" stroke-opacity=".15"/>';
 const headpiece=style===0||style===5?
   '<path d="M20 37 25 19 36 29 50 12 64 29 75 19 80 37 73 42H27Z" fill="url(#'+id+'-metal)" stroke="#937044" stroke-width="1.3"/><circle cx="50" cy="31" r="4" fill="'+cape+'" stroke="#faf0cf" stroke-width="1.4"/>':
  style===1||style===7?
   '<path d="M24 41Q50 10 76 41" fill="none" stroke="url(#'+id+'-metal)" stroke-width="4"/><path d="M43 27 50 16 57 27 50 36Z" fill="'+gold+'"/><circle cx="50" cy="26" r="3" fill="'+cape+'"/>':
  style===2?
   '<path d="M20 41Q32 7 53 6L72 41Q57 35 20 41Z" fill="'+cape+'" stroke="'+gold+'" stroke-width="2"/><path d="M17 43Q52 34 79 43" stroke="'+gold+'" stroke-width="4" fill="none"/>':
  style===3?'':style===4?
   '<path d="M21 42Q49 16 79 42" fill="none" stroke="'+gold+'" stroke-width="3"/><circle cx="50" cy="25" r="4" fill="'+cape+'" stroke="'+gold+'" stroke-width="2"/>':
  style===6?
   '<path d="M26 36Q49 23 74 36" fill="none" stroke="'+gold+'" stroke-width="5"/><circle cx="49" cy="31" r="5" fill="'+cape+'" stroke="'+gold+'" stroke-width="1.3"/>':
   '<path d="M23 36 49 24 77 36" fill="none" stroke="'+gold+'" stroke-width="3"/>';
 const fringe=style===6?
  '<path d="M25 48Q37 19 62 24Q74 30 76 49Q55 45 44 34Q35 48 25 48" fill="url(#'+id+'-hair)"/>':
 style===7?
  '<path d="M23 44Q32 13 58 21Q73 26 78 45Q60 32 47 35Q37 47 23 44" fill="url(#'+id+'-hair)"/>':
 style===2?
  '<path d="M26 48Q37 20 60 29L74 49Q60 40 48 38Q42 49 26 48" fill="url(#'+id+'-hair)"/>':
  '<path d="M23 45Q25 15 50 18Q72 18 78 45Q63 40 57 28Q45 46 23 45" fill="url(#'+id+'-hair)"/>';
 const beard=style===5?'<path d="M30 71Q32 94 51 98Q71 91 72 71L64 76Q54 91 49 86Q41 87 36 76Z" fill="'+hair+'"/><path d="M43 76h15" stroke="#eac9aa" stroke-width="1.3"/>':'';
 const clothing=style===2?
  '<path d="M25 97Q41 109 51 121Q63 107 75 96" stroke="'+gold+'" stroke-width="3.4" fill="none"/><circle cx="50" cy="105" r="5" fill="'+gold+'"/>':
 style===5?
  '<path d="M28 90 51 110 73 90" fill="none" stroke="'+gold+'" stroke-width="4"/><path d="M50 100v22M35 111h30" stroke="'+gold+'" stroke-width="1.6"/>':
 style===4?
  '<path d="M34 89 50 102 66 89" stroke="'+gold+'" stroke-width="4" fill="none"/><path d="M50 101 54 109 50 115 46 109Z" fill="'+gold+'"/>':
  '<path d="M28 90 50 108 72 90" fill="none" stroke="'+gold+'" stroke-width="4"/><circle cx="50" cy="105" r="4" fill="'+gold+'"/>';
 const hairDetails=style===1||style===6?
  '<path d="M25 57Q16 80 23 106M74 56Q83 77 75 108" fill="none" stroke="#e8ca9a" stroke-opacity=".4" stroke-width="4"/>':
  style===7?'<path d="M80 44Q96 59 82 88" fill="none" stroke="'+hair+'" stroke-width="7" stroke-linecap="round"/>':'';
 return '<svg class="memory-face-svg" viewBox="0 0 100 122" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">'+
 '<defs><linearGradient id="'+id+'-back" x2=".9" y2="1"><stop stop-color="'+back+'"/><stop offset="1" stop-color="#fbf2d9"/></linearGradient>'+
 '<linearGradient id="'+id+'-skin" x1=".1" x2="1" y2="1"><stop stop-color="#ffe0b7"/><stop offset=".31" stop-color="'+skin+'"/><stop offset="1" stop-color="#8e5848"/></linearGradient>'+
 '<linearGradient id="'+id+'-hair" x2="1" y2=".7"><stop stop-color="'+hair+'"/><stop offset=".5" stop-color="'+hair+'"/><stop offset="1" stop-color="#211e2a"/></linearGradient>'+
 '<linearGradient id="'+id+'-cape" x2="1" y2="1"><stop stop-color="'+cape+'"/><stop offset="1" stop-color="#343f4d"/></linearGradient>'+
 '<linearGradient id="'+id+'-metal" x2=".9" y2="1"><stop stop-color="#fff4bb"/><stop offset=".45" stop-color="'+gold+'"/><stop offset="1" stop-color="#a47c46"/></linearGradient></defs>'+
 '<rect width="100" height="122" fill="url(#'+id+'-back)"/>'+
 '<path d="M13 122V28Q51-8 87 28V122" stroke="#ffffff" stroke-opacity=".2" stroke-width="7" fill="none"/>'+
 '<circle cx="85" cy="16" r="10" fill="#ffffff" opacity=".22"/><path d="m14 22 3 8 8 3-8 3-3 8-3-8-8-3 8-3Z" fill="'+gold+'" opacity=".35"/>'+
 '<path d="M-9 124Q-2 92 25 86L43 81H57L75 86Q99 93 109 124Z" fill="url(#'+id+'-cape)"/>'+
 hairBack+ears+'<path d="M43 78H57V94Q50 101 43 94Z" fill="'+skin+'"/>'+
 '<ellipse cx="50" cy="53" rx="24" ry="32" fill="url(#'+id+'-skin)"/>'+
 '<path d="M30 55Q28 36 38 30M70 55Q70 34 62 29" fill="none" stroke="#fbe2b9" stroke-opacity=".24" stroke-width="2"/>'+
 fringe+hairDetails+
 '<path d="M34 54q5 -'+(eyebrow+2)+' 10 0M56 54q5 -3 10 0" fill="none" stroke="#302b2a" stroke-width="1.7" stroke-linecap="round"/>'+
 '<path d="M34 60Q39 57 44 60M56 60Q61 57 66 60" stroke="#312726" stroke-width="1.8" stroke-linecap="round" fill="none"/>'+
 '<circle cx="40" cy="60" r="1.7" fill="#221c1b"/><circle cx="60" cy="60" r="1.7" fill="#221c1b"/>'+
 '<path d="M50 62 48 71 53 71" stroke="#955e4d" stroke-opacity=".7" stroke-width="1.3" stroke-linecap="round" fill="none"/>'+
 (style===5?'':'<path d="M43 77Q50 '+(i%2?'82':'79')+' 57 77" fill="none" stroke="#7f443f" stroke-width="1.7" stroke-linecap="round"/>')+
 beard+headpiece+
 '<path d="M26 87Q36 91 43 88M57 88Q67 91 75 87" stroke="#f8e5d1" stroke-opacity=".43" stroke-width="1.7" fill="none"/>'+
 clothing+
 '<path d="M4 111Q26 96 43 111M60 111Q84 96 96 111" fill="none" stroke="#fff" stroke-opacity=".12" stroke-width="2.5"/>'+
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
