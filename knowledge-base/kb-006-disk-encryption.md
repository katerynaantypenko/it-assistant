---
id: kb-006
title: Laptop disk encryption (BitLocker and FileVault)
tags: encryption, bitlocker, filevault, security, laptop
author: it-security@example.com
created: 2026-02-24T13:15:00Z
---

Every company laptop must have full disk encryption enabled. The management agent turns
it on during enrollment and stores the recovery key centrally - you never need to write
it down yourself.

## Check the status

- **Windows** - Settings -> Privacy & security -> Device encryption. Expect *On*.
- **macOS** - System Settings -> Privacy & Security -> FileVault. Expect *FileVault is on*.

## Recovery key

If Windows asks for a BitLocker recovery key at boot, open
https://sso.example.com/devices from your phone, pick the device and read the key.
The prompt normally appears after a firmware update or a change to the boot order.

For macOS the recovery key is escrowed the same way, but a forgotten *login* password
also needs the FileVault password - see kb-001.

## External drives

USB drives are read-only unless they are encrypted. Encrypt them from the Company Portal
tool so that the recovery key is escrowed as well; drives encrypted with a personal key
stay read-only.
