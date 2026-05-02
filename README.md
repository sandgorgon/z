# z

A [Plan 9 Acme](https://9p.io/plan9/)-inspired text editor written in Scala. It follows the Acme mouse-chording model: right-click executes or navigates, middle-click-drag selects and runs — keeping your hands on the mouse and out of modal menus. There is no command palette, no sidebar, and no plugin system; just text, tag lines, and commands.

![z editor showing multiple columns and windows with syntax highlighting](/img/screenshot.jpg)

Built with Scala 3.8.2, sbt 1.10.7, and Java 17+.

## Installation

Pre-built packages for Linux, macOS, and Windows are available on the [Releases](https://github.com/sandgorgon/z/releases) page.

| Platform | Package | Notes |
|---|---|---|
| Linux | `z-editor_X.X.X_amd64.deb` or ZIP | ZIP includes `install.sh` |
| macOS | `z-X.X.X.dmg` or ZIP | Unsigned — right-click → Open to bypass Gatekeeper |
| Windows | `z-X.X.X.msi` or ZIP | Unsigned — choose "More info → Run anyway" in SmartScreen |

All native packages (`.deb`, `.dmg`, `.msi`) bundle a JRE — no separate Java installation required.
The ZIP packages require Java 17+ and include an install script.

## Build from Source

```sh
sbt assembly
mkdir -p ~/.local/lib/z
cp target/scala-3.8.2/z.jar ~/.local/lib/z/z.jar
```

Then create a launcher script at `~/bin/z` (or anywhere on your `$PATH`):

```sh
#!/bin/sh
exec java \
    -Dawt.useSystemAAFontSettings=on \
    -Dswing.aatext=true \
    -jar "${HOME}/.local/lib/z/z.jar" \
    "$@"
```

Make it executable:

```sh
chmod +x ~/bin/z
```

To run the tests:

```sh
sbt test
```

## Features

- **Acme-style mouse chording** — B3 (right-click) to look/navigate/execute, B2-drag to execute selected text; **keyboard capture mode** (Ctrl+Enter) lets you type a command with the keyboard, see it highlighted as you go, then execute as a command (Ctrl+Enter) or look/navigate (Ctrl+F)
- **Syntax highlighting** — powered by RSyntaxTextArea; use `Hilite` to enable, `Hilite [lang]` for a specific language, `Theme [name]` to switch colour themes
- **Language Server Protocol (LSP)** — use `Lsp` to start a language server for the current file:
  - Squiggly underlines for errors and warnings
  - Hover tooltips with type information and documentation
  - Code completion popup via `Complete` or **Ctrl+Space**
  - Diagnostics listed in a `+Diagnostics` scratch window
  - Configure servers in `~/.z/lsp.conf` (one `langId = command` per line):
    ```
    go     = gopls
    python = pylsp
    scala  = metals
    ```
- **Session persistence** — `Dump` / `Load` save and restore the full editor layout
- **Configurable tag line defaults** — set `tag.app`, `tag.col`, `tag.wnd`, and `tag.cmd` in `~/.z/settings` to customise the commands pre-loaded into each tag line at startup
- **Property inspection** — `Props` opens a `+Props` scratch window listing the current properties of the app, column, or window (depending on where it is run from)
- **Scratch buffers** — filenames containing `+` are never written to disk (e.g. `+scratch`, `+Results`)
- **Rotatable layout** — `RotateView` toggles window stacking within a column between top-to-bottom and side-by-side; from the app tag line it also rotates the column layout itself. Rotation state is persisted across sessions
- **Batch commands** — `X 'regexp' cmd` / `Y 'regexp' cmd` run a command across matching/non-matching windows
- **Interactive processes** — `Input` mode lets you send text line-by-line to a running process
- **Brace matching** — Ctrl+B1 selects the region between matching `{}` `[]` `()` `<>` or any delimiter pair
- **Line numbers** — toggle with `Ln`
- **Path expansion** — tag lines and commands support `~`, `./`, and `../` prefixes
- **Relative path style preservation** — when a window has a relative path, B3 navigation (directory listings, body text, tag path segments) opens new windows with relative tags rather than absolute paths, keeping the project-relative style consistent
- **Root directory control** — `Dir <path>` changes where relative paths resolve, where external commands run, and the LSP workspace root; works from window, column, or app tag line
- **User scripts** — place executable scripts in `.z/scripts/` (project-local) or `~/.z/scripts/` (global) and invoke them from any tag line with a comma prefix: `,Build`, `,Format`, `,Deploy`. Use `,,cmd` to run on every open window. Additional directories can be configured in `~/.z/scripts.conf`
- **Plumbing rules** — user-configurable dispatch rules in `~/.z/plumbing` that intercept B3 actions before normal look/execute logic. Each rule matches a regex and either runs a shell command (`exec`) or re-runs navigation on a transformed string (`look`). Built-in rules open URLs in the system browser and jump to `file:line:col` compiler output. Reload rules at any time with `Plumb`
- **Fuzzy file picker** (Ctrl+P) — keyboard-driven file search that replaces the system file dialog. Type to fuzzy-filter files under the current root; prefix with `/`, `~/`, `./`, or `../` to re-root the search interactively. Results are scored by consecutive runs, filename matches, and segment-start matches; the chosen path is inserted relative to the current file's directory so B3 navigation resolves it correctly
- **Font control** — `Font`/`FONT` set the variable/fixed-width body font; `TagFont` sets the tag line font. All three cascade from the app tag line (sets editor-wide default), column tag line (that column), or window tag line (that window only)
- **Keyboard shortcuts** for navigation, selection, editing, and tools — see the help screen for the full list

## Documentation

- [Tutorial](TUTORIAL.md) — start here if you are new to z
- [Command Reference](https://github.com/sandgorgon/z/blob/master/src/main/resources/help/main.txt) — full command listing
