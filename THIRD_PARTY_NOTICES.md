# Third-party notices

## Termux Terminal View and Terminal Emulator

MS Terminal alpha uses the separately published Termux terminal libraries:

- Maven component: `com.github.termux.termux-app:terminal-view:v0.118.0`
- Upstream source: https://github.com/termux/termux-app
- Source directories: `terminal-view/` and `terminal-emulator/`
- Upstream release tag: `v0.118.0`

The upstream `LICENSE.md` states that the `termux/termux-app` repository is GPL-3.0-only, with an explicit exception for the Terminal Emulator for Android code used by the `terminal-view` and `terminal-emulator` libraries, which is under the Apache License, Version 2.0. This app uses those separately published library components only; it does not incorporate the Termux app UI, `termux-shared`, or the Termux package/bootstrap system.

The upstream copyright and license notices remain applicable. The upstream license statement can be reviewed at:
https://github.com/termux/termux-app/blob/v0.118.0/LICENSE.md

Apache License, Version 2.0:
https://www.apache.org/licenses/LICENSE-2.0

## Components not included in this alpha

PRoot, Debian root filesystem images, package repositories, and other Linux environment binaries are not included in this alpha release. They must receive their own version pinning, source verification, license review, and notices before being added.

This notice is a component inventory, not a claim that every future MS Terminal dependency has already been reviewed.
