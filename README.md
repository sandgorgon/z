z
=

[Plan 9](http://plan9.bell-labs.com/plan9/) Acme Inspired Editor, done in Scala.

![Screenshot](/img/screenshot.jpg "Title")

Built with Scala 3.8.2, sbt 1.10.7, and Java 11+.

To build and install:

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

How to use it is documented in the [Z Help Screen](https://github.com/sandgorgon/z/tree/master/src/main/resources/help/main.txt).

The wiki has some guides on usage.

## Installation

Pre-built packages for Linux, macOS, and Windows are available on the [Releases](https://github.com/sandgorgon/z/releases) page.

| Platform | Package | Notes |
|---|---|---|
| Linux | `.deb` or ZIP | ZIP includes `install.sh` |
| macOS | `.dmg` or ZIP | Unsigned — right-click → Open to bypass Gatekeeper |
| Windows | `.msi` or ZIP | Unsigned — choose "More info → Run anyway" in SmartScreen |

All native packages (`.deb`, `.dmg`, `.msi`) bundle a JRE — no separate Java installation required.
The ZIP packages require Java 11+ and include an install script.

## Features

- **Acme-style mouse chording** — B3 (right-click) to look/navigate/execute, B2-drag to execute selected text
- **Syntax highlighting** — powered by RSyntaxTextArea; use `Hilite` to enable, `Hilite [lang]` for a specific language, `Theme [name]` to switch colour themes
- **Language Server Protocol (LSP)** — use `Lsp` to start a language server for the current file:
  - Squiggly underlines for errors and warnings
  - Hover tooltips with type information and documentation
  - Code completion popup via `Complete` or **Ctrl+Space**
  - Diagnostics listed in a `+Diagnostics` scratch window
  - Configure servers in `~/.zlsp` (one `langId = command` per line, e.g. `go = gopls`)
- **Session persistence** — `Dump` / `Load` save and restore the full editor layout
- **Scratch buffers** — filenames containing `+` are never written to disk
