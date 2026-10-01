import { parseNumberSelection } from './number-selection.js'

const token = value => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
const untoken = value => JSON.parse(Buffer.from(String(value || ''), 'base64url').toString('utf8'))

const titleOf = item => String(item?.title || item?.name || 'Untitled')
const idOf = item => String(item?.id ?? item?.slug ?? item?.url ?? titleOf(item))

function normalizeCourse(item, index = 0) {
  return {
    id:idOf(item),
    number:String(index + 1),
    title:titleOf(item),
    instructor:String(item?.instructor || item?.author || '').trim(),
    description:String(item?.description || '').trim(),
    raw:item,
  }
}

function normalizeContent(item, index = 0) {
  return {
    id:String(item?.id ?? item?.url ?? index + 1),
    number:String(index + 1),
    title:String(item?.title || item?.name || `Part ${index + 1}`),
    section:String(item?.section || item?.module || '').trim(),
    type:String(item?.type || item?.format || '').trim(),
    raw:item,
  }
}

function parsePick(value, entries = []) {
  const n = Number(String(value || '').trim())
  if (!Number.isInteger(n) || n < 1 || n > entries.length) return null
  return entries[n - 1]
}

function outcomeError(ctx, outcome) {
  if (outcome?.status === 'no-sources') return ctx.reply('No course sources are installed yet.')
  if (outcome?.status === 'source-error') return ctx.reply(`${outcome.source?.name || 'The course source'} could not complete that request.`)
  if (outcome?.status === 'all-failed') return ctx.reply('All configured course sources failed for that request.')
  return ctx.reply('Could not complete that course request.')
}

async function chooseSource(ctx, query) {
  const sources = ctx.listSources('courses')
  const prefix = ctx.publicPrefix || '.'
  if (!sources.length) return outcomeError(ctx, { status:'no-sources' })
  return ctx.replyList({
    title:'Choose course source',
    text:`Choose a source for “${query}”.`,
    buttonText:'Choose source',
    footer:`Save a default: ${prefix}source courses <source>`,
    rows:sources.map(source => ({
      title:source.name,
      description:source.description || 'Use for this search',
      id:`${prefix}course ~source ${source.id} ${token(query)}`,
    })),
  })
}

async function search(ctx, { query, sourceId = '' }) {
  if (!query) return ctx.reply('Usage: .course <topic>')

  const outcome = await ctx.executeSource({
    capability:'courses',
    explicitSource:sourceId,
    payload:{ action:'search', query },
  })
  if (outcome.status === 'choice-required') return chooseSource(ctx, query)
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)

  const result = outcome.result || {}
  const courses = (Array.isArray(result.items) ? result.items : result.item ? [result.item] : [])
    .map(normalizeCourse)

  if (!courses.length) return ctx.reply(`No courses found for “${query}”.`)

  ctx.setCommandReplySession?.({
    kind:'number-selection',
    command:'course',
    stage:'course',
    sourceId:outcome.source.id,
    entries:courses,
    expiresAt:Date.now() + 30 * 60000,
  })

  return ctx.reply([
    `*Courses for “${query}”*`,
    '',
    ...courses.slice(0, 25).map(course => {
      const extra = [course.instructor, course.description].filter(Boolean).join(' • ')
      return `${course.number}. ${course.title}${extra ? ` — ${extra}` : ''}`
    }),
    '',
    'Reply with the course number.',
  ].join('\n'))
}

async function loadContents(ctx, { sourceId, course }) {
  const outcome = await ctx.executeSource({
    capability:'courses',
    explicitSource:sourceId,
    payload:{ action:'contents', itemId:course.id, item:course.raw || course },
  })
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)

  const result = outcome.result || {}
  const entries = (result.contents || result.lessons || result.items || []).map(normalizeContent)
  if (!entries.length) {
    return downloadCourse(ctx, { sourceId, course })
  }

  ctx.setCommandReplySession?.({
    kind:'number-selection',
    command:'course',
    stage:'content',
    sourceId,
    course,
    entries,
    expiresAt:Date.now() + 30 * 60000,
  })

  return ctx.reply([
    `*${course.title}*`,
    '',
    ...entries.slice(0, 100).map(entry => {
      const extra = [entry.section, entry.type].filter(Boolean).join(' • ')
      return `${entry.number}. ${entry.title}${extra ? ` — ${extra}` : ''}`
    }),
    '',
    'Reply with the part number(s) you want.',
    'Examples: 1   •   1,3,5   •   1-4',
  ].join('\n'))
}

async function downloadCourse(ctx, { sourceId, course }) {
  ctx.clearCommandReplySession?.()
  const outcome = await ctx.executeSource({
    capability:'courses',
    explicitSource:sourceId,
    payload:{ action:'downloadCourse', itemId:course.id, item:course.raw || course, delivery:'document' },
  })
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)
  if (outcome.result?.delivered === true) return true
  if (typeof outcome.result === 'string') return ctx.reply(outcome.result)
  if (outcome.result?.text) return ctx.reply(String(outcome.result.text))
  return ctx.reply(`Course download started: ${course.title}.`)
}

async function downloadContent(ctx, { sourceId, course, selected }) {
  ctx.clearCommandReplySession?.()
  const messages = []
  for (const entry of selected) {
    const outcome = await ctx.executeSource({
      capability:'courses',
      explicitSource:sourceId,
      payload:{
        action:'download',
        itemId:course.id,
        item:course.raw || course,
        contentId:entry.id,
        content:entry.raw || entry,
        delivery:'document',
      },
    })
    if (outcome.status !== 'ok') return outcomeError(ctx, outcome)
    if (typeof outcome.result === 'string') messages.push(outcome.result)
    else if (outcome.result?.text) messages.push(String(outcome.result.text))
  }

  if (messages.length) return ctx.reply(messages.join('\n'))
  return ctx.reply(`Started ${selected.length} course part${selected.length === 1 ? '' : 's'}.`)
}

async function handleNumbers(ctx) {
  const session = ctx.getCommandReplySession?.()
  if (!session || session.command !== 'course' || session.kind !== 'number-selection') {
    return ctx.reply('That course selection expired. Run .course again.')
  }

  if (session.stage === 'course') {
    const selected = parsePick(ctx.commandReplyInput, session.entries || [])
    if (!selected) return ctx.reply('Reply with one number from the course list.')
    return loadContents(ctx, { sourceId:session.sourceId, course:selected })
  }

  if (session.stage === 'content') {
    const parsed = parseNumberSelection(ctx.commandReplyInput, session.entries || [], {
      numberOf:entry => entry.number,
      maxSelected:100,
    })
    if (!parsed.ok) return ctx.reply('I could not match those course-part numbers. Try: 1 or 1,3,5 or 1-4')
    return downloadContent(ctx, {
      sourceId:session.sourceId,
      course:session.course,
      selected:parsed.selected,
    })
  }

  return ctx.reply('That course selection expired. Run .course again.')
}

export async function runCourseCommand(ctx, { args = [] } = {}) {
  const first = String(args[0] || '')
  if (first === '~numbers') return handleNumbers(ctx)
  if (first === '~source') {
    try {
      return search(ctx, {
        sourceId:String(args[1] || ''),
        query:untoken(args[2] || ''),
      })
    } catch {
      return ctx.reply('That course source selection expired. Run .course again.')
    }
  }
  return search(ctx, { query:args.join(' ').trim() })
}
