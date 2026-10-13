# Third-party material

The MIT license in [LICENSE](LICENSE) covers Omalauncher's own code and artwork.
It does not cover the files below, which stay under their owners' terms.

## Coding agent logos

`art/icons/agent-*.svg`, and the PNGs rendered from them in
`app/src/main/assets/icons/agent-*.png` and `app/src/main/res/drawable-nodpi/agent_*.png`.

The marks are trademarks of their owners. They are used only to identify each
agent in the launcher, which is not affiliated with or endorsed by any of them.

| File | Mark | Owner | Source of the drawing |
|---|---|---|---|
| `agent-claude` | Claude | Anthropic | [simple-icons](https://github.com/simple-icons/simple-icons) (CC0-1.0) |
| `agent-codex` | OpenAI | OpenAI | simple-icons (CC0-1.0) |
| `agent-copilot` | GitHub Copilot | GitHub | simple-icons (CC0-1.0) |
| `agent-cursor-agent` | Cursor | Anysphere | simple-icons (CC0-1.0) |
| `agent-agy` | Antigravity | Google | [@lobehub/icons](https://github.com/lobehub/lobe-icons) (MIT) |
| `agent-grok` | Grok | xAI | @lobehub/icons (MIT) |
| `agent-opencode` | opencode | opencode | @lobehub/icons (MIT) |
| `agent-pi` | Pi | pi.dev | drawn for Omalauncher, a π in the colors of Pi's mark |
| `agent-crush` | Crush | Charm | redrawn from Charm's logo |

## Fonts

`app/src/main/res/font/nerd_symbols.ttf`: symbols from
[Nerd Fonts](https://www.nerdfonts.com) (MIT), [Weather Icons](https://erikflowers.github.io/weather-icons/)
(SIL OFL 1.1) and [Material Design Icons](https://pictogrammers.com/library/mdi/) (Apache-2.0).

## Omarchy themes

`app/src/main/assets/themes/*.toml` and `app/src/main/assets/theme-backgrounds.txt`
come from [Omarchy](https://github.com/omacom/omarchy), MIT, Copyright (c) David Heinemeier Hansson.
Omalauncher is not affiliated with Omarchy.

## Omarchy screensaver

The native text effects renderer in `ttfx-android/` uses
[ttfx](https://github.com/omacom/ttfx) (MIT, Copyright (c) omacom), pinned
to `921bd551c308c235e01c5867f0199efe0691c271`. Its effects are based on
[TerminalTextEffects](https://github.com/ChrisBuilds/TerminalTextEffects)
(MIT, Copyright (c) ChrisBuilds), as credited in ttfx's NOTICE.
`ttfx-android/src/main/assets/ttfx/omarchy-logo.txt` is from
[Omarchy](https://github.com/omacom/omarchy), MIT, Copyright (c) David Heinemeier Hansson.
The Android build also links the crates in
`ttfx-android/rust/Cargo.lock` (including clap, jni, terminal_size, rustix, libc,
and their support crates), which are distributed under their declared MIT and
Apache-2.0 licenses.

## Bundled at build time

`scripts/fetch-proot.sh` downloads pinned versions of Termux's builds of PRoot (GPL-2.0),
talloc (LGPL-3.0) and libandroid-shmem (BSD-3-Clause); they are not in this
repository. The app lists every component it ships, with its license, under
settings › licenses (`ui/LicensesScreen.kt`, texts in `app/src/main/assets/licenses/`).
