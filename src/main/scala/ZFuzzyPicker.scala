/*
Copyright (c) 2011-2026. Ramon de Vera Jr.
All Rights Reserved

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

import java.io.File
import java.util.concurrent.{Executors, Future => JFuture}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing._
import javax.swing.event.{DocumentEvent, DocumentListener}
import java.awt.{BorderLayout, Color, Dimension}
import java.awt.event.{KeyAdapter, KeyEvent, MouseAdapter, MouseEvent, WindowAdapter, WindowEvent}
import scala.jdk.CollectionConverters._
import util.Properties

// Keyboard-driven fuzzy file picker. Replaces the JFileChooser for Ctrl+P.
//
// Query rules:
//   - Plain text    → fuzzy match against all files under the current root
//   - ./  ../  ~/  / prefix → re-root the walk at the resolved directory;
//     text after the last '/' becomes the fuzzy query
//
// Scoring: chars in query must appear in path in order (case-insensitive).
// Consecutive runs, filename matches, and segment-start matches score higher.
// Shorter paths win ties. Top 200 results shown.
//
// Walk skips common build/cache directories and caps at MaxFiles / MaxDepth.
// A background thread populates results; the UI refreshes every 80ms via Timer.
object ZFuzzyPicker {

	private val SkipDirs = Set(
		"target", "node_modules", ".git", ".metals", ".bsp", ".bloop",
		"build", "dist", "out", ".idea", "__pycache__", ".gradle", ".mvn",
		"vendor", ".cache", ".sass-cache", ".parcel-cache"
	)
	private val MaxFiles = 20000
	private val MaxDepth = 8
	private val MaxResults = 200

	private val executor = Executors.newSingleThreadExecutor { r =>
		val t = new Thread(r, "z-fuzzy-walk")
		t.setDaemon(true)
		t
	}

	// Entry point. Returns the selected path or None.
	// relativeTo: base directory for computing the returned path. If None, falls back to root.
	//   Pass baseDir (parent of current file) from ZWnd so the result is relative to the
	//   file's own directory — matching how look() resolves relative paths.
	def show(root: String, parent: java.awt.Component, initialQuery: String = "", relativeTo: Option[String] = None): Option[String] =
		new ZFuzzyDialog(new File(root).getCanonicalPath, relativeTo.map(new File(_).getCanonicalPath), parent, initialQuery).show()

	// Returns a match score if all query chars appear in path in order, else None.
	private[ZFuzzyPicker] def score(path: String, query: String): Option[Int] = {
		if (query.isEmpty) return Some(0)
		val p = path.toLowerCase
		val q = query.toLowerCase
		var pi = 0; var qi = 0
		var sc = 0; var lastMatch = -2
		val lastSlash = p.lastIndexOf('/')

		while (pi < p.length && qi < q.length) {
			if (p(pi) == q(qi)) {
				if (lastMatch == pi - 1)              sc += 10  // consecutive run
				if (pi > lastSlash)                   sc += 5   // in filename
				if (pi == 0 || p(pi - 1) == '/')      sc += 3   // segment start
				lastMatch = pi
				qi += 1
			}
			pi += 1
		}
		if (qi == q.length) Some(sc - path.length) else None
	}

	// Splits a query into (searchRoot, fuzzyPart).
	// If the query starts with a path prefix that resolves to a directory,
	// the root shifts there and the remainder becomes the fuzzy query.
	private[ZFuzzyPicker] def splitQuery(q: String, currentRoot: String): (String, String) = {
		val isPath = q.startsWith("/") || q.startsWith("~/") || q == "~" ||
		             q.startsWith("./") || q.startsWith("../") || q == "." || q == ".."
		if (!isPath) return (currentRoot, q)

		val lastSlash = q.lastIndexOf('/')
		val (pathPart, fuzzyPart) =
			if (lastSlash >= 0) (q.substring(0, lastSlash + 1), q.substring(lastSlash + 1))
			else                (q, "")

		val expanded = ZUtilities.expandPath(pathPart.stripSuffix("/"), currentRoot)
		val dir = new File(if (expanded.nonEmpty) expanded else currentRoot)
		if (dir.isDirectory) (dir.getCanonicalPath, fuzzyPart)
		else                 (currentRoot, q)
	}

	private[ZFuzzyPicker] def walkAsync(
		root:    String,
		sink:    CopyOnWriteArrayList[String],
		counter: AtomicInteger,
		onDone:  () => Unit
	): JFuture[?] =
		executor.submit(new Runnable {
			def run(): Unit = {
				walkDir(new File(root), root + File.separator, 0, sink, counter)
				onDone()
			}
		})

	private def walkDir(
		dir:        File,
		rootPrefix: String,
		depth:      Int,
		sink:       CopyOnWriteArrayList[String],
		counter:    AtomicInteger
	): Unit = {
		if (depth > MaxDepth || counter.get >= MaxFiles || Thread.interrupted()) return
		val entries = Option(dir.listFiles()).getOrElse(Array.empty[File])
		var i = 0
		while (i < entries.length && counter.get < MaxFiles && !Thread.interrupted()) {
			val f = entries(i)
			if (f.isDirectory) {
				val name = f.getName
				if (!SkipDirs.contains(name) && !name.startsWith("."))
					walkDir(f, rootPrefix, depth + 1, sink, counter)
			} else {
				sink.add(f.getPath.stripPrefix(rootPrefix))
				counter.incrementAndGet()
			}
			i += 1
		}
	}

	// UI colors matching the editor's default schemes
	private val ColQuery     = new Color(0x4A, 0x61, 0x95)  // tag blue
	private val ColQueryText = Color.WHITE
	private val ColListBg    = new Color(0xFF, 0xFF, 0xE0)  // body yellow
	private val ColListFg    = Color.BLACK
	private val ColSelBg     = new Color(0xC8, 0x75, 0x9F)  // editor selection
	private val ColSelFg     = Color.WHITE
	private val ColStatus    = new Color(0xEE, 0xEE, 0xEE)

	private class ZFuzzyDialog(originalRoot: String, relBase: Option[String], parent: java.awt.Component, initialQuery: String) {
		private var searchRoot               = originalRoot
		private val files                    = new CopyOnWriteArrayList[String]()
		private val fileCount                = new AtomicInteger(0)
		private var walkFuture: Option[JFuture[?]] = None
		private var walkDone                 = false
		private var result: Option[String]   = None

		private val dialog     = new JDialog(SwingUtilities.getWindowAncestor(parent), java.awt.Dialog.ModalityType.APPLICATION_MODAL)
		private val queryField = new JTextField(initialQuery, 40)
		private val listModel  = new DefaultListModel[String]()
		private val resultList = new JList[String](listModel)
		private val statusLabel = new JLabel(" ")

		// Debounce timer: only re-walk when user pauses typing a path prefix
		private val rootTimer = new Timer(150, _ => checkRootChange())
		rootTimer.setRepeats(false)

		// Refresh timer: pulls new files from walk into the displayed list
		private val refreshTimer = new Timer(80, _ => refreshList())
		refreshTimer.setRepeats(true)

		locally {
			// Query field — tag color scheme
			queryField.setFont(ZFonts.defaultTag)
			queryField.setBackground(ColQuery)
			queryField.setForeground(ColQueryText)
			queryField.setCaretColor(ColQueryText)
			queryField.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7))
			queryField.setSelectedTextColor(ColQuery)
			queryField.setSelectionColor(ColQueryText)

			// Result list — body color scheme
			resultList.setFont(ZFonts.SANS_SERIF_MONO)
			resultList.setBackground(ColListBg)
			resultList.setForeground(ColListFg)
			resultList.setSelectionBackground(ColSelBg)
			resultList.setSelectionForeground(ColSelFg)
			resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
			resultList.setFocusable(false)
			resultList.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6))

			// Status bar
			statusLabel.setFont(ZFonts.defaultTag.deriveFont(ZFonts.defaultTag.getSize - 1f))
			statusLabel.setBackground(ColStatus)
			statusLabel.setForeground(Color.DARK_GRAY)
			statusLabel.setOpaque(true)
			statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7))

			val scroll = new JScrollPane(resultList)
			scroll.setBorder(BorderFactory.createEmptyBorder())
			scroll.getViewport.setBackground(ColListBg)

			val content = dialog.getContentPane
			content.setLayout(new BorderLayout())
			content.add(queryField,   BorderLayout.NORTH)
			content.add(scroll,       BorderLayout.CENTER)
			content.add(statusLabel,  BorderLayout.SOUTH)

			dialog.setUndecorated(true)
			dialog.getRootPane.setBorder(BorderFactory.createLineBorder(ColQuery, 1))
			dialog.setPreferredSize(new Dimension(540, 320))
			dialog.pack()
			dialog.setLocationRelativeTo(parent)

			// Key handling — all keys go to queryField, arrows navigate list
			queryField.addKeyListener(new KeyAdapter {
				override def keyPressed(e: KeyEvent): Unit = e.getKeyCode match {
					case KeyEvent.VK_ESCAPE => close(None)
					case KeyEvent.VK_ENTER  =>
						val sel = if (resultList.getSelectedIndex >= 0) resultList.getSelectedValue
						          else if (listModel.size > 0) listModel.get(0)
						          else null
						if (sel != null) close(Some(sel))
					case KeyEvent.VK_UP     => moveSelection(-1)
					case KeyEvent.VK_DOWN   => moveSelection(1)
					case _ =>
				}
			})

			resultList.addMouseListener(new MouseAdapter {
				override def mouseClicked(e: MouseEvent): Unit =
					if (e.getClickCount == 2 && resultList.getSelectedIndex >= 0)
						close(Some(resultList.getSelectedValue))
			})

			queryField.getDocument.addDocumentListener(new DocumentListener {
				def insertUpdate(e: DocumentEvent): Unit  = onQueryChange()
				def removeUpdate(e: DocumentEvent): Unit  = onQueryChange()
				def changedUpdate(e: DocumentEvent): Unit = onQueryChange()
			})

			dialog.addWindowListener(new WindowAdapter {
				override def windowClosing(e: WindowEvent): Unit = close(None)
			})
		}

		private def moveSelection(delta: Int): Unit = {
			val i    = resultList.getSelectedIndex
			val next = (if (i < 0 && delta > 0) 0 else i + delta).max(0).min(listModel.size - 1)
			if (listModel.size > 0) {
				resultList.setSelectedIndex(next)
				resultList.ensureIndexIsVisible(next)
			}
		}

		private def onQueryChange(): Unit = {
			val q = queryField.getText
			val (newRoot, _) = ZFuzzyPicker.splitQuery(q, searchRoot)
			if (newRoot != searchRoot) rootTimer.restart()
			else refreshList()
		}

		private def checkRootChange(): Unit = {
			val q = queryField.getText
			val (newRoot, fuzzyPart) = ZFuzzyPicker.splitQuery(q, searchRoot)
			if (newRoot != searchRoot) {
				searchRoot = newRoot
				if (fuzzyPart != q) {
					queryField.setText(fuzzyPart)
					queryField.setCaretPosition(fuzzyPart.length)
				}
				startWalk()
			}
		}

		private def startWalk(): Unit = {
			walkFuture.foreach(_.cancel(true))
			files.clear()
			fileCount.set(0)
			walkDone = false
			statusLabel.setText("scanning...")
			walkFuture = Some(ZFuzzyPicker.walkAsync(searchRoot, files, fileCount, () =>
				SwingUtilities.invokeLater(() => { walkDone = true; refreshList() })
			))
		}

		private def refreshList(): Unit = {
			val q = queryField.getText
			val (_, fuzzy) = ZFuzzyPicker.splitQuery(q, searchRoot)
			val snapshot   = files.toArray(Array.empty[String]).toList

			val ranked =
				if (fuzzy.isEmpty) snapshot.take(MaxResults).map((0, _))
				else snapshot
					.flatMap(p => ZFuzzyPicker.score(p, fuzzy).map((_, p)))
					.sortBy { case (s, p) => (-s, p.length) }
					.take(MaxResults)

			val prevSel = resultList.getSelectedValue
			listModel.clear()
			ranked.foreach { case (_, p) => listModel.addElement(p) }

			val selIdx = Option(prevSel)
				.flatMap(s => (0 until listModel.size).find(i => listModel.get(i) == s))
				.getOrElse(0)
			if (listModel.size > 0) {
				resultList.setSelectedIndex(selIdx)
				resultList.ensureIndexIsVisible(selIdx)
			}

			val n       = fileCount.get
			val capNote = if (n >= MaxFiles) s"$n+ files (capped)" else if (walkDone) s"$n files" else s"scanning $n..."
			val rootNote =
				if (searchRoot != originalRoot) s"  in ${searchRoot.replace(Properties.userHome, "~")}"
				else ""
			statusLabel.setText(capNote + rootNote)
		}

		private def close(r: Option[String]): Unit = {
			refreshTimer.stop()
			rootTimer.stop()
			walkFuture.foreach(_.cancel(true))
			result = r.map { p =>
				val abs  = new File(searchRoot + File.separator + p).getCanonicalPath
				val base = relBase.getOrElse(originalRoot)
				if (abs.startsWith(base + File.separator)) abs.substring(base.length + 1)
				else abs
			}
			dialog.dispose()
		}

		def show(): Option[String] = {
			startWalk()
			refreshTimer.start()
			if (initialQuery.nonEmpty) refreshList()
			dialog.setVisible(true)  // enters nested event loop (modal)
			result
		}
	}
}
