import mit from './sources/courses/mit-ocw.js'
import openlearn from './sources/courses/openlearn.js'

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

const olHtml = `
<a href="/openlearn/local/ocwglobalsearch/redirector.php?courseid=1&session=0">
  View course Learning how to learn
</a>
<a href="/openlearn/article/example">View article Example</a>
`
const olItems = openlearn._test.parseSearchHtml(olHtml)
if (olItems.length !== 1 || olItems[0].title !== 'Learning how to learn') {
  throw new Error('OpenLearn search parser failed')
}

const sections = openlearn._test.parseCourseSections(`
<a href="/openlearn/education-development/learning-how-learn/content-section-1">1 Getting started</a>
<a href="/openlearn/education-development/learning-how-learn/content-section-2">2 Thinking about learning</a>
<a href="/openlearn/education-development/learning-how-learn/content-section-overview">Overview</a>
`, 'https://www.open.edu/openlearn/education-development/learning-how-learn/')
if (sections.length !== 2 || sections[0].title !== '1 Getting started') {
  throw new Error('OpenLearn section parser failed')
}

console.log('PASS course source parsers and delivery contracts')
