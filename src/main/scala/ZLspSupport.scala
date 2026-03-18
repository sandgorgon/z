/*
Copyright (c) 2011-2026. Ramon de Vera Jr.
All Rights Reserved

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to use
, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
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

import org.fife.ui.autocomplete.{AutoCompletion, AutoCompletionEvent, AutoCompletionListener,
	Completion, CompletionCellRenderer, DefaultCompletionProvider}
import org.eclipse.lsp4j.{Diagnostic, DiagnosticSeverity}

// Owns all LSP infrastructure for one ZWnd: client lifecycle, hover/change timers,
// autocomplete wiring, and the reflection-based resolve listener.
// Callbacks are invoked on the EDT by the caller (e.g. SwingUtilities.invokeLater).
class ZLspSupport(
	body: ZTextArea,
	getPath: () => String,
	onStatusChange: () => Unit,
	onDiagnosticsReady: String => Unit
) {
	var client:  Option[ZLspClient] = None
	var root    = ""
	var status  = ""
	var enabled = false

	@volatile private var resolveVersion = 0
	private var listenerAdded = false
	private var hoverPoint: java.awt.Point = new java.awt.Point(0, 0)

	val parser         = new ZLspDiagnosticParser
	val provider       = new DefaultCompletionProvider() {
		override def getListCellRenderer() = new CompletionCellRenderer()
	}
	val autoCompletion = new AutoCompletion(provider)

	private val hoverTimer     = new javax.swing.Timer(500, null)
	private val didChangeTimer = new javax.swing.Timer(400, null)

	// ── wiring ──────────────────────────────────────────────────────────────

	body.peer.addParser(parser)

	hoverTimer.addActionListener(_ => {
		client.foreach { c =>
			try {
				val dot  = body.peer.viewToModel2D(hoverPoint).toInt
				val line = body.lineNo(dot)
				val col  = dot - body.lineStart(line)
				c.hover(line, col, text =>
					javax.swing.SwingUtilities.invokeLater(() => {
						body.lspTooltip = if (text.isEmpty) null else
							"<html><pre>" +
							text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") +
							"</pre></html>"
						if (text.nonEmpty) {
							val pt = hoverPoint
							javax.swing.ToolTipManager.sharedInstance().mouseMoved(
								new java.awt.event.MouseEvent(body.peer,
									java.awt.event.MouseEvent.MOUSE_MOVED,
									System.currentTimeMillis(), 0, pt.x, pt.y, 0, false)
							)
						}
					})
				)
			} catch { case _: Throwable => }
		}
	})
	hoverTimer.setRepeats(false)

	didChangeTimer.addActionListener(_ => client.foreach(_.didChange(body.text)))
	didChangeTimer.setRepeats(false)

	body.peer.addMouseMotionListener(new java.awt.event.MouseMotionAdapter {
		override def mouseMoved(e: java.awt.event.MouseEvent): Unit =
			if (enabled) { hoverPoint = e.getPoint; hoverTimer.restart() }
	})

	body.peer.getDocument.addDocumentListener(new javax.swing.event.DocumentListener {
		def insertUpdate(e: javax.swing.event.DocumentEvent): Unit  = if (enabled) didChangeTimer.restart()
		def removeUpdate(e: javax.swing.event.DocumentEvent): Unit  = if (enabled) didChangeTimer.restart()
		def changedUpdate(e: javax.swing.event.DocumentEvent): Unit = {}
	})

	autoCompletion.setAutoActivationEnabled(false)
	autoCompletion.setAutoCompleteSingleChoices(false)
	autoCompletion.setShowDescWindow(true)
	autoCompletion.setChoicesWindowSize(350, 200)
	autoCompletion.setDescriptionWindowSize(400, 200)
	autoCompletion.setTriggerKey(javax.swing.KeyStroke.getKeyStroke(
		java.awt.event.KeyEvent.VK_PERIOD, java.awt.event.InputEvent.CTRL_DOWN_MASK))
	autoCompletion.install(body.peer)

	// Replace RSTA's default trigger action with our async LSP fetch.
	body.peer.getInputMap.put(autoCompletion.getTriggerKey, "z.complete")
	body.peer.getActionMap.put("z.complete", new javax.swing.AbstractAction() {
		def actionPerformed(e: java.awt.event.ActionEvent): Unit = complete()
	})

	// On first popup show, inject a ListSelectionListener via reflection so we can
	// fire completionItem/resolve for the selected item and refresh the desc window.
	autoCompletion.addAutoCompletionListener(new AutoCompletionListener() {
		def autoCompleteUpdate(e: AutoCompletionEvent): Unit =
			if (e.getEventType == AutoCompletionEvent.Type.POPUP_SHOWN && !listenerAdded)
				setupResolveListener()
	})

	// ── public API ──────────────────────────────────────────────────────────

	def indexing: Boolean = client.exists(_.indexing)

	def start(projRoot: String, langId: String): Unit = {
		if (!enabled) {
			ZLspManager.serverCmd(langId).foreach { cmd =>
				val uri = s"file://${new java.io.File(projRoot).getCanonicalPath}/"
				val c = new ZLspClient(langId, cmd, getPath(), uri, onDiagnostics,
					() => javax.swing.SwingUtilities.invokeLater(() => onStatusChange()),
					p  => javax.swing.SwingUtilities.invokeLater(() => {
						status = if (p.hide) "" else p.text.trim
						onStatusChange()
					}))
				c.start(() => javax.swing.SwingUtilities.invokeLater(() => {
					c.didOpen(body.text)
					c.didChange(body.text)
				}))
				client  = Some(c)
				root    = new java.io.File(projRoot).getCanonicalPath
				ZLspManager.register(c)
				enabled = true
			}
		}
	}

	def stop(): Unit = {
		client.foreach { c =>
			c.didClose()
			c.shutdown()
			ZLspManager.unregister(c)
		}
		client  = None
		root    = ""
		enabled = false
		parser.clearDiagnostics()
		body.peer.forceReparsing(0)
	}

	def close(): Unit = {
		hoverTimer.stop()
		didChangeTimer.stop()
		client.foreach { c =>
			try { c.didClose() }  catch { case _: Throwable => }
			try { c.shutdown() }  catch { case _: Throwable => }
			ZLspManager.unregister(c)
		}
		client = None
	}

	def check(): Unit = client.foreach(_.didChange(body.text))

	def complete(): Unit = client.foreach { c =>
		val dot  = body.caret.dot
		val line = body.lineNo(dot)
		val col  = dot - body.lineStart(line)
		c.completion(line, col, items =>
			javax.swing.SwingUtilities.invokeLater(() => {
				provider.clear()
				items.foreach { item =>
					provider.addCompletion(new ZLspCompletion(provider, item))
				}
				autoCompletion.doCompletion()
			})
		)
	}

	def didOpen(content: String): Unit   = client.foreach(_.didOpen(content))
	def didClose(): Unit                  = client.foreach(_.didClose())
	def didChange(content: String): Unit  = client.foreach(_.didChange(content))

	// Notifies the server that the file path has changed: closes the old URI and
	// updates the client's path so the next didOpen uses the new URI.
	def updatePath(newPath: String): Unit = client.foreach { c =>
		c.didClose()
		c.updatePath(newPath)
	}

	// ── private ─────────────────────────────────────────────────────────────

	private def onDiagnostics(diags: List[Diagnostic]): Unit = {
		parser.setDiagnostics(diags)
		val content = diags.map { d =>
			val sev = Option(d.getSeverity) match {
				case Some(DiagnosticSeverity.Error)       => "error"
				case Some(DiagnosticSeverity.Warning)     => "warning"
				case Some(DiagnosticSeverity.Information) => "info"
				case _                                    => "hint"
			}
			val ln = d.getRange.getStart.getLine + 1
			s"${getPath()}:${ln}: ${sev}: ${d.getMessage}"
		}.mkString("\n")
		javax.swing.SwingUtilities.invokeLater(() => {
			body.peer.forceReparsing(0)
			onDiagnosticsReady(content)
		})
	}

	// Inject a ListSelectionListener into AutoCompletePopupWindow's private JList via
	// reflection. Called once on the first POPUP_SHOWN event. On each selection change
	// we fire completionItem/resolve against the LSP server and push the returned
	// documentation into the description window via showSummaryFor().
	private def setupResolveListener(): Unit = {
		listenerAdded = true
		try {
			val popupField = classOf[AutoCompletion].getDeclaredField("popupWindow")
			popupField.setAccessible(true)
			val popup = popupField.get(autoCompletion)
			if (popup == null) { System.err.println("[z] resolve: popupWindow is null"); return }

			val listField = popup.getClass.getDeclaredField("list")
			listField.setAccessible(true)
			val jlist = listField.get(popup).asInstanceOf[javax.swing.JList[?]]

			val descField = popup.getClass.getDeclaredField("descWindow")
			descField.setAccessible(true)

			// Resolve and cache the showSummaryFor method once so we pay the lookup cost
			// only once, and call setAccessible(true) to bypass the package-private class barrier.
			var showSummaryFor: java.lang.reflect.Method = null

			def getShowSummaryFor(descW: AnyRef): java.lang.reflect.Method = {
				if (showSummaryFor == null) {
					showSummaryFor = descW.getClass.getMethod("showSummaryFor", classOf[Completion], classOf[String])
					showSummaryFor.setAccessible(true)
				}
				showSummaryFor
			}

			jlist.addListSelectionListener { e =>
				if (!e.getValueIsAdjusting) {
					jlist.getSelectedValue match {
						case zlsp: ZLspCompletion =>
							resolveVersion += 1
							val myVersion = resolveVersion
							client.foreach { c =>
								c.resolveCompletion(zlsp.lspItem, resolved => {
									// Ignore stale responses if selection moved on.
									if (resolveVersion == myVersion) {
										val html = ZLspCompletion.buildSummary(resolved)
										if (html != null) {
											zlsp.resolvedSummary = Some(html)
											javax.swing.SwingUtilities.invokeLater(() => {
												val descW = descField.get(popup)
												if (descW != null)
													getShowSummaryFor(descW).invoke(descW, zlsp, html)
											})
										}
									}
								})
							}
						case _ =>
					}
				}
			}
		} catch {
			case ex: Exception =>
				System.err.println(s"[z] resolve listener setup failed: ${ex.getMessage}")
		}
	}
}
