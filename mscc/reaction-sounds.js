// NIGHT private-group reaction soundboard. Metadata only: audio files are not distributed in the repository.
import { access, readFile } from 'node:fs/promises';
import { join } from 'node:path';

const CLIPS = Object.freeze([
  { id:'sp_sorry', label:'Cartman apologizing', file:'sp_sorry.ogg', auto:true, when:'You are theatrically admitting YOUR OWN small mistake or backing down from a silly bluff.' },
  { id:'sp_no_no_no', label:'Cartman refusing', file:'sp_no_no_no.ogg', auto:true, when:'You playfully insist YOU do not want something; never refuse a legitimate request just for the sound.' },
  { id:'sp_cry', label:'Cartman crying', file:'sp_cry.ogg', auto:true, when:'You theatrically lament YOUR OWN silly blunder, mock defeat or frustration; NEVER another member losing, embarrassment, pain or real grief.' },
  { id:'sp_authoritah', label:'Cartman authoritah', file:'sp_authoritah.ogg', auto:true, when:'You personally choose a comically bossy, fake-authority performance; not because you judged a user disrespectful.' },
  { id:'sp_laugh', label:'Cartman laughing', file:'sp_laugh.ogg', auto:true, when:'You are amused by YOUR OWN silly thought, punchline or absurd observation; do not laugh at another member’s misfortune.' },
  { id:'sp_sonofabitch', label:'Cartman angry outburst', file:'sp_sonofabitch.ogg', auto:false },
  { id:'sp_why_not', label:'Cartman why not', file:'sp_why_not.ogg', auto:false },
  { id:'sp_kyles_mom', label:'Kyle’s mom song', file:'sp_kyles_mom.ogg', auto:false },
  { id:'sp_gay', label:'Short sensitive reaction', file:'sp_gay.ogg', auto:false },
  { id:'sp_video_01', label:'Uploaded video · full (unreviewed)', file:'sp_video_01.ogg', auto:false },
  { id:'sp_video_a', label:'Uploaded video · A (unreviewed)', file:'sp_video_a.ogg', auto:false },
  { id:'sp_video_b', label:'Uploaded video · B (unreviewed)', file:'sp_video_b.ogg', auto:false },
  { id:'sp_video_c', label:'Uploaded video · C (unreviewed)', file:'sp_video_c.ogg', auto:false },
  { id:'sp_video_d', label:'Uploaded video · D (unreviewed)', file:'sp_video_d.ogg', auto:false },
]);

const CLIP_BY_ID = new Map(CLIPS.map(clip => [clip.id, clip]));
const ALIASES = new Map(Object.entries({
  sorry:'sp_sorry', no:'sp_no_no_no', no_no_no:'sp_no_no_no',
  cry:'sp_cry', crying:'sp_cry', authoritah:'sp_authoritah',
  authority:'sp_authoritah', laugh:'sp_laugh', sonofabitch:'sp_sonofabitch',
  why_not:'sp_why_not', kyles_mom:'sp_kyles_mom', gay:'sp_gay',
  video:'sp_video_01', video_full:'sp_video_01',
  video_a:'sp_video_a', video_b:'sp_video_b', video_c:'sp_video_c', video_d:'sp_video_d',
}));
const normalize = value => String(value || '').trim().toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
const on = value => ['true', '1', 'yes', 'on'].includes(String(value ?? '').trim().toLowerCase());
const isGroup = jid => /^[^@\s]+@g\.us$/.test(String(jid || ''));

export function resolveReactionSound(name) {
  const key = normalize(name);
  const id = ALIASES.get(key) || key;
  return CLIP_BY_ID.get(id) || null;
}

export function parseReactionSoundDirective(response) {
  // Treat the model's marker as untrusted input: known IDs only, max one marker.
  // Always remove directive-looking output from the text, even if invalid.
  const raw = String(response || '');
  const markers = [...raw.matchAll(/\[\[night_sound:([^\]\r\n]{0,90})\]\]/gi)];
  const text = raw.replace(/\[\[night_sound:[^\]\r\n]{0,90}\]\]/gi, '')
    .replace(/\n{3,}/g, '\n\n').trim();
  const id = markers.length === 1 ? String(markers[0][1]).trim().toLowerCase() : '';
  return { text, id:CLIP_BY_ID.get(id)?.auto === true ? id : '' };
}

