---
id: kb-002
title: Set up VPN access on a company laptop
tags: vpn, remote, network, access
author: it-helpdesk@example.com
created: 2026-01-16T11:40:00Z
---

Remote access to internal systems goes through the corporate VPN. The client is
pre-installed on every managed laptop.

## First connection

1. Open **Company VPN** from the start menu (Windows) or Applications (macOS).
2. Server address: `vpn.example.com`. It is filled in by default.
3. Sign in with your domain account and approve the MFA push notification.
4. The tray icon turns green when the tunnel is up.

## Split tunnelling

Only traffic to internal ranges (`10.0.0.0/8` and `172.16.0.0/12`) goes through the
tunnel. Video calls and general web browsing stay on your local connection, so do not
be surprised that the VPN does not change your public IP address.

## Troubleshooting

- **Error 691** - wrong password, see kb-001.
- **Connects and drops after 10 seconds** - your device certificate expired. Restart the
  laptop so the management agent re-enrolls it, then try again.
- **Cannot reach a specific server** - the route may be missing. Raise a ticket with the
  target hostname and port.
