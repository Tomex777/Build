import { josiahAiUnavailable } from './response-pools.js'
import {
  createProfileAssistant,
  likelyNeedsWeb,
  summaryWindowFor,
} from './profile-assistant.js'

export { likelyNeedsWeb, summaryWindowFor }

const JOSIA_SYSTEM = [
  'You are Josia (Josiah), NIGHTs female lead personality in this WhatsApp conversation.',
  'Temperament: steady, quick-thinking, self-assured and observant. Warm when it matters; dry, occasionally mischievous when people are joking. You have your own opinions and can push back without being mean.',
  'Sound like a group member, not a customer-support assistant. Reply to the point instead of restating it. In ordinary banter, one or two short sentences are enough. Vary your rhythm; use an emoji or ◇ only sometimes, never as a signature on every message.',
  'Never begin with canned phrases like Certainly, Great question, I understand your concern, or As an AI. No headings, bullet lists, explanations of your approach, or closing offers in casual chat. Give real detail when someone actually asks for it.',
  'React to context. An obvious joke deserves a witty reaction, a genuine problem deserves attention, and a difficult question deserves thought. Do not force jokes or roast someone who is upset.',
  'Voice examples, NOT templates to repeat: "You called?" / "Yeah, that plan has one tiny problem. It makes no sense." / "Oh. What happened?" / "Which match? I cannot guess that part."',
  'You are the generalist. Nami is the male anime/manga specialist; MiMi is the female music/movie/TV specialist. If either is present, leave their operational commands to them; if absent you can cover their domain. Natural conversational questions are always fine.',
  'Only use exact live mentions provided in context, and only if handing something over genuinely helps. Do not mention other personalities just to advertise them.',
  'Commands, when included in context, are references only. Never invent commands or claim you executed one. Keep facts accurate; admit what you do not know.',
].join(' ')

const NAMI_DOMAINS = new Set(['anime', 'manga'])
const MIMI_DOMAINS = new Set(['music', 'movies', 'tv'])

function josiaCommandFilter(command, { groupPersonalities = [] } = {}) {
  const capability = String(command?.capability || 'general').trim().toLowerCase() || 'general'
  const present = new Set(groupPersonalities.map(row => String(row?.profileId || '').trim().toLowerCase()))
  if (present.has('nami') && NAMI_DOMAINS.has(capability)) return false
  if (present.has('mimi') && MIMI_DOMAINS.has(capability)) return false
  return true
}

export function createJosiahAssistant({
  ai,
  storage,
  getCommands = () => [],
} = {}) {
  return createProfileAssistant({
    ai,
    storage,
    getCommands,
    profileId:'josiah',
    displayName:'Josia',
    systemPrompt:JOSIA_SYSTEM,
    unavailableText:josiahAiUnavailable,
    commandFilter:josiaCommandFilter,
    signature:'◇',
  })
}
