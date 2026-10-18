/**
 * Curated real-world photographs for NIGHT Play Jigsaw.
 * Images are served through Unsplash's image CDN. See PHOTO_CREDITS.md.
 * These are individual landscape, wildlife, place and flower pictures, not
 * generated art or a general-purpose stock-photo search/download library.
 */
export const photos = Object.freeze([
 {id:'photo-alpine-lake',name:'Alpine Lake',category:'nature',asset:'photo-1470770841072-f978cf4d019e'},
 {id:'photo-forest-light',name:'Forest Light',category:'nature',asset:'photo-1441974231531-c6227db76b6e'},
 {id:'photo-golden-coast',name:'Golden Coast',category:'nature',asset:'photo-1507525428034-b723cf961d3e'},
 {id:'photo-mountain-air',name:'Mountain Air',category:'nature',asset:'photo-1501785888041-af3ef285b470'},
 {id:'photo-tabby-cat',name:'Curious Cat',category:'animals',asset:'photo-1514888286974-6c03e2ca1dba'},
 {id:'photo-golden-dog',name:'Golden Friend',category:'animals',asset:'photo-1552053831-71594a27632d'},
 {id:'photo-red-fox',name:'Wild Fox',category:'animals',asset:'photo-1474511320723-9a56873867b5'},
 {id:'photo-paris-evening',name:'Paris Evening',category:'places',asset:'photo-1502602898657-3e91760cbb34'},
 {id:'photo-tokyo-night',name:'Tokyo Nights',category:'places',asset:'photo-1540959733332-eab4deabeeaf'},
 {id:'photo-taj-mahal',name:'Morning in Agra',category:'places',asset:'photo-1548013146-72479768bada'},
 {id:'photo-pink-blooms',name:'Pink Blossoms',category:'flowers',asset:'photo-1490750967868-88aa4486c946'},
 {id:'photo-bouquet',name:'Spring Bouquet',category:'flowers',asset:'photo-1468327768560-75b778cbb551'},
 {id:'photo-starry-sky',name:'Under the Stars',category:'sky',asset:'photo-1519681393784-d120267933ba'},
 {id:'photo-sunset-sky',name:'Last Light',category:'sky',asset:'photo-1470252649378-9c29740c9fa8'}
]);
export const getPhoto=id=>photos.find(photo=>photo.id===id);
export function photoUrl(photo,thumbnail=false){
 const size=thumbnail?'w=480&h=360&q=72':'w=1200&h=900&q=85';
 return 'https://images.unsplash.com/'+photo.asset+'?auto=format&fit=crop&crop=entropy&'+size;
}
