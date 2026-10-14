# MSCC Community Cloud — media-type automatic routing

**Private owner-only archive.** The bot chooses the destination; nobody has
to type contributor IDs or pick cloud accounts. The donor keeps ownership of
their MEGA account and only shares a dedicated folder with MSCC.

## Commands

- `.cloud status` — primary account quota and shared-folder connection counts.
- `.cloud folders` — readable routing roles and status (Images, Videos, etc.).
- `.cloud upload` — reply to any supported WhatsApp media; bot auto-routes it.
- `.cloud list` — newest files across the primary account and connected shares.
- `.cloud discover` — private owner setup utility; lists mounted folder node IDs.

The backend supports authenticated `GET /api/cloud` and loopback `GET /cloud`
for a Cortex Storage dashboard (UI still to be built). No passwords appear
in API responses or in files committed to GitHub.

## Storage rules

Supported roles: `images`, `videos`, `audio`, `documents`, `archives`,
`stickers`. A folder may cover several roles; `all` matches any role.

For five MEGA accounts total, use **three for videos**, one for images
and stickers, and one primary account for music, books, documents and archives.
That is up to ~60 GB of nominal video capacity if each video account really
has 20 GB free. The five accounts remain separate; this is not one MEGA quota.
Each video account shares a dedicated folder with the primary account.

When three writable video folders are available, MSCC automatically chooses
the one holding the fewest **visible bytes**, not the fewest files. One giant
movie weighs more than hundreds of tiny clips. Visible bytes in the shared
folder are **not** proof of donor account capacity or remaining quota.

If a category is *unassigned*, MSCC uses its own primary MEGA account.
If a category is assigned but the matching folder is missing or not approved
for writes, the upload is rejected explicitly rather than secretly going to
an unrelated storage account. Errors after an upload starts do not trigger
an automatic retry elsewhere, avoiding possible duplicate uploads.

Files must have known nonzero sizes and be within a 2 GiB per-upload
limit. Media is streamed directly from WhatsApp to MEGA: large movies do not
need to be buffered onto the small Azure VM disk. Transfer/network quotas
and the underlying WhatsApp media limit still apply. Actual available capacity for *contributor-owned* accounts cannot be
reliably established from incoming folder shares, so the dashboard shows
verified quota for the primary account only. Do not display hypothetical
"100 GB available" as if that were confirmed.

## Initial connection

1. Put `MSCC_MEGA_EMAIL` and `MSCC_MEGA_PASSWORD` in Azure's protected
   environment, never WhatsApp or GitHub. Optional
   `MSCC_MEGA_FOLDER=MSCC Uploads`.
2. Redeploy MSCC with installed dependencies.
3. In your private bot control chat run `.cloud status` and reply to a
   small test image with `.cloud upload`. Check the actual MEGA account.

## Contributor onboarding

1. Each contributor keeps their MEGA credentials and recovery key.
2. They create a *dedicated* folder in MEGA and send MSCC's primary MEGA
   account a folder invitation with permission to add files.
3. Accept the invite on the primary MEGA account. Run `.cloud discover`
   privately and obtain the actual exact folder node ID. If it does not
   appear, do not mark it as connected.
4. Configure the opt-in role mapping in the **protected server environment**.
   Example (replace every node ID with the value from `.cloud discover`):

   ```dotenv
   MSCC_MEGA_SHARED_FOLDERS='[{"id":"video_a","label":"Video A","nodeId":"videoAAA","mediaTypes":["videos"],"uploadEnabled":true},{"id":"video_b","label":"Video B","nodeId":"videoBBB","mediaTypes":["videos"],"uploadEnabled":true},{"id":"video_c","label":"Video C","nodeId":"videoCCC","mediaTypes":["videos"],"uploadEnabled":true},{"id":"photos","label":"Images & Stickers","nodeId":"photoAAA","mediaTypes":["images","stickers"],"uploadEnabled":true}]'
   ```

   Account 5 is the primary MSCC MEGA account for audio, books, documents and archives.\n   The internal IDs above are only for secure configuration. Users never
   supply them. `uploadEnabled:true` is an additional operator-approved
   check and does not override MEGA's own permission checks.
5. Run `.cloud folders` to confirm the role mapping. Test each category
   with a small upload and confirm it arrived and is readable in the
   *contributor's* MEGA account. Confirm quota there too.

## Safety and known limitations

- The MEGAJS contributor-mount behavior has mock regression coverage but
  has **not** been verified with your real accounts yet. In particular,
  validate upload encryption/decryption and write permissions on each share.
- Existing Azure SQLite, active WhatsApp sessions, and authentication
  backups remain on the server. This archive must not be their only backup.
- Donors can revoke access, quotas can run out, and MEGA enforces transfer
  and service limits. Automatic cross-account out-of-space failover is **not**
  supported until live correctness and quotas are verifiable.
- No unsolicited automatic archiving of group messages, and no public MEGA
  sharing links are generated.
