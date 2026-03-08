z
=

[Plan 9](http://plan9.bell-labs.com/plan9/) Acme Inspired Editor, done in Scala.

![Screenshot](/img/screenshot.jpg "Title")

Built with Scala 3.8.2, sbt 1.10.7, and Java 11+.

To build it, go to the root of the source and then,

        sbt assembly

... and you can just copy the jar that was created in the (literal) target directory and put it anywhere you want. For example for me, I put it in a $HOME/z directory.

How to use it is documented in the [Z Help Screen](https://github.com/sandgorgon/z/tree/master/src/main/resources/help/main.txt).

The wiki has some guides on usage.

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
