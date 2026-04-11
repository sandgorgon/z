# The z Editor Tutorial

> *"The purpose of a tool is to disappear."*

Most editors hand you a cockpit: toolbars, sidebars, palettes, panes within panes, settings buried six dialogs deep. z takes the opposite bet. It gives you text, a tag line, and three mouse buttons — then gets out of the way.

This tutorial will take you from zero to genuinely productive. By the end you will understand not just *what* z does but *why* it works the way it does, and that understanding will make everything click together naturally.

---

## Table of Contents

1. [Getting Started](#1-getting-started)
2. [The Three Mouse Buttons](#2-the-three-mouse-buttons)
3. [Opening, Navigating & Closing Files](#3-opening-navigating--closing-files)
4. [Editing Essentials](#4-editing-essentials)
5. [Running External Commands](#5-running-external-commands)
6. [Working with Multiple Windows & Columns](#6-working-with-multiple-windows--columns) — including `RotateView`
7. [Scratch Buffers](#7-scratch-buffers)
8. [Appearance & Fonts](#8-appearance--fonts)
9. [Syntax Highlighting & Themes](#9-syntax-highlighting--themes)
10. [Language Server Protocol (LSP)](#10-language-server-protocol-lsp)
11. [Session Management](#11-session-management)
12. [Command-Line Flags (Advanced)](#12-command-line-flags-advanced)
13. [User Scripts](#13-user-scripts)
14. [Putting It All Together](#14-putting-it-all-together)
- [Quick Reference](#quick-reference)

---

## 1. Getting Started

### Installation

Pre-built packages for Linux, macOS, and Windows are available on the [Releases](https://github.com/sandgorgon/z/releases) page. Native packages (`.deb`, `.dmg`, `.msi`) bundle their own JRE — nothing else to install.

If you prefer to build from source:

```sh
sbt assembly
mkdir -p ~/.local/lib/z
cp target/scala-3.8.2/z.jar ~/.local/lib/z/z.jar
```

Then create a launcher at `~/bin/z`:

```sh
#!/bin/sh
exec java \
    -Dawt.useSystemAAFontSettings=on \
    -Dswing.aatext=true \
    -jar "${HOME}/.local/lib/z/z.jar" \
    "$@"
```

Make it executable: `chmod +x ~/bin/z`

### Launching

```sh
z                        # Open with an empty scratch buffer
z myfile.scala           # Open a specific file
z ~/projects/myapp       # Open a directory
z file1.go file2.go      # Open multiple files
```

### Anatomy of the Editor

When z opens, you see three distinct zones stacked vertically:

```
┌─────────────────────────────────────────────────────┐
│  Help  NewCol  Put  Dump  Load  Dir          ← App tag line
├──────────────────────────┬──────────────────────────┤
│  CloseCol  New  Sort     │  CloseCol  New  Sort      ← Column tag lines
├──────────────────────────┼──────────────────────────┤
│  ~/proj/main.go  Get Put │  ~/proj/util.go  Get Put  ← Window tag lines
├──────────────────────────┼──────────────────────────┤
│                          │                           │
│   file content here      │   file content here       │ ← Window bodies
│                          │                           │
└──────────────────────────┴──────────────────────────┘
│  1/342 @ 0  Tab:4  NoWrap  Hack Regular 14           ← Status bar
└─────────────────────────────────────────────────────┘
```

There are three levels of hierarchy:

- **App** — the whole editor. Its tag line runs across the very top.
- **Columns** — vertical strips. Each has its own tag line.
- **Windows** — individual file panes within columns. Each has a tag line above its body.

This hierarchy is not just organizational — it determines the *scope* of commands. A command run from the app tag line affects everything. The same command run from a window tag line affects only that window. This one rule explains a huge amount of z's behavior.

### The Tag Line

Each tag line is just editable text. The commands pre-loaded into it are there for convenience — you can add your own, remove ones you never use, or type a command anywhere in any tag line and execute it. There is nothing special about the words already there; they are just text that happens to be commands.

### The Status Bar

The status bar at the bottom of each window gives you a live readout:

```
12/340 @ 4   Tab:4   NoWrap   NoIndent   Hack Regular 14
```

Reading left to right: current line / total lines, cursor column, tab size, wrap state, indent state, and the active font. When LSP or syntax highlighting is active, those appear here too.

---

## 2. The Three Mouse Buttons

This is the heart of z. Nothing else in this tutorial will make complete sense until the mouse model clicks for you, so we will take it slowly.

### B1 — Left Button: Select & Place

B1 behaves exactly as you expect from any editor:

- **Click** to place the caret
- **Click and drag** to select text
- **Shift+B1** to extend the selection by character
- **Shift+Ctrl+B1** to extend by word
- **Ctrl+B1** for brace/symbol matching — click inside any `{}`, `[]`, `()`, or `<>` pair and z selects everything between the matching delimiters. Works with any repeated character as a delimiter too.

### B2 — Middle Button: Execute

B2-drag is the execute gesture. Hold B2, drag across some text to select it, release — z runs it as a command.

That's it. Any text in any window or tag line can become a command with a B2-drag. You can write a shell pipeline directly in a file you're editing, B2-drag across it to run it, and see the output appear in a `+Results` window — without ever leaving the editor or touching a terminal.

> **Tip:** You do not need to select precisely. If your B2 drag starts inside an already-selected region, z will use the existing selection.

### B3 — Right Button: Look & Act

B3 is the smart button. It does not just execute — it *thinks* first, working through a priority list:

1. **Valid file or directory path** → opens it in a new window
2. **`:n`** → jumps to line *n* in the current file
3. **`:/regexp`** → searches forward for *regexp* from the current position
4. **`filename:n`** → opens *filename* at line *n*
5. **`filename:/regexp`** → opens *filename*, then searches for *regexp*
6. **Anything else** → searches forward for the text in the current window
7. **Search fails** → executes the text as a command

This cascade is elegant. B3 on `main.go` opens `main.go`. B3 on `:42` jumps to line 42. B3 on `foo` finds the next occurrence of "foo". B3 on `Put` saves the file. The right thing happens naturally.

Like B2, B3 also works as a drag gesture: hold B3, drag to select, release to act on the selection.

### Relative Path Style Preservation

When a window has a relative path, B3 navigation preserves that style — any path opened from it gets a relative tag rather than an absolute one. This applies uniformly: directory listings, paths in body text, and tag path segments all behave consistently.

For example, a directory window showing `src/main`:

| You B3-click | New window tag |
|-------------|---------------|
| `Foo.scala` in the listing | `src/main/Foo.scala` |
| `util/` in the listing | `src/main/util` |
| `main` in the tag line | `src/main` |
| `src` in the tag line | `src` |

In the tag line, this applies only to the path at the very start. Relative paths appearing later in the tag — such as arguments to `Get` or other commands — resolve normally (relative to the file's parent directory).

### Forcing a Command with `%`

Sometimes z's smart B3 gets in the way. If you have a word like `Put` in a file you're editing and you want to *run* it rather than search for it, prefix it with `%`:

```
%Put
%ls -la
%Close
```

The `%` tells z: skip the look/search logic, treat this as a command directly.

> **Tip:** Placing the caret at the very end of the file also bypasses look logic — another way to force command execution.

### Putting It Together

Once these three buttons become second nature, z starts to feel very fast. You select with B1, execute with B2, navigate with B3 — and your hands almost never leave the mouse for the common operations of an editing session.

---

## 3. Opening, Navigating & Closing Files

### Opening Files

The simplest way to open a file is to type its path anywhere — in a tag line, in a body — and B3-click on it. z recognizes paths automatically and opens them in a new window.

Path prefixes are supported everywhere:

| Prefix | Expands to |
|--------|-----------|
| `~` | Your home directory |
| `~/foo` | `$HOME/foo` |
| `.` | The window's current root directory |
| `./foo` | root/foo |
| `..` | Parent of the current root |
| `../foo` | root/../foo (canonicalized) |

Expansion is *invisible* — the tag line keeps the text as you typed it. The resolved absolute path is used only when z actually reads or writes the file.

**Using `Get`:** Type a path in a window's tag line and execute `Get` to load that path into the window. `Get` without an argument reloads the current file from disk — useful for picking up external changes.

**Directories:** Opening a directory shows a sorted listing with subdirectories marked by a trailing `/`. B3 on any entry in the listing opens it.

### Navigating Within and Across Files

| What you type | What B3 does |
|--------------|-------------|
| `:42` | Jump to line 42 in current file |
| `:/TODO` | Search forward for "TODO" |
| `server.go:10` | Open server.go at line 10 |
| `server.go:/main` | Open server.go, find "main" |
| `someword` | Search forward for "someword" |

These work from tag lines, from body text, anywhere. B3 on an error message that says `main.go:47:` will open `main.go` at line 47.

> **Note:** Searches run forward from the caret to the end of the file only — there is no wrap-around. If the text is not found, z falls through to executing it as a command instead. To search again from the top, move the caret to the beginning of the file first (`Ctrl+Home`).

### Saving Files

`Put` saves the current file. From a column tag line, it saves all files in that column. From the app tag line, it saves everything open. The dirty flag (`*` prefix in the tag line) is cleared on a successful save.

`Put [fname]` saves to a different filename without changing the tag line.

### Closing Files

- `Close` — closes the window, prompting if there are unsaved changes
- `CloseCol` — closes an entire column and all its windows
- `CLOSE` — closes without any confirmation, regardless of dirty state

> **Warning:** `CLOSE` (all caps) is intentionally destructive. It discards unsaved changes silently.

### Bind — Navigate in Place

By default, B3 navigation opens a new window for each destination. `Bind` changes this: when bind mode is on, navigating replaces the current window's content instead of opening a new one. This is useful when you want a single "reference" window that follows your jumps.

Execute `Bind` from a window's tag line to toggle it. The status bar will show `Bind` when it is active.

### Dir — Changing the Root Directory

Each window has a *root directory* — the base against which relative paths are resolved. By default this is the directory of the file the window opened with. `Dir` changes it:

```
Dir ~/otherproject
Dir ./src
Dir ..
```

`Dir` accepts the same path prefixes as everywhere else in z (`~`, `./`, `../`). From a column or app tag line, it applies to all windows in scope.

> **Watch for unexpected dirty flags:** If a window's tag line contains a *relative* path (e.g., `./main.go`), changing its root with `Dir` means that path now resolves to a different file — so z marks the window dirty to signal that its tag line path has changed meaning. Windows with absolute paths are unaffected.

---

## 4. Editing Essentials

### Keyboard Shortcuts

The standard shortcuts work as expected:

| Action | Shortcut |
|--------|---------|
| Cut | `Ctrl+X` |
| Copy (Snarf) | `Ctrl+C` |
| Paste | `Ctrl+V` |
| Undo | `Ctrl+Z` |
| Redo | `Ctrl+R` |
| Select all | `Ctrl+A` |
| Delete word left | `Ctrl+Backspace` |
| Delete word right | `Ctrl+Delete` |
| Go to top/bottom | `Ctrl+Home` / `Ctrl+End` |
| Previous/next word | `Ctrl+Left` / `Ctrl+Right` |
| Extend selection | `Shift+cursor keys` |
| Extend by word | `Shift+Ctrl+cursor` |

Note that z uses the Acme term *snarf* for copy — you will see it in commands (`Snarf`) and in the help text. It means exactly what you think.

### Undo and Redo

`Undo` and `Redo` are available as tag line commands as well as keyboard shortcuts. Both are per-window and maintain a full history for the session.

### Line Behaviour

**`Wrap`** — toggles line wrapping. Off by default. Execute from any tag line.

**`Indent`** — toggles auto-indent. When on, pressing Enter preserves the leading whitespace of the current line. Off by default.

**`Tab n`** — sets the tab width to *n* spaces. For example, `Tab 2` or `Tab 8`. The default is 4.

### Bookmarks with `Mark`

`Mark` appends a bookmark for the current cursor position to a scratch window called `path+Mark` (e.g., `~/myproject+Mark`). Each entry is recorded as:

```
filename:linenum    line content
```

B3 on any entry in the mark window jumps straight to that location. This makes `Mark` a lightweight jump list — execute it whenever you are about to wander away from a spot you know you will need to return to.

### The Dirty Flag

A `*` before the filename in a window's tag line means the file has unsaved changes. You can manually control this with `Clean` (mark as unmodified) and `Dirty` (mark as modified). These are occasionally useful when you want to suppress a save prompt or force one.

---

## 5. Running External Commands

This is where z becomes genuinely powerful. Rather than embedding a terminal emulator as an afterthought, z treats external commands as first-class citizens: their output flows directly into your editing session.

There are four external command operators, each with a distinct relationship between the command, the selection, and the output.

### `< cmd` — Replace With Output

The `<` operator runs *cmd* and **replaces the current selection** (or inserts at the caret if nothing is selected) with the command's standard output.

```
< date
< echo "Hello, world"
< git log --oneline -10
```

Practical use: you have a placeholder in a file that should be replaced with generated content. Select it, then run `< myscript.py` to replace it in place.

### `> cmd` — Send Selection as Input

The `>` operator takes the current selection (or the entire file if nothing is selected) and **sends it as standard input** to *cmd*. Output goes to a `+Results` scratch window.

```
> wc -l
> python3
> jq .
```

Practical use: select a JSON blob and run `> jq .` to pretty-print it in `+Results` without touching the original.

### `| cmd` — Pipe Through

The `|` operator **pipes the selection through** *cmd* and **replaces the selection with the output**. The selection goes in as stdin; stdout replaces it.

```
| sort
| sort -u
| column -t
| python3 -c "import sys; print(sys.stdin.read().upper())"
```

Practical use: select a list of items, run `| sort -u` to sort and deduplicate, get the result back in the same spot.

### `! cmd` — Replace Window Content

The `!` operator runs *cmd* and **replaces the entire window content** with its output.

```
! ls -la
! git diff
! cat README.md
```

Where the output goes depends on where you execute `!` from:

- **Window tag line** → replaces that window's content
- **Column tag line** → opens a new window in that column
- **App tag line** → opens a new window in the rightmost column

Practical use: keep a window showing live `git diff` output by running `! git diff` whenever you want a refresh.

### Working Directories and `Dir`

Understanding *where* an external command runs is important — and it is not quite what you might expect.

z derives the working directory from the **resolved tag line path**, not from the window's root directly:

- If the tag line resolves to a **directory** → the command runs there
- If the tag line resolves to a **file** → the command runs in that file's **parent directory**
- If the tag line has **no path** (a scratch buffer or empty window) → the command runs in the window's root

`Dir` sets the root, which matters only when the tag line contains a *relative* path — because that is when the root is used to resolve it. If a window's tag line has an absolute path (e.g., `/home/user/myapp/main.go`), the command always runs in `/home/user/myapp/` regardless of what `Dir` is set to.

In practice this means: if you open `~/myapp/src/server.go` by absolute path and run `! sbt test`, sbt runs from `~/myapp/src/` — which is probably not your project root and will fail. You have two options: change the tag line path to point at the project root directory, or use a `cd` inside the command itself:

```
! cd ~/myapp && sbt test
```

`Dir` is most powerful when working with relative tag line paths or scratch buffers, where the root directly determines where commands land.

> **`Dir` is not the same as the LSP workspace root.** They are independent settings. See [Section 10](#10-language-server-protocol-lsp) for the distinction.

### While a Command Is Running

The tag line shows `<!>` while an external command is active. To terminate it early, execute `Kill` from that window's tag line.

### Interactive Processes with `Input`

When you start a long-running process (a REPL, a debugger, a build tool), you can talk to it interactively using `Input` mode.

1. Start the process: `< python3` or `! bash`
2. Execute `Input` from the tag line to toggle interactive mode
3. z watches for prompt lines (lines ending in `>`, `$`, `%`, `?`, or `#` by default)
4. Type your response on the same line as the prompt, then press **Enter** — z sends it to the process

To customise the prompt detection pattern:

```
Input [>$]
```

This sets the prompt regexp to any line ending in `>` or `$`.

### Batch Commands with `X` and `Y`

`X` and `Y` let you run a command across multiple open windows at once.

```
X '.*\.go' Put              ← Save all open Go files
Y '.*_test\.go' Hilite go   ← Enable Go highlighting on all non-test files
X '.*\.scala' > scalafmt    ← Format all Scala files
```

`X 'pattern' cmd` runs *cmd* in every window whose path matches the pattern. `Y` is the inverse — it runs *cmd* in every window whose path does *not* match. Matching is anchored (Java `.matches`), so use `.*foo.*` to match paths containing "foo".

### A Note on Quoting

When a command or path contains spaces, wrap it in **single quotes**:

```
< 'wc -l'
Get '/home/user/My Documents/notes.txt'
| 'awk {print $2}'
```

Single-quote wrapping applies anywhere z parses a command or path — file names, `Get`, `Put`, `Dir`, and all four external command operators. Inside single quotes, the text is treated as a single token regardless of spaces.

### Environment Variables

Every external command launched by z has access to two environment variables describing the current window's file:

| Variable | Value |
|---------|-------|
| `Z_FILE` | File path as written in the tag line (`~` and `./` expanded, symlinks not resolved) |
| `Z_FP` | Canonical absolute path (symlinks fully resolved) |
| `Z_DIR` | Working directory where the command runs |
| `Z_SELECTION` | Currently selected text (empty string if nothing selected) |

All four variables are available to every external command — `< > | !` operators and user scripts alike.

---

## 6. Working with Multiple Windows & Columns

### Creating Windows and Columns

- **`New`** — creates a new empty window. From the app tag line, creates one in each column.
- **`NewCol`** — creates a new column (app tag line only).
- **`Zerox`** — clones the current window into a new window. Both windows show the same file; changes in one are reflected in the other.

### Moving Things Around

Windows can be moved freely within and between columns.

| Command | Effect |
|---------|--------|
| `Up` | Move window up within its column |
| `Dn` | Move window down within its column |
| `Lt` | Move window to the column on the left (or move a column left) |
| `Rt` | Move window to the column on the right (or move a column right) |

Run these from the window's tag line to move that window. Run `Lt` or `Rt` from a *column* tag line to move the entire column.

### Rotating the Layout

`RotateView` toggles the layout orientation.

- From a **column tag line**: flips that column's windows between top-to-bottom stacking (the default) and side-by-side stacking.
- From the **app tag line**: also flips the column layout (columns go from left-to-right to top-to-bottom), and each existing column independently toggles its window orientation.

New columns created after a rotation inherit the current app orientation.

Rotation state is persisted automatically: per-column state survives `Dump`/`Load`, and the app-level orientation is saved to `~/.z/settings` on exit so it is restored on next launch.

### Sorting

`Sort` — executed from a column tag line — sorts that column's windows alphabetically by path. From the app tag line, it sorts each column independently.

---

## 7. Scratch Buffers

A scratch buffer is any window whose filename contains a `+`. z will never write these to disk — `Put` is a no-op on them.

```
+scratch
/home/user/+notes
/tmp/+Results
~/myproject+Mark
```

Scratch buffers appear in `Dump` state and survive across sessions. They are ideal for accumulating notes, command output, or anything you want to keep handy without creating actual files.

### System Scratch Buffers

z creates several scratch buffers automatically:

| Buffer | Created by |
|--------|-----------|
| `+Results` | `>`, `|`, and `!` command output |
| `+Diagnostics` | LSP diagnostic messages |
| `+Props` | The `Props` command |
| `+Fonts` | The `Fonts` command |
| `+Help` | The `Help` command |
| `path+Mark` | The `Mark` command |

### Controlling Scroll Behaviour

By default, z scrolls a window as new content arrives. For a `+Results` window receiving a lot of output, this can be distracting. Execute `Scroll off` from the window's tag line to stop auto-scrolling — the content still streams in, but the view stays put. `Scroll on` restores the default.

---

## 8. Appearance & Fonts

### Fonts

z ships with three bundled fonts:

- **Hack Regular** — fixed-width, used by default in body and tag lines
- **Bitstream Vera Sans** — proportional
- **Bitstream Vera Serif** — proportional

Two font slots exist: a fixed-width slot (`FONT`) and a variable-width slot (`Font`). You can switch between them instantly:

```
FONT                        ← Apply the stored fixed-width font (Hack Regular 14)
Font                        ← Apply the stored variable-width font (Bitstream Vera Sans 14)

FONT 'Hack Regular' 16      ← Set and apply a fixed-width font at size 16
Font 'Bitstream Vera Serif' 13  ← Set and apply a variable-width font
```

`Fonts` (from the app tag line) lists every font available to z, in a `+Fonts` scratch window.

**Tag line fonts** are set with `TagFont`:

```
TagFont 'Hack Regular' 12
```

Font commands cascade with the scope hierarchy:
- From a **window** tag line: affects that window only
- From a **column** tag line: affects the column tag and all window tags in it
- From the **app** tag line: affects all tag lines editor-wide and sets the default for new windows

### Line Numbers

`Ln` toggles line numbers in the body gutter. Off by default.

### Current Line Highlight

`CLine` toggles the highlight on the line containing the caret. On by default.

### Colours

Body and tag line colours are set by RGB values (integers 0–255):

```
ColorBack 30 30 30        ← Body background
ColorFore 220 220 200     ← Body foreground
ColorCaret 255 200 0      ← Caret colour
ColorSelBack 60 80 120    ← Selection background
ColorSelFore 255 255 255  ← Selection foreground

ColorTBack 50 50 60       ← Tag background
ColorTFore 180 180 220    ← Tag foreground
```

`Props` will show you the current RGB values for all colour settings if you need to inspect or copy them.

---

## 9. Syntax Highlighting & Themes

### Enabling Highlighting

`Hilite` enables syntax highlighting, detecting the language from the file extension:

```
Hilite             ← Auto-detect from extension
Hilite scala       ← Force Scala highlighting
Hilite go
Hilite python
Hilite off         ← Disable highlighting
```

### Themes

Once highlighting is on, apply a colour theme with `Theme`:

```
Theme z           ← Default (matches editor colour scheme)
Theme dark
Theme monokai
Theme idea
Theme eclipse
Theme druid
Theme vs
```

### After Loading a Session

When you restore a session with `Load`, the highlighting state is restored — but the actual colouring is *not* automatically reapplied. If your files were highlighted before the dump, re-run `Hilite` after loading to bring the colours back.

---

## 10. Language Server Protocol (LSP)

LSP connects z to a language server for the file you are editing, enabling real-time diagnostics, hover documentation, and code completion.

### `Dir` vs. the LSP Project Root

These are two independent settings that are easy to conflate:

- **`Dir`** sets the window's root directory — where relative paths resolve and where external commands run (when the tag line has a relative or missing path).
- **`Lsp [projRoot]`** sets the *LSP workspace root* — the directory the language server uses to locate build files (`build.sbt`, `go.mod`, `Cargo.toml`, etc.) and index the project.

Running `Dir ~/myapp/src` does not tell the language server that your project lives in `~/myapp`. The server needs to be started with `Lsp ~/myapp` so it finds the build file at the project root. Pointing it at a subdirectory is a common source of "why isn't LSP finding my symbols?" confusion.

Equally, `Lsp ~/myapp` does not change where your shell commands run — for that you still need `Dir`.

### Starting the Server

From a window's tag line, execute:

```
Lsp
Lsp .              ← Use the window's current root as project root
Lsp ~/myproject    ← Explicit project root
```

z has built-in defaults for common languages — no configuration needed for:

`go`, `python`, `java`, `scala`, `javascript`, `typescript`, `rust`, `kotlin`, `shellscript`

Once active, you get:

- **Squiggly underlines** for errors, warnings, and info
- **Hover tooltips** with type information and documentation (hover over any underlined or interesting symbol)
- **A `+Diagnostics` window** listing all current issues, updated as you type
- **Code completion** via `Ctrl+Space` or the `Complete` command

### Forcing a Diagnostic Refresh

`Check` forces an immediate update of diagnostics. Normally they update automatically after a short pause when you stop typing, but `Check` is useful when you want an instant result.

### Stopping the Server

```
Lsp off
```

This shuts down the language server for that window and clears the diagnostics.

### Configuration

To override built-in server commands or add new languages, create `~/.z/lsp.conf`:

```
go     = gopls
python = pylsp
scala  = metals
```

One `langId = command` per line. z reads this file at startup, and your entries take priority over the built-in defaults.

---

## 11. Session Management

### Saving and Restoring Sessions

`Dump` saves the full editor state — every column, every window, the content of dirty scratch buffers, fonts, and colours — to a flat file.

```
Dump                    ← Save to z.dump in the working directory
Dump ~/mysession        ← Save to a specific path
```

`Load` restores it:

```
Load                    ← Load z.dump from the working directory
Load ~/mysession
```

Both commands run from the app tag line only. The window geometry (size and position of the main window) is saved automatically to `~/.z/settings` on exit.

### Customising Default Tag Lines

The commands pre-loaded into each tag line are just defaults — they can be customised globally via `~/.z/settings`. z reads this file at startup and writes window geometry back to it on exit, so any keys you add are preserved across launches.

| Key | What it controls | Built-in default |
|-----|-----------------|-----------------|
| `tag.app` | App tag line content | `Help NewCol Put Dump Load Dir ` |
| `tag.col` | Column tag line default | `CloseCol Close New Sort ` |
| `tag.wnd` | Window tag line default | `Get Put Zerox Close \| Undo Redo Wrap Ln Indent Mark Bind ` |
| `tag.cmd` | Command/results window tag line default | `Close \| Undo Redo Wrap Kill Clear Font Scroll Input ` |

For example, to slim down your tag lines:

```
tag.app = Help NewCol Dump Load Fonts
tag.wnd = Get Put Close | Undo Redo Wrap Ln
tag.cmd = Close | Kill Clear Scroll
```

Tag lines remain fully editable at runtime — these settings only control what text they start with when z launches or opens a new window.

### Inspecting State with `Props`

`Props` opens a `+Props` scratch window with a complete property listing for the app, column, or window — depending on where you run it from. It includes paths, dirty state, scroll settings, font and colour values, LSP status, line count, cursor position, and more. Useful for debugging your setup or copying colour values to tweak elsewhere.

---

## 12. Command-Line Flags (Advanced)

These flags are most useful once you are comfortable with the editor — they let you script your startup layout and automate initial actions from the command line.

### Opening Files and Columns

```sh
z file1.go file2.go           # Open two files in one column
z -c file1.go -c file2.go     # Open each file in its own column
```

`-c` creates a new column; files following it are placed into that column.

### Running Commands at Startup

```sh
z -! 'git log --oneline' myproject/
```

`-!` executes a command in the context of the last-opened file or directory. After the files are loaded, the command runs as if you had typed it in that window.

```sh
z -c! 'Hilite go' file1.go file2.go
```

`-c!` runs a command from the current column's tag line — affecting all windows in that column.

```sh
z -a! 'Put' file1.go file2.go
```

`-a!` runs a command from the app tag line — affecting all columns.

### Searching at Startup

```sh
z -l 'func main' main.go       # Open main.go and search for "func main"
z -cl 'TODO' src/              # Search for "TODO" across the current column
z -al 'FIXME' .                # Search for "FIXME" across all columns
```

`-l`, `-cl`, and `-al` run a regexp search after loading, at window, column, and app scope respectively.

### Resetting Argument Parsing

```sh
z -r file1.go
```

`-r` resets: any flags before it are processed, then subsequent arguments are treated as plain file or directory paths. Useful in shell aliases or scripts where the argument list might otherwise be ambiguous.

### Combining Flags

Flags compose naturally, left to right:

```sh
z -c main.go -! 'Hilite go' -c test.go -! 'Hilite go'
```

This opens `main.go` in one column with Go highlighting, then `test.go` in a second column, also highlighted. Each `-!` applies to the most recently opened file.

---

## 13. User Scripts

z lets you place executable scripts in a well-known directory and invoke them from any tag line using a comma prefix — no full path required. They look and feel like built-in commands.

### Script Directories

z searches for scripts in this order:

1. `.z/scripts/` in the current working directory (project-local — different per project)
2. `~/.z/scripts/` (global — available in every project)
3. Any additional directories listed in `~/.z/scripts.conf`

The global directory is created automatically on first launch. The project-local directory is just a convention — create `.z/scripts/` in your project root and z picks it up automatically.

### Invoking Scripts

Use a leading comma to invoke a script:

```
,Build
,Test --watch
,Deploy staging
,Format
```

z resolves the script name against the search directories and runs it. If not found, an error dialog shows which directories were searched.

**`,cmd`** runs the script once, in the context of where you invoke it:
- From a **window tag line** → output goes to a `path+Results` window
- From a **column tag line** → output goes to a `+Cmd` window in that column
- From the **app tag line** → output goes to `+Cmd` in the rightmost column

**`,,cmd`** runs the script once *per window* in scope:
- From a **window tag line** → same as `,cmd` (the window is the leaf)
- From a **column tag line** → runs on every window in that column
- From the **app tag line** → runs on every window in every column

`,,Format` from the app tag line, for example, runs your formatter on every open file simultaneously.

### Environment Variables

Scripts receive these environment variables from z:

| Variable | Value |
|---------|-------|
| `Z_FILE` | File path as written in the tag line (`~` and `./` expanded, symlinks not resolved) |
| `Z_FP` | Canonical absolute path (symlinks fully resolved) |
| `Z_DIR` | Working directory where the script runs |
| `Z_SELECTION` | Currently selected text (empty string if nothing selected) |

A script can ignore these entirely, or use them to operate on the current file:

```sh
#!/bin/sh
# .z/scripts/Format — format the current file in place
scalafmt "$Z_FILE"
```

```sh
#!/bin/sh
# .z/scripts/Test — run tests for the current project
cd "$Z_DIR" && sbt test
```

### Configuration

To add extra script directories beyond the two defaults, create `~/.z/scripts.conf`:

```
scripts.path = /work/team-scripts:/home/user/bin/z-scripts
```

Colon-separated, appended after the auto-discovered directories.

### Scripts Are Editor-Agnostic

Scripts are plain executables — shell scripts, Python scripts, anything. They have no knowledge of z. The same script can be run directly from a terminal. The comma prefix is purely z's way of finding and invoking them; the script itself is just a file.

---

## 14. Putting It All Together

Let us walk through a realistic workflow from scratch — opening a Scala project, using LSP, running tests, navigating errors, and saving the session.

### 1. Open the Project

```sh
z ~/myapp/src -c ~/myapp/src/test
```

Two columns: source files on the left, tests on the right.

### 2. Set Up Syntax Highlighting

From the app tag line:

```
Hilite scala
Theme monokai
Ln
```

All open windows are now highlighted, themed, and showing line numbers.

### 3. Start LSP

Click into a source window. From its tag line:

```
Lsp ~/myapp
```

The path `~/myapp` is the *LSP workspace root* — the directory where Metals will find `build.sbt` and index the project. This is distinct from where your shell commands run (see the next step).

Wait a moment while Metals indexes. Watch the status bar — it will show the LSP status. The `+Diagnostics` window appears with any issues found.

### 4. Set the Working Directory and Run the Tests

The windows were opened by path (`~/myapp/src` and `~/myapp/src/test`), so external commands run in those subdirectories by default — not at the project root where `build.sbt` lives. Fix that before running sbt.

From the app tag line:

```
Dir ~/myapp
```

This sets all windows' root to `~/myapp`. Now from the test column tag line:

```
! sbt test
```

A new window opens in that column with streaming test output. While it runs, `<!>` appears in the tag. When it finishes, scroll up through the output.

Spot a failure: `MainSpec.scala:42: assertion failed`. B3 on that text — z opens `MainSpec.scala` at line 42.

### 5. Fix the Bug

Edit the code. LSP underlines the fix as you type if anything is wrong. Press `Ctrl+Space` for completion suggestions. When the diagnostics window is clean, `Put` to save.

### 6. Re-run with X

```
X '.*_test\.scala' ! sbt testOnly
```

Runs `sbt testOnly` in every test window. Watch all of them refresh simultaneously.

### 7. Bookmark Key Locations

Navigate to a tricky piece of logic. Execute `Mark`. Move somewhere else. Navigate to a second location. Execute `Mark` again. Now open `+Mark` and B3 on entries to jump between them.

### 8. Save the Session

From the app tag line:

```
Dump ~/myapp/session
```

Tomorrow:

```sh
z
```

From the app tag line:

```
Load ~/myapp/session
```

Everything is back: every window, every scratch buffer, every colour setting. Re-run `Hilite scala` on any window that needs its colours restored.

---

## Quick Reference

### Mouse

| Gesture | Action |
|---------|--------|
| B1 click/drag | Cursor, select |
| Shift+B1 | Extend selection |
| Ctrl+B1 | Brace/symbol matching |
| B2 drag | Execute selected text as command |
| B3 click/drag | Look/navigate/execute (smart) |
| `%text` then B3 | Force text as command |

### Keyboard

| Shortcut | Action |
|---------|--------|
| `Ctrl+Z` / `Ctrl+R` | Undo / Redo |
| `Ctrl+X` / `Ctrl+C` / `Ctrl+V` | Cut / Snarf / Paste |
| `Ctrl+A` | Select all |
| `Ctrl+Home` / `Ctrl+End` | Top / Bottom |
| `Ctrl+Left` / `Ctrl+Right` | Prev / Next word |
| `Ctrl+Backspace` / `Ctrl+Delete` | Delete word left / right |
| `Ctrl+F` | File chooser at caret |
| `Ctrl+Space` | LSP completion |

### File Commands

| Command | Scope | Action |
|---------|-------|--------|
| `Get` | Win | Reload from tag line path |
| `Get [fname]` | Win | Load fname |
| `Put` | Win/Col/App | Save file(s) |
| `New` | Col/App | New empty window |
| `NewCol` | App | New column |
| `Zerox` | Win | Clone window |
| `Close` | Win/Col/App | Close (prompts if dirty) |
| `CloseCol` | Col | Close column |
| `CLOSE` | Win | Force close, no prompt |
| `Dump [fname]` | App | Save session |
| `Load [fname]` | App | Restore session |

### Navigation

| Text | B3 action |
|------|----------|
| `path/to/file` | Open file |
| `:42` | Go to line 42 |
| `:/regexp` | Search forward |
| `file:42` | Open file at line |
| `file:/regexp` | Open file, search |

### Window Management

| Command | Action |
|---------|--------|
| `Lt` / `Rt` | Move window/column left/right |
| `Up` / `Dn` | Move window up/down |
| `Sort` | Sort windows alphabetically |
| `RotateView` | Toggle window/column layout orientation |

### External Commands

| Command | Input | Output |
|---------|-------|--------|
| `< cmd` | — | Replaces selection (or inserts at caret) |
| `> cmd` | Selection (or full file) | `+Results` window |
| `\| cmd` | Selection | Replaces selection |
| `! cmd` | — | Replaces window (or new window from col/app) |
| `X 'pat' cmd` | — | Runs cmd in matching windows |
| `Y 'pat' cmd` | — | Runs cmd in non-matching windows |
| `Kill` | — | Terminate running command |
| `Input` | — | Toggle interactive mode |

### User Scripts

| Invocation | Scope | Action |
|-----------|-------|--------|
| `,scriptname [args]` | Win/Col/App | Run script once at invoked level |
| `,,scriptname [args]` | Col/App | Run script on every window in scope |

Scripts live in `.z/scripts/` (project) or `~/.z/scripts/` (global). See [Section 13](#13-user-scripts).

### Editing

| Command | Action |
|---------|--------|
| `Wrap` | Toggle line wrap |
| `Indent` | Toggle auto-indent |
| `Tab n` | Set tab width |
| `Mark` | Bookmark current line |
| `Clean` / `Dirty` | Toggle dirty flag |
| `Clear` | Erase window content |
| `Scroll on/off` | Toggle auto-scroll |
| `Bind` | Toggle navigate-in-place |
| `Dir <path>` | Change root (affects relative path resolution and command working dir for relative/scratch windows; does not affect LSP workspace root) |

### Appearance

| Command | Action |
|---------|--------|
| `Font [name] [pt]` | Variable-width body font |
| `FONT [name] [pt]` | Fixed-width body font |
| `TagFont [name] [pt]` | Tag line font |
| `Fonts` | List available fonts |
| `Ln` | Toggle line numbers |
| `CLine` | Toggle current-line highlight |
| `Hilite [lang\|off]` | Syntax highlighting |
| `Theme [name]` | Colour theme |
| `ColorBack R G B` | Body background colour |
| `ColorFore R G B` | Body foreground colour |

### LSP

| Command | Action |
|---------|--------|
| `Lsp [root]` | Start language server |
| `Lsp off` | Stop language server |
| `Check` | Refresh diagnostics |
| `Complete` | Show completion popup |

### Command-Line Flags

| Flag | Action |
|------|--------|
| `-c` | New column |
| `-! 'cmd'` | Run cmd in last window |
| `-c! 'cmd'` | Run cmd in current column |
| `-a! 'cmd'` | Run cmd app-wide |
| `-l 're'` | Search in last window |
| `-cl 're'` | Search in current column |
| `-al 're'` | Search app-wide |
| `-r` | Reset: treat rest as paths |

### Scratch Buffers

Any path containing `+` is a scratch buffer. Never written to disk. Examples: `+scratch`, `~/myproject+notes`, `+Results`.

### Session Files

| File | Purpose |
|------|---------|
| `~/.z/settings` | Window geometry (auto-saved) and tag line defaults (`tag.app`, `tag.col`, `tag.wnd`, `tag.cmd`) |
| `~/.z/lsp.conf` | LSP server configuration |
| `z.dump` (default) | Saved session (Dump/Load) |
| `~/.z/scripts.conf` | User script directory configuration |
| `~/.z/scripts/` | Global user scripts directory |

---

*z is inspired by [Plan 9 Acme](https://9p.io/plan9/). The philosophy is simple: text is the interface. Once that lands, everything else follows.*
