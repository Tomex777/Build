# Torri Help

This page covers the product-help links used by Torri 1.0.0. Torri keeps the mature manga/library/reader behavior of its open-source foundation while using Torri-specific product and support surfaces.

## Troubleshooting

If a source, chapter, download, or page stops loading:

1. Retry the action once and confirm the device has a working connection.
2. Open the source again and check whether its catalog still loads.
3. If the source uses a website, open its WebView and complete any login or verification it requires.
4. For downloaded content, confirm the selected storage location is still available to Android and that Torri still has access to it.
5. If the problem persists, capture the exact error and the relevant Torri logs before reporting it in the `Tomex777/Build` issue tracker.

Avoid clearing app data as a first troubleshooting step. Back up your library/settings before destructive recovery actions.

## Cloudflare

Some source websites use Cloudflare or similar browser verification. When Torri asks you to solve a verification challenge, use the in-app WebView, complete the challenge, and return to the source.

If verification loops:

- confirm Android System WebView is installed and current;
- make sure cookies are allowed for the source session;
- temporarily disable network tools that alter requests if they are interfering;
- reopen the source WebView and complete verification again.

Torri cannot bypass a site's access rules when the site refuses the session.

## Local sources

Torri can read manga from the Local source using the storage location exposed by the app. Keep each title in its own folder and keep chapter content grouped consistently so the Local source can identify it.

If local content does not appear, verify that the selected Torri storage location still exists, Android has not revoked access, and the files are in a format supported by the mature Local source implementation. After correcting the files, reopen or refresh the Local source.

## Source migration

Use migration when the same title needs to move from one source to another. Review the destination match before confirming: similarly named titles can have different editions or chapter numbering.

Migration can preserve library organization and reading state where supported, but it does not make two unrelated source catalogs identical. Check the destination chapters after migration before deleting old downloads.

## Tracking

Tracking integrations are optional and may require signing in to the external service. Configure them from Torri's tracking settings, authorize the service, then attach a tracker to a title from its details screen.

If synchronization fails, re-check the service login and network connection. External tracking services can also have outages or API changes outside Torri's control.

## Storage and backups

Keep regular Torri backups somewhere other than the app's working directory. Backups can contain private library/settings data, so store and share them carefully.

If Android revokes access to a folder or removable storage disappears, reselect an available storage location before downloading or restoring. Do not rename or move large managed storage trees while Torri is actively downloading.

Before reinstalling, clearing app data, or moving to a new device, create and verify a recent backup.

## Large library updates

Large manual updates and bulk downloads can create substantial traffic against source websites and may trigger rate limits. Torri can warn before unusually large operations for that reason.

Prefer smaller update/download batches when a source is slow or rate-limited. If a source starts rejecting requests, stop the bulk operation and try again later rather than repeatedly retrying every title.

## Reporting a Torri issue

Use the `Tomex777/Build` GitHub issue tracker for Torri-specific defects. Include the Torri version, Android version, the action that failed, the exact error, and logs/screenshots when relevant. Do not include passwords, private tokens, or other secrets.
