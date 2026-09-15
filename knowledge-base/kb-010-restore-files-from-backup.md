---
id: kb-010
title: Restore files from backup
tags: backup, restore, files, onedrive, recovery
author: it-helpdesk@example.com
created: 2026-03-12T12:45:00Z
---

Only files in your synced folders and on network shares are backed up. Anything stored
outside them - for example `C:\temp` or a local Downloads folder - is not.

## Restore it yourself

- **Synced folders (Desktop, Documents, Pictures)** - right-click the file in the web
  portal and choose *Version history*. Deleted files stay in the recycle bin for 93 days.
- **Network shares** - right-click the folder in Explorer, choose *Properties -> Previous
  Versions*, pick a snapshot. Snapshots are taken hourly and kept for 30 days.

## When you need the helpdesk

Open a ticket with the full path, the file name and the date and time you want restored.
Restores from tape (older than 30 days, kept for 7 years) take up to two working days.

## A note about ransomware

If many files suddenly have a strange extension, stop and treat it as a security
incident - see kb-008. Do not try to restore first: a restore into an infected device
loses the clean copy as well.
