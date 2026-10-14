# MSCC Community Cloud (MEGA phase)

This is an **opt-in private control feature**. No donor password, account transfer,
or automatic pooling of separate free accounts is required.

## What works

* `.cloud status` — MEGA's reported used/total for MSCC's **own account** only.
* `.cloud folders` — show configured, mounted contributor locations.
* `.cloud discover` — list accessible mount IDs in the private control inbox.
* `.cloud list [id]` — list recent files in a selected folder.
* `.cloud upload [id]` — reply to WhatsApp media and stream it into the
  selected allowlisted folder (maximum 512 MiB, verified size required).
* Cortex's authenticated GET `/api/cloud` and its loopback GET `/cloud` provide
  the same dashboard data as JSON. Neither endpoint exposes credentials.

Uploaded content stays private by default: **no public MEGA link is generated**.
There is no background auto-archive or migration of sessions/database.

## Activate

1. Set `MSCC_MEGA_EMAIL` / `MSCC_MEGA_PASSWORD` in Azure's protected
   `/etc/mscc.env` (or Cortex's secure environment editor), **never in GitHub**.
2. Set `MSCC_MEGA_FOLDER=MSCC Uploads` optionally.
3. Install the project's dependencies and redeploy MSCC normally.
4. In the bot's private owner/control DM, run `.cloud status`, then reply to an
   image with `.cloud upload`.

## Contributors — shared folder invitations

Each supporter should keep their own login and recovery key.

1. On the donor's MEGA account, create a dedicated folder and invite the
   MSCC MEGA account by its email with permissions for uploads.
2. Accept the invitation on the MSCC MEGA account.
3. Run `.cloud discover` privately to see which incoming folders MEGAJS can
   access and each exact node ID. If the folder does not appear, do **not**
   assume it is writable from the bot.
4. Configure an explicit allowlist in the **secure** server environment:
   `MSCC_MEGA_SHARED_FOLDERS=\'[{"id":"donor1","label":"Supporter One","nodeId":"abcdefgh","uploadEnabled":true}]\'`
   where `nodeId` is the actual value from `.cloud discover`.
5. Verify `.cloud folders` shows the target as connected. Make a small
   test upload with `.cloud upload donor1`. Confirm on the donor's MEGA
   account that the file is there and which quota changed.

Only the exact selected mount can receive contributor uploads. Nonlisted
folders and entries without `uploadEnabled:true` are never uploaded to.
`uploadEnabled` is operator approval, not a bypass of MEGA permissions.

## Capacity and limits

The dashboard displays *real* quota for the authenticated primary account;
it intentionally does not add hypothetical 20 GB donations to the displayed
total. MEGAJS does not provide each contributor's remaining account quota via
a recipient's folder share. MEGA also enforces transfer and service limits.

The initial shared-folder integration is tested with a mock client; actual
MEGA incoming-share upload support, permissions, and quota attribution need
a small live verification before relying on it for important data.

The system does not automatically switch between donor folders when the
primary is full. All uploads currently select the primary folder unless the
owner explicitly names an allowlisted contributor target.

Keep critical WhatsApp sessions and the SQLite database on persistent Azure
storage with separate tested backups. Never treat community folders as the
only backup: donors can revoke access, and service limits can change.
