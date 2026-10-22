// NIGHT Channels — opt-in text-only caption transforms. Not a platform-moderation bypass.
const WORD_CHAR = '[\\p{L}\\p{N}\\p{M}_]';
const CONTROL = /[\u0000-\u001f\u007f-\u009f\u202a-\u202e\u2066-\u2069]/gu;
const UNSAFE_RULE_TEXT = /[\u0000-\u001f\u007f-\u009f\u202a-\u202e\u2066-\u2069]/u;
const escapeRegex = value => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const bytes = value => Buffer.byteLength(value, 'utf8');

export function replaceCaptionWords(input, { enabled = false, rules = [] } = {}) {
  if (typeof input !== 'string') throw new TypeError('Caption must be a string.');
  if (!enabled) return input;
  if (!Array.isArray(rules) || rules.length > 32) throw new RangeError('Expected at most 32 text replacement rules.');
  if (rules.length === 0) return input;
  const seen = new Set();
  const pairs = rules.map(rule => {
    if (typeof rule?.from !== 'string' || typeof rule?.to !== 'string') throw new TypeError('Invalid replacement.');
    const from = rule.from.trim().normalize('NFC');
    if (!from || from.length > 80 || rule.to.length > 120 ||
        UNSAFE_RULE_TEXT.test(from) || UNSAFE_RULE_TEXT.test(rule.to))
      throw new RangeError('Invalid replacement length or phrase.');
    if (seen.has(from.toLocaleLowerCase('en'))) throw new Error('Duplicate replacement phrase.');
    seen.add(from.toLocaleLowerCase('en'));
    const pattern = escapeRegex(from).replace(/\s+/gu, '\\s+');
    return { from, to: rule.to, pattern };
  }).sort((a,b) => b.from.length - a.from.length);
  // ONE pass: an output such as f*ck cannot be replaced a second time.
  const re = new RegExp(`(?<!${WORD_CHAR})(?:${pairs.map(p=>p.pattern).join('|')})(?!${WORD_CHAR})`, 'giu');
  const lookup = new Map(pairs.map(p => [p.from.toLocaleLowerCase('en'),p.to]));
  return input.normalize('NFC').replace(re, match => {
    const exact = lookup.get(match.replace(/\s+/gu, ' ').toLocaleLowerCase('en'));
    return exact === undefined ? match : exact;
  });
}

export function composeChannelCaption({
  text = '', replacements = {enabled:false}, poster = {enabled:false}, maxUtf8Bytes = 2048,
} = {}) {
  if (typeof text !== 'string') throw new TypeError('Caption must be a string.');
  if (!Number.isInteger(maxUtf8Bytes) || maxUtf8Bytes < 1 || maxUtf8Bytes > 65536) throw new RangeError('Invalid caption budget.');
  // A malicious source can deliver megabytes of text before formatting. Bound
  // the work BEFORE Unicode normalization / regex transforms on the 1 GB bot.
  if (bytes(text) > Math.max(8192, maxUtf8Bytes * 4))
    throw new RangeError('Unformatted caption exceeds safe processing limit.');
  let main = replaceCaptionWords(text, replacements);
  main = main.replace(/[\s\uFEFF]+$/u, '');
  if (poster?.enabled === true) {
    if (typeof poster.name !== 'string' || !poster.name.trim()) throw new Error('Poster signature requires an explicitly configured person/persona.');
    // Poster, never the channel name. Keep signature to a single safe line.
    const person = poster.name.replace(CONTROL, ' ').replace(/\s+/gu, ' ').trim();
    if (!person || [...person].length > 80 || person.includes('>')) throw new Error('Invalid poster display name.');
    main += (main ? '\n\n' : '') + '> ' + person;
  }
  if (bytes(main) > maxUtf8Bytes) throw new RangeError('Formatted caption exceeds the configured WhatsApp media caption byte budget.');
  return main;
}
