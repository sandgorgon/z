# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**z** is a Plan 9 Acme-inspired text editor written in Scala 3 using Scala Swing. It produces a self-contained fat JAR via sbt-assembly.

## Build & Run Commands

```bash
# Build the fat JAR (output: target/scala-3.8.2/z.jar)
sbt assembly

# Deploy to the installed location
cp target/scala-3.8.2/z.jar ~/.local/lib/z/z.jar

# Run tests
sbt test

# Run a single test
sbt "testOnly HelloSpec"

# Run the editor from source (requires Java)
sbt run

# Run the built JAR directly
java -jar target/scala-3.8.2/z.jar [file|dir|options...]
```

## Architecture

The UI hierarchy is: **ZPanel** (app) → **ZCol** (columns) → **ZWnd** (windows/panes).

Each level has a **tag line** (top, `ZTextArea`) and a **body** (bottom, `ZTextArea` in a `ScrollPane`). Commands are executed by right-clicking (B3) or middle-click-drag (B2) on text in tag lines or body content.

### Key Classes

- **`z.scala`** — Entry point (`SwingApplication`). Manages the `MainFrame`, loads/saves window geometry from `~/.z`, handles OS X fullscreen. Creates the top-level `ZPanel`.
- **`ZPanel.scala`** — The application-level panel. Manages a `List[ZCol]` rendered as nested `SplitPane`s. Handles app-level commands (`NewCol`, `Help`, `Dump`, `Load`, `Fonts`). Dispatches unknown commands down to all columns. `nextCol`/`prevCol` return `Option[ZCol]`.
- **`ZCol.scala`** — A column panel. Manages a `List[ZWnd]` rendered as nested horizontal `SplitPane`s. Handles column-level commands (`New`, `Sort`, `CloseCol`, `Lt`/`Rt` for column moves). Dispatches unknown commands to all its windows.
- **`ZWnd.scala`** — A single editor window (a `SplitPane` with tag on top, body below). Handles file I/O (`Get`/`Put`), external command execution (`< > | !`), interactive process I/O, color/font changes, and all window-level commands. Uses Thread-based callbacks with EDT marshaling (`SwingUtilities.invokeLater`) for async external command output streaming. `cmdProcess` and `cmdProcessWriter` are `Option[...]` types.
- **`ZTextArea.scala`** — Extended `swing.TextArea` with undo/redo (`UndoManager`), brace matching (Ctrl+B1), dirty tracking via `ZDirtyTextEvent`/`ZCleanTextEvent`, and helper methods for line/selection manipulation.
- **`ZUtilities.scala`** — Static utilities: text selection logic, brace/symbol matching (`symMatch`), external process launching (`extCmd`), and shell command tokenization (handles single-quoted tokens).
- **`ZFonts.scala`** — Registers bundled fonts (Hack, Bitstream Vera) from classpath resources.
- **`ZSettings.scala`** — Simple key=value flat-file persistence for app state (used by `Dump`/`Load` and `~/.z` for window size).

### Event Flow

Components communicate upward via Scala Swing's `publish`/`listenTo` reactor pattern:
- `ZWnd` publishes `ZCmdEvent`, `ZLookEvent`, `ZStatusEvent`
- `ZCol` listens to its `ZWnd`s, handles or re-publishes events upward
- `ZPanel` listens to its `ZCol`s, handles or re-publishes to `z` (the app object)

### Command Dispatch

- **B3 click** on text: calls `look()` first (file/path/regex navigation), falls back to `command()` if look fails
- **B2 drag** on text: calls `command()` directly
- Each level's `command()` method handles its own commands and delegates unknown ones to child components
- The `%` prefix forces text to be treated as a command (bypasses look logic)
- External commands: `< cmd` (replace/insert stdout), `> cmd` (send selection as stdin), `| cmd` (pipe selection), `! cmd` (replace all content)

### Scratch Buffers

Files with `+` in the path prefix (e.g., `+scratch`, `+Results`) are never saved to disk. They appear in `Dump` state but `Put` is a no-op on them.

### Session Persistence

`Dump [fname]` serializes the full editor state (all columns, windows, content of dirty scratch buffers, fonts, colors) to a flat key=value file via `ZSettings`. `Load [fname]` restores it. App window size is auto-saved to `~/.z` on close.
