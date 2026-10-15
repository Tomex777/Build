# NIGHT South Park reactions — private audio deployment

This is a **group-only, opt-in** soundboard and optional AI performance layer for MSCC. It integrates with the existing Baileys handler and **existing** Groq AI reply; there is no second AI request, no speech synthesis, and no Voicy API dependency.

## Owner-only WhatsApp setup (recommended)

NIGHT now supports importing a locally prepared **authorized** Ogg/Opus sound pack directly from a WhatsApp document, without logging in to the VM. Only the primary NIGHT owner may operate setup commands.

- In a private DM with NIGHT, send the prepared ZIP as a document with caption `.sp import`, or reply to that document with `.sp import`. It validates filenames, size, and media signatures before writing to the configured private persistent directory.
- In your chosen group, send `.sp on` to opt the group into manual and occasional automatic AI reactions. Use `.sp auto off` for manual-only reactions, `.sp auto on` to re-enable them, or `.sp off` to disable the group.
- Verify functionality with `.sp list` and an explicit short reaction command.

No downloaded media is checked into the repository. Use recordings for which you have appropriate rights.

## Production enablement (not automatically enabled on merge)

1. Use audio you are authorized to use. This public repository deliberately contains **metadata and playback code only**. The user-supplied ZIP stays private. Do **not** commit downloaded TV-show recordings to this repo.
2. Put the files from the provided private NIGHT_SouthPark_Reaction_Pack_v2.zip in the persistent private server directory. Only the .ogg files are needed. For example, after transferring the ZIP to a server that you control:

   ~~~bash
   mkdir -p /var/lib/mscc/sounds/southpark
   unzip -j -o NIGHT_SouthPark_Reaction_Pack_v2.zip '*/audio/*.ogg' -d /var/lib/mscc/sounds/southpark
   ~~~

   Ensure the server runtime user can read the directory. Use existing private file transfer, **not public GitHub**.

3. In the runtime environment, set:

   ~~~env
   MSCC_REACTION_SOUNDS_ENABLED=true
   MSCC_REACTION_SOUNDS_AUTO=true
   MSCC_REACTION_SOUND_GROUPS=120363xxxxxxxxxx@g.us
   MSCC_REACTION_SOUNDS_DIR=/var/lib/mscc/sounds/southpark
   MSCC_REACTION_SOUND_COOLDOWN_MS=360000
   ~~~

   Replace the example with the **actual** group JID. You can comma-separate several group IDs. Nothing activates without an exact group JID.

4. Restart the production MSCC process via the established deployment. Test .sp list inside the enabled group; then .sp authoritah, .sp laugh, .sp cry, and .sp video_a. Outside the allowlist the command must refuse to play.
5. Address a live personality (Josia, MiMi, Nami) inside that group. The existing AI reply **may** optionally append one internal sound marker; NIGHT strips it and sends a voice note after its normal response. Most replies should send no sound. This **never** requires a second model request. AI-to-AI replies, DMs, group welcome messages, and group summary responses do not auto-play sounds.

## Catalog & review

| Manual command | File | Auto eligible |
| --- | --- | --- |
| .sp sorry | sp_sorry.ogg | yes |
| .sp no | sp_no_no_no.ogg | yes |
| .sp cry | sp_cry.ogg | yes — NIGHT dramatizes its OWN mistake, not someone else's loss |
| .sp authoritah | sp_authoritah.ogg | yes |
| .sp laugh | sp_laugh.ogg | yes — NIGHT expresses its OWN amusement |
| .sp why_not | sp_why_not.ogg | no |
| .sp sonofabitch | sp_sonofabitch.ogg | no |
| .sp kyles_mom | sp_kyles_mom.ogg | no |
| .sp gay | sp_gay.ogg | no; context-sensitive |
| .sp video_full | sp_video_01.ogg | no; needs speech review |
| .sp video_a/b/c/d | sp_video_a/b/c/d.ogg | no; preliminary silence-based cuts only |

The video excerpts have **not** been verified for exact wording or clean dialogue boundaries. They remain manual-only. Clip aliases are fixed; no arbitrary URL, file path, or remote user content is fetched.

## Testing, rollout and rollback

- Local: cd mscc && node reaction-sounds-selftest.js && node --check index.js && node --check profile-assistant.js && node --check commands/fun/sp.js
- Staging: allowlist a single test group, send .sp list, play one short clip, trigger one natural AI reply, verify cooldown and no marker leakage.
- Production: preserve the persistent audio directory across container restarts and deploys.
- Immediate rollback: set MSCC_REACTION_SOUNDS_ENABLED=false and restart. Set MSCC_REACTION_SOUNDS_AUTO=false to retain manual playback only.

## Notes

- Playback uses local .ogg Opus with ptt:true, no runtime FFmpeg.
- At most one auto-sound is selected per AI response, with a six-minute default **per-group** cooldown and per-group send lock.
- Missing audio fails without breaking an AI reply; sounds cannot play until assets are provisioned.
- Private/non-commercial use does not automatically grant permission to reproduce copyrighted TV-show recordings. Confirm permissions for the intended use.