export function createReactionSounds({
  env = process.env, now = () => Date.now(), accessFile = access, read = readFile,
} = {}) {
  const groups = new Set(String(env.MSCC_REACTION_SOUND_GROUPS || '').split(/[\s,;]+/)
    .filter(isGroup));
  const enabled = on(env.MSCC_REACTION_SOUNDS_ENABLED) && groups.size > 0;
  const autoEnabled = enabled && on(env.MSCC_REACTION_SOUNDS_AUTO);
  const directory = String(env.MSCC_REACTION_SOUNDS_DIR || '/var/lib/mscc/sounds/southpark').trim();
  const parsedCooldown = Number.parseInt(String(env.MSCC_REACTION_SOUND_COOLDOWN_MS || ''), 10);
  const cooldownMs = Number.isFinite(parsedCooldown)
    ? Math.max(60000, Math.min(3600000, parsedCooldown)) : 6 * 60000;
  const lastAutoByGroup = new Map();
  const inFlight = new Set();

  const allowed = jid => enabled && isGroup(jid) && groups.has(jid);
  const pathFor = clip => join(directory, clip.file);
  async function playable(clip) {
    try { await accessFile(pathFor(clip)); return true; } catch { return false; }
  }
  function canAuto(jid) {
    return allowed(jid) && autoEnabled && !inFlight.has(jid)
      && now() - (lastAutoByGroup.get(jid) ?? -Infinity) >= cooldownMs;
  }
  async function promptFor(jid) {
    if (!canAuto(jid)) return '';
    const choices = [];
    for (const clip of CLIPS.filter(item => item.auto)) {
      if (await playable(clip)) choices.push(clip);
    }
    if (!choices.length) return '';
    return [
      'OPTIONAL NIGHT REACTION VOICE NOTES (only for this permitted WhatsApp group):',
      'You may choose AT MOST ONE of the existing audio clips below as part of YOUR OWN comedic performance, never as a mechanical reaction to another person losing, embarrassment, identity, illness or grief.',
      'Usually choose NONE. If an existing clip perfectly matches what YOU want to express, append exactly one tag of form [[night_sound:ID]] at the very end of your normal reply.',
      'Never quote or explain the tag. No additional AI call is made. Do not invent IDs, do not choose a sound just because the user mentions its keywords, and never describe yourself as literally feeling an emotion.',
      ...choices.map(clip => clip.id + ': ' + clip.when),
    ].join('\n');
  }
  async function play(sock, jid, requested, { automatic = false, quoted = null } = {}) {
    if (!allowed(jid)) return { ok:false, reason:'group_not_enabled' };
    const clip = resolveReactionSound(requested);
    if (!clip) return { ok:false, reason:'unknown_sound' };
    if (automatic && (!clip.auto || !canAuto(jid))) return { ok:false, reason:'automatic_not_available' };
    if (inFlight.has(jid)) return { ok:false, reason:'busy' };
    inFlight.add(jid);
    try {
      // Reads only hardcoded leaf filenames, never arbitrary user/model-provided paths.
      const audio = await read(pathFor(clip));
      if (!audio?.length) throw new Error('empty audio');
      await sock.sendMessage(jid, {
        audio, mimetype:'audio/ogg; codecs=opus', ptt:true,
      }, quoted ? { quoted } : undefined);
      if (automatic) lastAutoByGroup.set(jid, now());
      return { ok:true, id:clip.id };
    } catch (error) {
      console.warn('NIGHT reaction sound unavailable:', clip.id, error?.message || error);
      return { ok:false, reason:'audio_unavailable' };
    } finally {
      inFlight.delete(jid);
    }
  }
  function manualList(jid, prefix = '.') {
    if (!allowed(jid)) return 'South Park reactions are not enabled in this group.';
    return [
      '*NIGHT · South Park sounds*',
      ...CLIPS.map(c => prefix + 'sp ' + c.id.slice(3) + ' — ' + c.label),
      '',
      'Audio is shared only where the group owner enables this private soundboard.',
    ].join('\n');
  }
  return { enabled, autoEnabled, allowed, canAuto, promptFor, play, manualList };
}
