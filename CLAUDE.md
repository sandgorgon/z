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

- **`z.scala`** — Entry point (`SwingApplication`). Manages the `MainFrame`, loads/saves `~/.z/settings` (window geometry + tag line defaults), handles OS X fullscreen. Creates the top-level `ZPanel`. On startup, applies `tag.app`/`tag.col`/`tag.wnd`/`tag.cmd` from settings to the live defaults; on exit, reads the existing settings file before writing so user-set keys are not erased. The status bar is split into two labels: `statusLeft` (window state, updated by `ZStatusEvent` on mouse hover, cleared by `ZStatusClearEvent` on mouse exit) and `statusRight` (command echo, updated by `ZCmdEchoEvent` whenever any command runs at any level).
- **`ZPanel.scala`** — The application-level panel. Manages a `List[ZCol]` rendered as nested `SplitPane`s. Handles app-level commands (`NewCol`, `Help`, `Dump`, `Load`, `Fonts`, `RotateView`, `History`). Dispatches unknown commands down to all columns. `nextCol`/`prevCol` return `Option[ZCol]`. `var rotated` controls whether columns stack left-to-right (`Orientation.Vertical`, default) or top-to-bottom (`Orientation.Horizontal`); `RotateView` toggles it, refreshes, and propagates to all columns. New columns created via `NewCol` inherit the panel's `rotated` state. App-level rotation persisted in `~/.z/settings` as `view.rotated`. The `History` command opens/refreshes a `+History` scratch buffer in the last column with the full `CommandLog` contents. Holds a `var tagScheme: ZColorScheme` for the app tag line; `Color T*` applies to the app tag only, `ColorAll T*` applies and cascades to all columns and their windows. When a column is added via `+=`, its `colHandle.onDragRelease` is wired to dispatch `col.command("Lt"/"Rt")` based on the dominant drag axis — horizontal drag in normal layout, vertical drag in rotated layout (see **Drag Reordering**).
- **`ZCol.scala`** — A column panel. Manages a `List[ZWnd]` rendered as nested `SplitPane`s. Handles column-level commands (`New`, `Sort`, `CloseCol`, `Lt`/`Rt` for column moves, `RotateView`). Dispatches unknown commands to all its windows. The companion object holds `var colTagLine`, `var wndTagLine`, and `var cmdTagLine` — the mutable defaults that `z.scala` overwrites from `~/.z/settings` at startup. `var rotated` controls whether windows stack top-to-bottom (`Orientation.Horizontal`, default) or side-by-side (`Orientation.Vertical`); persisted via `Dump`/`Load`. Holds a `var tagScheme: ZColorScheme`; `Color T*` applies to the column tag only, `ColorAll T*` applies and cascades to all windows in the column. The column tag is wrapped in a `BorderPanel` row with `colHandle` (`ZDragHandle`) on the left; when a window is added via `+=`, its `tagHandle.onDragRelease` is wired to dispatch `w.command("Up"/"Dn"/"Lt"/"Rt")` based on drag direction — vertical drag reorders within the column, horizontal drag moves to an adjacent column (see **Drag Reordering**).
- **`ZWnd.scala`** — A single editor window (a `SplitPane` with tag on top, body below). Handles file I/O (`Get`/`Put`), external command execution (`< > | !`), interactive process I/O, color/font changes, and all window-level commands. Uses Thread-based callbacks with EDT marshaling (`SwingUtilities.invokeLater`) for async external command output streaming. `cmdProcess` and `cmdProcessWriter` are `Option[...]` types. `look(txt, fromTag)` accepts a `fromTag` flag (passed as `true` by tag-line mouse handlers) that tightens the path-prefix guard for tag clicks (see Window Path Segment Navigation). `command()` delegates to seven private handler methods by concern: `handleFileCmd`, `handleDisplayCmd`, `handleEditCmd`, `handleProcessCmd`, `handleLspCmd`, `handleColorCmd`, `handleScriptCmd` — each returns `Boolean` (handled/not). File I/O delegates to `ZFileIO`; path resolution delegates to `ZPathResolver`. The tag line is a bare `ZTextArea` (no `ScrollPane`) placed in a `BorderPanel` row alongside `tagHandle` (`ZDragHandle`) on the left; tag height auto-sizes to content via a `DocumentListener` and `ComponentListener` that call `peer.setDividerLocation(tagContentHeight())` whenever text or window size changes — starts at one line, grows as needed. Syntax highlighting commands: `Hilite` (auto-detect from file extension), `Hilite <lang>` (force a language name or extension alias), `Hilite off` (revert to plain text) — all delegate to `body.hilite(style)` via `ZLangRegistry`; `indHilite` flag is persisted in `Dump`/`Load` and re-applied on `Get`.
- **`ZTextArea.scala`** — Extended `swing.TextArea` with undo/redo (`UndoManager`), brace matching (Ctrl+B1), dirty tracking via `ZDirtyTextEvent`/`ZCleanTextEvent`, and helper methods for line/selection manipulation.
- **`ZUtilities.scala`** — Static utilities: text selection logic, brace/symbol matching (`symMatch`), external process launching (`extCmd`), shell command tokenization (handles single-quoted tokens), and shared split-pane divider helpers (`collectDividers`/`applyDividers`) used by both `ZPanel` and `ZCol`.
- **`ZFonts.scala`** — Registers bundled fonts (Hack, Bitstream Vera) from classpath resources.
- **`CommandLog.scala`** — Singleton command history ring buffer. `record(level, source, cmd)` appends a timestamped `(HH:mm:ss, level, source, cmd)` entry and returns the timestamp string. `render()` formats all entries as tab-aligned plain text for the `+History` window. `setLimit(n)` overrides the default cap of 500 entries; called at startup from `~/.z/settings` key `history.limit`. `clear()` resets the log (used by tests).
- **`ZColors.scala`** — Canonical colour constants shared across `ZWnd`, `ZPanel`, `ZCol`, `ZFuzzyPicker`, and `ZTheme`. Named by role, not by hue (`TagBack`, `TagFore`, `TagCaret`, `TagSelBack`, `BodyBack`, `BodySelBack`). Changing a constant here propagates everywhere. `handleFor(base: Color)` derives a contrasting handle color from any tag background: lightens dark tags (luminance < 0.5) by factor 1.4, darkens light tags by factor 0.75 — used by `ZDragHandle` so the grip is always visually distinct regardless of the active color scheme.
- **`ZDragHandle.scala`** — An 8px-wide `Panel` placed to the left of every tag line (in both `ZWnd` and `ZCol`). Has a MOVE cursor and a single callback `onDragRelease: (Int, Int) => Unit` receiving `(dx, dy)` screen-pixel deltas. Fires only when the drag exceeds a 20px threshold, preventing accidental triggers. Color is initialized and kept in sync with the tag's `handleFor(tagScheme.back)`. See **Drag Reordering** below.
- **`ZLangRegistry.scala`** — Maps file extensions and human-readable language names to RSTA `SyntaxConstants` style strings. `forPath(path)` looks up by extension; `forLang(lang)` looks up by name alias (case-insensitive). Also maps extensions to LSP language IDs via `langIdFor(path)`. Used by `ZWnd`'s `Hilite` command and `ZLspSupport`.
- **`ZPathResolver.scala`** — Pure path-resolution utilities factored out of `ZWnd` (no Swing dependencies). `resolvePath(p, root)` expands and canonicalizes a path. `isWndPathPrefix(rawPath, stxt, fromTag, tagText)` and `resolveBase(...)` implement the tag-line segment navigation guard (see Window Path Segment Navigation below).
- **`ZFileIO.scala`** — Pure file I/O factored out of `ZWnd.get()` and `ZWnd.put()` (no Swing dependencies). `readFile(path)`, `readDir(dirPath)`, and `writeFile(path, content)` each return `Either[String, T]` (Left = error message, Right = result).
- **`ZSettings.scala`** — Simple key=value flat-file persistence for app state (used by `Dump`/`Load` and `~/.z/settings` for window geometry and tag line defaults).
- **`ZPlumbing.scala`** — Plumbing rules engine. Loads `~/.z/plumbing`; rule file uses blank-line-separated blocks (Plan 9 style). `plumb(PlumbMessage)` returns `Option[PlumbResult]` for the first matching block. `PlumbMessage` carries `data`, `wdir`, `src`, `attrs`. `PlumbResult` carries `port` (`PlumbPortEdit` or `PlumbPortExec`), the mutated message, and `cmd` (for exec). Condition verbs: `data matches <regex>`, `arg isfile <tmpl>`, `arg isdir <tmpl>`, `wdir matches <regex>`, `src is <value>`, `type is <value>` (no-op). Action verbs: `data set <tmpl>`, `attr add <key>=<tmpl>`, `plumb to edit|exec`, `plumb start <cmd-tmpl>`. Template variables: `$0` = full data, `$1`..$n = capture groups, `$wdir`, `$arg`/`$file` = path resolved by isfile/isdir. Old single-line `match label /regex/ exec|look template` format accepted for backward compat. `ZWnd` handles `case "Plumb" => ZPlumbing.load()`. `PlumbPortEdit` results build the look target as `data + ":" + attrs("addr")`; `PlumbPortExec` results dispatch `ZPlumbExecEvent` handled by `ZCol` in a `+plumb` scratch window.
- **`ZFuzzyPicker.scala`** — Keyboard-driven fuzzy file picker invoked by Ctrl+P. `show(root, parent, initialQuery, relativeTo)` is the entry point. Walks the directory tree in a background thread (`CopyOnWriteArrayList` + `AtomicInteger`), skipping common build/cache dirs. Scores matches by consecutive runs, filename position, segment-start hits, and path length. Supports path-prefix queries (`/`, `~/`, `./`, `../`) that re-root the walk after a 150 ms debounce; the prefix is stripped from the query field after the root changes. `relativeTo` (passed as `baseDir` from `ZWnd`, derived from `ZWnd.root`) controls the relativization base for returned paths — ensures inserted paths resolve correctly under `look()`. When invoked from a `ZWnd`, the search `root` is `colRoot` (col's `currentDir` captured at window construction), not `ZWnd.root` — so the walk covers the full column scope regardless of how deep the open file is.

### Event Flow

Components communicate upward via Scala Swing's `publish`/`listenTo` reactor pattern:
- `ZWnd` publishes `ZCmdEvent`, `ZStatusEvent`, `ZStatusClearEvent`, `ZCmdEchoEvent`
- `ZCol` listens to its `ZWnd`s, handles or re-publishes events upward
- `ZPanel` listens to its `ZCol`s, handles or re-publishes to `z` (the app object)

**Status bar events:**
- `ZStatusEvent` — published by `ZWnd` on mouse enter; carries the full window properties map; updates `statusLeft` in `z.scala`.
- `ZStatusClearEvent` — published by `ZWnd` on mouse exit; clears `statusLeft` so it shows nothing when the mouse is not over any window.
- `ZCmdEchoEvent(timestamp, level, source, cmd)` — published by `ZWnd`, `ZCol`, and `ZPanel` after every command or look execution; bubbles up through the listenTo chain; updates `statusRight` in `z.scala` with `[HH:mm:ss] cmd`. Also triggers `CommandLog.record()` at each level so the full cascade is captured in the history.

### Command Dispatch

- **B3 click** on text: calls `look()` first. Inside `look()`: plumbing rules run first (via `ZPlumbing.plumb()`), then file-path resolution, then line/regex navigation, then search, finally falls back to `command()` if all look steps fail
- **B2 drag** on text: calls `command()` directly
- Each level's `command()` method handles its own commands and delegates unknown ones to child components
- The `%` prefix forces text to be treated as a command (bypasses look logic)
- External commands: `< cmd` (replace selection or insert stdout at caret), `> cmd` (send selection as stdin), `| cmd` (pipe selection or full body), `! cmd` (replace all content)

### Relative Path Navigation (B3 on tag or body)

When a window has a relative `rawPath`, any B3 navigation that resolves to a path under `root` will open the new window with a relative tag rather than an absolute one. The resolved absolute path (`ep`) is stripped of the `root` prefix before being passed to `lookUpward`. This applies uniformly — directory listings, body text references, and tag path segments all produce relative-tagged windows.

`ZPathResolver.isWndPathPrefix` is a narrower condition used only for **resolution base** (`ZPathResolver.resolveBase`): when clicking a segment of the tag line's own path (e.g. `main` in `src/main/Foo.scala`), resolution must use `root` rather than `baseDir` to avoid a double-prefix. Guards:
- `rawPath` is relative
- The extracted text is a prefix of `rawPath`
- **Tag only** (`fromTag=true`): the extracted text starts at position 0 of the tag line, ruling out command arguments that coincidentally share the same path prefix

### Scratch Buffers

Files with `+` in the path prefix (e.g., `+scratch`, `+Results`) are never saved to disk. They appear in `Dump` state but `Put` is a no-op on them.

### Keyboard Capture Mode

`ZTextArea` has a **capture mode** API: `startCapture()`, `endCapture(): String`, `abortCapture()`. `ZWnd` manages the state via `captureTA: Option[ZTextArea] = None` (presence means active) and exposes `cancelCapture()`.

- **Ctrl+Enter** — if text is selected: executes it as a command (no deletion). If capture is active: ends capture and executes. Otherwise: starts capture, recording the caret position. Everything typed from that point is highlighted via a Swing `Highlighter` (using `peer.getSelectionColor` so it follows the active theme). The `Highlighter` + `CaretListener` approach is synchronous (no `invokeLater`), no flicker.
- **Ctrl+F** — if text is selected: performs a look on it (no deletion). If capture is active: ends capture and performs a look. No-op outside capture with no selection.
- **Escape** — cancels capture (no execution, text stays).
- Body capture text is deleted after execution (`replaceSelection("")`); tag capture text stays and remains selected.
- `endCapture()` converts the Highlighter region to a real Swing selection before returning, so `replaceSelection("")` and natural selection persistence both work correctly.
- **Ctrl+P** opens the file-chooser path picker in `ZWnd`, `ZCol`, and `ZPanel`.
- **`< cmd` in capture mode**: `externalCmd` accepts `insertPos: Option[Int]`; for `<`, this is always `Some(body.peer.getCaretPosition)` captured synchronously at call time, and output is inserted via `peer.getDocument.insertString(pos, s, null)` using an `AtomicInteger` to advance the offset across multiple `invokeLater` callbacks. Scroll mode does not affect placement.
- **`| cmd` in capture mode**: after `endCapture()` + `replaceSelection("")` clears the body selection, `body.peer.selectAll()` is called so that `|` receives the full body content as stdin rather than an empty string.

### Drag Reordering

Every tag line has an 8px `ZDragHandle` grip on its left edge (MOVE cursor). B1-press and drag at least 20px to trigger a reorder on release:

- **Window handle** (in `ZWnd` tag): vertical drag → `Up`/`Dn` within the column; horizontal drag → `Lt`/`Rt` to move to an adjacent column. These dispatch via `w.command(...)` which publishes `ZCmdEvent`, handled by `ZCol`.
- **Column handle** (in `ZCol` tag): the dominant axis determines direction — `Lt`/`Rt` via `col.command(...)` which publishes `ZMoveColEvent`, handled by `ZPanel`. Both horizontal (normal layout) and vertical (rotated layout) drags are supported.

The handle color is derived from `ZColors.handleFor(tagScheme.back)` and kept in sync whenever the tag color scheme changes.

### Syntax Highlighting

`ZWnd` supports RSTA-based syntax highlighting via `ZLangRegistry`:

- **`Hilite`** — auto-detects language from the file's extension and applies the matching RSTA style.
- **`Hilite <lang>`** — forces a specific language (accepts extensions like `scala` or aliases like `python`, `shell`, `postgres`).
- **`Hilite off`** — reverts to plain text.

The `indHilite` flag is persisted in `Dump`/`Load` and re-applied automatically on `Get` when the file path changes. Adding new languages means adding entries to `ZLangRegistry.byExt` and/or `byName`.

### Session Persistence

`Dump [fname]` serializes the full editor state (all columns, windows, content of dirty scratch buffers, fonts, colors) to a flat key=value file via `ZSettings`. `Load [fname]` restores it.

`~/.z/settings` is a separate, always-on persistence file with two concerns:
- **Window geometry** — `app.width` / `app.height` are written on every clean exit and read at next launch.
- **Tag line defaults** — `tag.app`, `tag.col`, `tag.wnd`, `tag.cmd` are read at startup and applied to the live tag-line variables in `ZCol` (and `mainPanel.tag.text` for `tag.app`). The file is never fully overwritten on exit; the existing key set is loaded first so user-defined keys survive across sessions.
- **Layout rotation** — `view.rotated` is written on exit and read at startup to restore the app-level `RotateView` state.
- **Command history limit** — `history.limit` is read at startup and passed to `CommandLog.setLimit(n)`. Never auto-written; user sets it manually. Default is 500 if absent or non-numeric.
