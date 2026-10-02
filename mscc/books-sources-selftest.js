import { SourceRegistry } from './source-registry.js'
import gutenberg from './sources/books/gutenberg.js'
import standardEbooks from './sources/books/standard-ebooks.js'
import openLibrary from './sources/books/openlibrary.js'
import novelBuddy from './sources/books/novelbuddy.js'

const registry = new SourceRegistry({
  rootUrl:new URL('./sources/', import.meta.url),
  storage:{ brandForCapability:() => 'MSCC' },
})
await registry.load()

const ids = registry.list('books').map(source => source.id).sort()
const expected = ['gutenberg','novelbuddy','openlibrary','standard-ebooks'].sort()
if (ids.join('|') !== expected.join('|')) {
  throw new Error('Unexpected books source set: ' + ids.join('|'))
}
if (registry.mode('books') !== 'user-choice') {
  throw new Error('Books must remain user-selectable by source.')
}

const gutSearch = gutenberg._test.parseSearch(`
<li class="booklink">
  <a href="/ebooks/1342">
    <img src="/cache/epub/1342/pg1342.cover.medium.jpg">
    <span class="title">Pride and Prejudice</span>
    <span class="subtitle">Jane Austen</span>
  </a>
</li>`)
if (gutSearch.length !== 1 || gutSearch[0].id !== '1342' || !gutSearch[0].cover.includes('pg1342.cover.medium.jpg')) {
  throw new Error('Gutenberg search parsing failed.')
}
const gutRows = gutenberg._test.parseEditions(`
<a href="/ebooks/1342.epub3.images">EPUB3 (with images)</a>
<a href="/ebooks/1342.txt.utf-8">Plain Text UTF-8</a>
`)
if (gutRows.map(row => row.format).join('|') !== 'EPUB|TXT') {
  throw new Error('Gutenberg edition parsing failed.')
}

const seHtml = `
<a href="/ebooks/charlotte-bronte">Charlotte Brontë</a>
<a href="/ebooks/charlotte-bronte/jane-eyre"><span>Jane Eyre</span></a>
`
const seItems = standardEbooks._test.parseSearch(seHtml)
if (seItems.length !== 1 || seItems[0].title !== 'Jane Eyre') {
  throw new Error('Standard Ebooks search parsing failed.')
}
if (!seItems[0].cover.includes('/downloads/cover.jpg?source=download')) {
  throw new Error('Standard Ebooks cover URL missing.')
}
const seEditions = standardEbooks._test.parseEditions(`
<a href="/ebooks/charlotte-bronte/jane-eyre/downloads/jane-eyre.epub">Compatible epub</a>
<a href="/ebooks/charlotte-bronte/jane-eyre/downloads/jane-eyre.kepub.epub">kepub</a>
<a href="/ebooks/charlotte-bronte/jane-eyre/downloads/jane-eyre.azw3">azw3</a>
`)
if (!seEditions.some(row => row.format === 'EPUB' && row.url.includes('jane-eyre.epub?source=download'))) {
  throw new Error('Standard Ebooks download-link parsing failed.')
}

const iaRows = openLibrary._test.archiveFormats('public-item', {
  files:[
    { name:'book.epub', size:'123456' },
    { name:'book.pdf', size:'456789' },
    { name:'secret.epub', size:'123', private:true },
    { name:'book_meta.xml', size:'10' },
  ],
})
if (iaRows.length !== 2 || iaRows.some(row => /secret|meta/.test(row.url))) {
  throw new Error('Open Library public archive filtering failed.')
}

const nbItems = novelBuddy._test.searchItems({
  data:{ items:[{ id:'123', name:'Example Novel', url:'/novel/example-novel', slug:'example-novel', cover:'/covers/example.jpg', authors:[{name:'Example Author'}] }] },
})
if (nbItems.length !== 1 || nbItems[0].id !== '123' || !nbItems[0].url.startsWith('https://novelbuddy.me/')) {
  throw new Error('NovelBuddy search result parsing failed.')
}
if (nbItems[0].author !== 'Example Author' || !nbItems[0].cover.endsWith('/covers/example.jpg')) {
  throw new Error('NovelBuddy cover/author metadata missing.')
}
const next = novelBuddy._test.nextData('<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"initialManga":{"id":"123"}}}}</script>')
if (next?.props?.pageProps?.initialManga?.id !== '123') {
  throw new Error('NovelBuddy __NEXT_DATA__ parsing failed.')
}

console.log('PASS books/novels sources: ' + expected.join(' | '))
