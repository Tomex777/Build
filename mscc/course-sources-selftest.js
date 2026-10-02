import mit from './sources/courses/mit-ocw.js'
import wikiversity from './sources/courses/wikiversity.js'

const mitHtml = `
<a href="/courses/6-0001-introduction-to-computer-science-and-programming-in-python-fall-2016/">
  Introduction to Computer Science and Programming in Python
</a>
<a href="/courses/6-0001-introduction-to-computer-science-and-programming-in-python-fall-2016/">duplicate</a>
`
const mitItems = mit._test.parseSearchHtml(mitHtml)
if (mitItems.length !== 1 || !mitItems[0].url.includes('/courses/6-0001-')) {
  throw new Error('MIT OCW search parser failed')
}

const api = mit._test.normalizeApiRow({
  id:1,
  readable_id:'ocw-6-0001-fall-2016',
  title:'Introduction to Computer Science and Programming in Python',
  description:'Programming course',
  platform:{ code:'ocw' },
  url:'https://ocw.mit.edu/courses/6-0001-introduction-to-computer-science-and-programming-in-python-fall-2016/',
  course:{ course_numbers:[{ value:'6.0001' }] },
  runs:[{ instructors:[{ name:'Ana Bell' }] }],
})
if (!api || api.title !== 'Introduction to Computer Science and Programming in Python') {
  throw new Error('MIT OCW API normalization failed')
}

const zipLinks = mit._test.parseDownloadPage(
  '<a href="../course.zip">file_download Download course</a>',
  'https://ocw.mit.edu/courses/example/download/',
)
if (zipLinks.length !== 1 || zipLinks[0].url !== 'https://ocw.mit.edu/courses/example/course.zip') {
  throw new Error('MIT OCW download parser failed')
}

const wikiItem = wikiversity._test.normalizeSearchRow({
  title:'Python Programming',
  snippet:'An open <span class="searchmatch">Python</span> learning resource.',
})
if (!wikiItem || wikiItem.pageTitle !== 'Python Programming' || !wikiItem.url.includes('Python_Programming')) {
  throw new Error('Wikiversity search normalization failed')
}

const sections = wikiversity._test.normalizeSections({
  parse:{
    title:'Python Programming',
    sections:[
      { index:'1', line:'Introduction', anchor:'Introduction' },
      { index:'2', line:'Variables', anchor:'Variables' },
    ],
  },
})
if (sections.length !== 2 || sections[1].sectionIndex !== '2' || sections[1].title !== 'Variables') {
  throw new Error('Wikiversity section normalization failed')
}

const wikiText = wikiversity._test.parseBody({
  parse:{ text:'<div><p>Hello <b>course</b> learner.</p></div>' },
})
if (!wikiText.includes('Hello course learner.')) {
  throw new Error('Wikiversity page text conversion failed')
}

console.log('PASS course source parsers and delivery contracts')
