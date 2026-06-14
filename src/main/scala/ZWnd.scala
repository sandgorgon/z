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

import collection.immutable.HashMap
import swing.{SplitPane, Orientation}
import swing.event.{KeyPressed, KeyReleased, Key, MouseClicked, MouseEntered, MouseExited, MousePressed, MouseDragged, MouseReleased, Event}

import java.io.{File, BufferedWriter, OutputStreamWriter}
import java.awt.{Font, Color}
import java.awt.ComponentOrientation.RIGHT_TO_LEFT
import javax.swing.text.{Utilities, DefaultCaret}
import javax.swing.{JOptionPane, ScrollPaneConstants, SwingUtilities}
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import org.fife.ui.rsyntaxtextarea.SyntaxConstants

class ZWnd(initTagText : String, initBodyText : String = "", currDir : String = ".") extends SplitPane(Orientation.Horizontal) {
	val colRoot           = new File(currDir).getCanonicalPath
	private var rootPath  = new File(currDir).getAbsolutePath
	var indIndent = false
	var indScroll = true
	var indInteractive = false
	var indBind = false
	var indHilite         = false
	var indHiliteOff      = false  // true when user explicitly ran Hilite off; suppresses auto-enable on Get
	var indTheme          = "z"
	var indLineNums       = false

	var bodyScheme = ZColorScheme(
		ZColors.BodyBack,
		Color.BLACK, Color.BLACK,
		ZColors.BodySelBack, Color.WHITE)

	var tagScheme = ZColorScheme(
		ZColors.TagBack, ZColors.TagFore, ZColors.TagCaret,
		ZColors.TagFore,   // selBack: white (inverted from TagBack)
		ZColors.TagBack)   // selFore: TagBack (inverted from white)

	var lookUpward: String => Unit = _ => ()
	var cmdProcess : Option[Process] = None
	var cmdProcessWriter : Option[BufferedWriter] = None
	var dragSel = false
	var dragSelMark = -1
	var captureTA: Option[ZTextArea] = None
	var rePrompt = ZWnd.defaultPromptRegex

	val tag = new ZTextArea(initTagText, true)
	tag.font = ZFonts.defaultTag
	tagScheme.applyTo(tag)
	tag.rows = 1

	val tagHandle = new ZDragHandle(ZColors.handleFor(tagScheme.back))

	val body = new ZTextArea(initBodyText)
	body.clineHighlight = true
	bodyScheme.applyTo(body)

	val lsp = new ZLspSupport(body, () => path,
		() => publish(new ZStatusEvent(this, statusProperties)),
		content => publish(new ZDiagnosticsReadyEvent(this, content)))

	var fontVar   = ZFonts.defaultVar
	var fontFixed = ZFonts.defaultFixed
	body.font = fontFixed

	dividerSize = 2
	peer.setMinimumSize(new java.awt.Dimension(0, 0))
	topComponent = new swing.BorderPanel {
		layout(tagHandle) = swing.BorderPanel.Position.West
		layout(tag) = swing.BorderPanel.Position.Center
	}

	val bodyScroll = new org.fife.ui.rtextarea.RTextScrollPane(body.peer, false) {
		setComponentOrientation(RIGHT_TO_LEFT)
		override def doLayout(): Unit = {
			super.doLayout()
			val vsb = getVerticalScrollBar
			val rh  = getRowHeader
			val vp  = getViewport
			if (rh != null && vsb != null) {
				val vsbW = if (vsb.isVisible) vsb.getWidth else 0
				rh.setBounds(vsbW, rh.getY, rh.getWidth, rh.getHeight)
				vp.setBounds(vsbW + rh.getWidth, vp.getY, vp.getWidth, vp.getHeight)
			}
		}
	}
	bodyScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS)
	bottomComponent = swing.Component.wrap(bodyScroll)
	styleGutter()

	tag.peer.getDocument.addDocumentListener(new javax.swing.event.DocumentListener {
		def changedUpdate(e: javax.swing.event.DocumentEvent): Unit = ()
		def insertUpdate(e: javax.swing.event.DocumentEvent): Unit  = adjustTagDivider()
		def removeUpdate(e: javax.swing.event.DocumentEvent): Unit  = adjustTagDivider()
	})
	peer.addComponentListener(new java.awt.event.ComponentAdapter {
		override def componentResized(e: java.awt.event.ComponentEvent): Unit = adjustTagDivider()
	})

	listenTo(tag.mouse.moves, body.mouse.moves)
	reactions += {
		case e : MouseEntered => publish(new ZStatusEvent(this, statusProperties))
		case e : MouseExited  => publish(new ZStatusClearEvent(this))
		case e : MousePressed => if(SwingUtilities.isMiddleMouseButton(e.peer) || SwingUtilities.isRightMouseButton(e.peer))
			e.source match {
				case ta: ZTextArea => dragSelMark = ta.peer.viewToModel2D(e.point).toInt
				case _ =>
			}
		case e : MouseDragged => if(SwingUtilities.isMiddleMouseButton(e.peer) || SwingUtilities.isRightMouseButton(e.peer)) {
			e.source match {
				case ta: ZTextArea =>
					dragSel = true
					if(dragSelMark != -1) {
						ta.peer.setCaretPosition(dragSelMark)
						dragSelMark = -1
					}
					ta.peer.moveCaretPosition(ta.peer.viewToModel2D(e.point).toInt)
				case _ =>
			}
		}
		case e : MouseReleased =>
			if(dragSel) {
				e.source match {
					case ta: ZTextArea =>
						if(SwingUtilities.isMiddleMouseButton(e.peer))  command(ZUtilities.selectedText(ta, e))
						else if(SwingUtilities.isRightMouseButton(e.peer))  look(ZUtilities.selectedText(ta, e), ta == tag)
					case _ =>
				}
			}
			dragSel = false
			dragSelMark = -1
	}

	def cancelCapture(): Unit = {
		captureTA.foreach(_.abortCapture())
		captureTA = None
	}

	listenTo(tag.keys, body.keys)
	reactions += {
		case e : KeyPressed if((e.key == Key.P) && e.peer.isControlDown()) =>
			val ta      = if(e.source.hashCode == tag.hashCode) tag else body
			val q       = Option(ta.selected).map(_.trim).filter(_.nonEmpty).getOrElse("")
			val f       = new File(path)
			val baseDir = if (f.isDirectory) f.getCanonicalPath else f.getParentFile.getCanonicalPath
			ZFuzzyPicker.show(colRoot, ta.peer, q, relativeTo = Some(baseDir)).foreach { p =>
				if (ta == tag) look(p, fromTag = true) else ta.selected = p
			}

		case e : KeyReleased =>
			// Ctrl+Enter: execute existing selection as command, or start/end capture mode
			if(e.key == Key.Enter && e.peer.isControlDown()) {
				captureTA match {
					case Some(ta) =>
						val txt     = ta.endCapture().trim
						val wasBody = ta == body
						captureTA = None
						if(txt.nonEmpty) {
							if(wasBody) body.peer.replaceSelection("")
							// | needs a body selection; after deleting capture text, select all remaining body
							if(wasBody && txt.startsWith("|")) body.peer.selectAll()
							command(txt)
						}
					case None =>
						val activeTA = if(e.source.hashCode == tag.hashCode) tag else body
						val sel = Option(activeTA.selected).getOrElse("").trim
						if(sel.nonEmpty) command(sel)
						else { captureTA = Some(activeTA); activeTA.startCapture() }
				}
			}
			// Ctrl+F: look on existing selection, or end capture mode as look
			if(e.key == Key.F && e.peer.isControlDown() && !e.peer.isShiftDown()) {
				captureTA match {
					case Some(ta) =>
						val txt     = ta.endCapture().trim
						val wasBody = ta == body
						// Save capture bounds before look() changes the selection
						val capStart = body.peer.getSelectionStart
						val capEnd   = body.peer.getSelectionEnd
						captureTA = None
						if(txt.nonEmpty) {
							val found = look(txt, !wasBody)
							// Delete by position — replaceSelection() would delete whatever look() selected
							if(found && wasBody) body.peer.getDocument.remove(capStart, capEnd - capStart)
						}
					case None =>
						val activeTA = if(e.source.hashCode == tag.hashCode) tag else body
						val sel = Option(activeTA.selected).getOrElse("").trim
						if(sel.nonEmpty) look(sel, activeTA == tag)
				}
			}
			if(e.key == Key.Escape) cancelCapture()

			if(e.key == Key.Enter && !e.peer.isControlDown() && indInteractive && cmdProcess.isDefined) {
				val curPrompt = rePrompt
				body.line(body.currLineNo - 1) match {
					case curPrompt(cmd) =>
						cmdProcessWriter.foreach { w => w.write(cmd); w.newLine(); w.flush() }
					case _ => /* Do nothing, if not a valid prompt and command */
				}
			} else if(e.key == Key.Enter && !e.peer.isControlDown() && indIndent) {
				val p = body.line(body.currLineNo - 1)
				p match {
					case ZWnd.reWhiteSpace(spc) => body.selected = spc
					case _ => /* Do Nothing  */
				}

				if(p.trim().isEmpty)  body.lineSet(body.currLineNo -1, "")
			}

			publish(new ZStatusEvent(this, statusProperties))
	}

	listenTo(tag.mouse.clicks, body.mouse.clicks)
	reactions += {
		case e : MouseClicked =>
			if(SwingUtilities.isRightMouseButton(e.peer)) {
				try {
					e.source match {
						case ta: ZTextArea =>
							val txt = ZUtilities.selectedText(ta, e)
							if(!look(txt, ta == tag)) command(txt)
						case _ =>
					}
				} catch {
					case e : Throwable => JOptionPane.showMessageDialog(null, e.getMessage, "Look Error", JOptionPane.ERROR_MESSAGE)
				}
			}

			publish(new ZStatusEvent(this, statusProperties))
	}

	listenTo(body)
	reactions += {
		case e : ZCleanTextEvent => dirty = false
		case e : ZDirtyTextEvent => dirty = true
	}

	def command(cmds: String): Unit = if (cmds != null && !cmds.trim.isEmpty) {
		for (cmd <- cmds.linesIterator.map(_.trim)) {
			cmd match {
				case ZWnd.reExplicitCmd(c) => command(c)
				case c =>
					if (!handleFileCmd(c) && !handleDisplayCmd(c) && !handleEditCmd(c) &&
					    !handleProcessCmd(c) && !handleLspCmd(c) && !handleColorCmd(c) &&
					    !handleScriptCmd(c))
						publish(new ZCmdEvent(this, c))
			}
			val ts = CommandLog.record("wnd", path, cmd)
			publish(new ZCmdEchoEvent(ts, "wnd", path, cmd))
		}
	}

	private def handleFileCmd(cmd: String): Boolean = cmd match {
		case "Get" =>
			get(if (ZWnd.isScratchBuffer(tag.text)) "" else path)
			dirty = false
			true
		case ZWnd.reQuotedGet(f)       => get(f); true
		case ZWnd.reGet(f)             => get(f); true
		case "Put" =>
			put(if (ZWnd.isScratchBuffer(tag.text)) "" else path)
			dirty = false
			true
		case ZWnd.reQuotedPut(f)       => put(f); true
		case ZWnd.rePut(f)             => put(f); true
		case "Dirty" | "Clean"         => dirty = !dirty; true
		case ZUtilities.reDirQuoted(d) => applyDir(d); true
		case ZUtilities.reDir(d)       => applyDir(d); true
		case "NewZ" =>
			val f = new java.io.File(path)
			val dir = if (f.isDirectory) f else f.getParentFile
			if (dir != null && dir.exists()) ZUtilities.spawnZ(dir)
			true
		case ZWnd.reNewZQuoted(p) => ZUtilities.spawnZFromPath(p, root); true
		case ZWnd.reNewZ(p)       => ZUtilities.spawnZFromPath(p, root); true
		case _                         => false
	}

	private def handleDisplayCmd(cmd: String): Boolean = cmd match {
		case "Scroll"  => scroll = !scroll; true
		case "Wrap"    => body.lineWrap = !body.lineWrap; true
		case "CLine"   => body.clineHighlight = !body.clineHighlight; true
		case "Ln"      =>
			indLineNums = !indLineNums
			bodyScroll.setLineNumbersEnabled(indLineNums)
			styleGutter()
			true
		case "Hilite"  =>
			val first = !indHilite
			indHilite = true; indHiliteOff = false
			body.hilite(ZLangRegistry.forPath(path))
			if (first) ZTheme(indTheme, body)
			true
		case ZWnd.reHilite("off")           =>
			indHilite = false; indHiliteOff = true
			body.hilite(SyntaxConstants.SYNTAX_STYLE_NONE)
			true
		case ZWnd.reHilite(lang)            =>
			val first = !indHilite
			indHilite = true; indHiliteOff = false
			body.hilite(ZLangRegistry.forLang(lang))
			if (first) ZTheme(indTheme, body)
			true
		case ZWnd.reTheme(theme)            => indTheme = theme; ZTheme(theme, body); true
		case "Indent"                       => indIndent = !indIndent; true
		case "Bind"                         => indBind = !indBind; true
		case ZUtilities.reFont(font, pt)    =>
			fontVar = new Font(font, Font.PLAIN, pt.toInt)
			body.font = fontVar
			styleGutter()
			true
		case ZUtilities.reFONT(font, pt)    =>
			fontFixed = new Font(font, Font.PLAIN, pt.toInt)
			body.font = fontFixed
			styleGutter()
			true
		case ZUtilities.reTagFont(font, pt) => tag.font = new Font(font, Font.PLAIN, pt.toInt); true
		case "Font"                         => body.font = fontVar; styleGutter(); true
		case "FONT"                         => body.font = fontFixed; styleGutter(); true
		case _                              => false
	}

	private def handleEditCmd(cmd: String): Boolean = cmd match {
		case "Cut"         => body.cut(); true
		case "Paste"       => body.paste(); true
		case "Snarf"       => body.copy(); true
		case "Redo"        => body.redo(); true
		case "Undo"        => body.undo(); true
		case "Clear"       => body.text = ""; true
		case ZWnd.reTab(t) => if (t != null && !t.isEmpty) body.tabSize = t.toInt; true
		case _             => false
	}

	private def handleProcessCmd(cmd: String): Boolean = cmd match {
		case "Input"                   => indInteractive = !indInteractive; true
		case ZWnd.reInput(prompt)      => rePrompt = prompt.r; true
		case "Kill"                    =>
			cmdProcess.foreach { p =>
				cmdProcessWriter.foreach(_.close())
				p.destroy()
				tag.text = tag.text.replaceAll(ZWnd.CmdExecIndicator, "")
			}
			cmdProcess = None
			cmdProcessWriter = None
			true
		case ZWnd.reExternalCmd(op, c) =>
			cmdProcess.foreach(_.destroy())
			cmdProcess = externalCmd(op, c)
			cmdProcessWriter = cmdProcess.map(p => new BufferedWriter(new OutputStreamWriter(p.getOutputStream)))
			true
		case _                         => false
	}

	private def handleLspCmd(cmd: String): Boolean = cmd match {
		case "Lsp"             =>
			JOptionPane.showMessageDialog(null,
				"Lsp requires a project root path, e.g:  Lsp ~/myapp",
				"LSP Error", JOptionPane.ERROR_MESSAGE)
			true
		case ZWnd.reLsp("off") => lsp.stop(); true
		case ZWnd.reLsp(p)     =>
			val projRoot = ZUtilities.expandPath(p.trim, root)
			val langId   = ZLangRegistry.langIdFor(path)
			lsp.start(projRoot, langId)
			true
		case "Check"           => lsp.check(); true
		case "Complete"        => lsp.complete(); true
		case "Plumb"           => ZPlumbing.load(); true
		case _                 => false
	}

	private def tagContentHeight(): Int = {
		val insets = tag.peer.getInsets
		try {
			tag.peer.getUI.getRootView(tag.peer)
				.getPreferredSpan(javax.swing.text.View.Y_AXIS).toInt +
			insets.top + insets.bottom
		} catch { case _: Throwable =>
			tag.peer.getFontMetrics(tag.peer.getFont).getHeight + insets.top + insets.bottom
		}
	}

	private def adjustTagDivider(): Unit = SwingUtilities.invokeLater(() =>
		if (peer.getHeight > 0) peer.setDividerLocation(tagContentHeight())
	)

	private def applyColorComponent(t: String, r: String, g: String, b: String): Unit =
		if (t.startsWith("T")) {
			tagScheme = tagScheme.withComponent(t.drop(1), r.toInt, g.toInt, b.toInt)
			tagScheme.applyTo(tag)
			tagHandle.background = ZColors.handleFor(tagScheme.back)
		} else {
			bodyScheme = bodyScheme.withComponent(t, r.toInt, g.toInt, b.toInt)
			bodyScheme.applyTo(body)
			styleGutter()
		}

	private def handleColorCmd(cmd: String): Boolean = cmd match {
		case ZWnd.reColors(t, r, g, b)   => applyColorComponent(t, r, g, b); true
		case ZWnd.reColorAll(t, r, g, b) => applyColorComponent(t, r, g, b); true
		case _                            => false
	}

	private def handleScriptCmd(cmd: String): Boolean = cmd match {
		case ZScripts.reAnyScript(name, args) =>
			ZScripts.resolve(name, root) match {
				case Right(f)       => publish(new ZScriptEvent(this, f.getPath, args.trim))
				case Left(searched) => ZScripts.showError(name, searched)
			}
			true
		case _ => false
	}

	private def lookViaPlumbing(txt: String, fromTag: Boolean): Option[Boolean] = {
		val wd = cmdWorkDir
		ZPlumbing.plumb(PlumbMessage(txt, wd, src = path)) match {
			case Some(r) if r.port == PlumbPortExec =>
				publish(new ZPlumbExecEvent(this, r.cmd.getOrElse(""), wd)); Some(true)
			case Some(r) =>
				val target = r.message.data + r.message.attrs.get("addr").map(":" + _).getOrElse("")
				Some(look(target, fromTag))
			case None => None
		}
	}

	private def parseLookPath(txt: String): (String, String) = txt match {
		case ZWnd.reFilePath(f, l)        => (f, l)
		case ZWnd.reFilePath2(f, l)       => (f, l)
		case ZWnd.reQuotedFilePath(f, l)  => (f, l)
		case ZWnd.reQuotedFilePath2(f, l) => (f, l)
		case _                            => (txt, "")
	}

	private def lookViaFilePath(stxt: String, loc: String, fromTag: Boolean): Option[Boolean] = {
		val sp      = ZUtilities.expandPath(stxt, root)
		val absPath = new File(path)
		val baseDir = if (absPath.isDirectory) path else absPath.getParent
		val rb      = ZPathResolver.resolveBase(rawPath, stxt, fromTag, tag.text, root, baseDir)
		val ep      = (if (ZUtilities.isFullPath(sp)) "" else (rb + ZUtilities.separator)) + sp
		if (new File(ep).exists) {
			val resolvedPath = if (!ZUtilities.isFullPath(rawPath) && ep.startsWith(root + ZUtilities.separator))
			                       ep.substring(root.length + 1)
			                   else ep
			if (indBind) { path = resolvedPath; command("Get"); if (loc.nonEmpty) look(loc) }
			else lookUpward(resolvedPath + loc)
			Some(true)
		} else None
	}

	// Catch-all: try txt as a regex in the body, then fall back to dispatching as a command.
	private def lookViaRegex(stxt: String): Boolean = {
		val pos  = body.caret.position
		val t    = body.text.substring(pos)
		val m    = Pattern.compile(stxt, Pattern.MULTILINE).matcher(t)
		if (m.find() && m.end() > m.start()) {
			body.caret.dot = pos + m.start()
			body.caret.moveDot(pos + m.end())
			body.requestFocus()
		} else {
			command(stxt)
		}
		true
	}

	def look(txt: String, fromTag: Boolean = false): Boolean = {
		if (txt == null || txt.trim.isEmpty) return true
		txt match {
			case ZWnd.reLineNo(no) =>
				val n = no.toInt
				if (n >= 1 && n <= body.lineCount) {
					body.caret.dot = body.lineStart(n - 1)
					body.caret.moveDot(body.lineEnd(n - 1) - 1)
					body.requestFocus()
					true
				} else false
			case ZWnd.reRegExp(re) =>
				val pos = body.caret.position
				val t   = body.text.substring(pos)
				val m   = ZWnd.compiledPattern(re).matcher(t)
				if (m.find()) {
					body.caret.dot = pos + m.start()
					body.caret.moveDot(pos + m.end())
					body.requestFocus()
					true
				} else false
			case _ =>
				val (stxt, loc) = parseLookPath(txt)
				lookViaPlumbing(txt, fromTag)
					.orElse(lookViaFilePath(stxt, loc, fromTag))
					.getOrElse(lookViaRegex(stxt))
		}
	}

	private def cmdWorkDir: String = {
		val f = new File(path)
		if (f.isDirectory) f.getCanonicalPath else f.getParentFile.getCanonicalPath
	}

	private def cmdEnv(extraEnv: Map[String, String] = Map.empty): HashMap[String, String] = {
		val localFp = path
		val f  = new File(localFp)
		val wd = if (f.isDirectory) f.getCanonicalPath else f.getParentFile.getCanonicalPath
		val sel = Option(body.selected).getOrElse("")
		(new HashMap[String, String] +
			("Z_FILE"      -> localFp) +
			("Z_FP"        -> f.getCanonicalPath) +
			("Z_DIR"       -> wd) +
			("Z_SELECTION" -> sel)) ++ extraEnv
	}

	private def cmdOnDone: () => Unit = () => SwingUtilities.invokeLater(() =>
		tag.text = tag.text.replaceAll(ZWnd.CmdExecIndicator, "")
	)

	def externalCmd(op: String, cmd: String, in: Option[String] = None, insertPos: Option[Int] = None): Option[Process] = {
		val resolved = cmd.trim match {
			case ZScripts.reScript(name, args) =>
				ZScripts.resolve(name, rootPath) match {
					case Right(f) => f.getPath + (if (args.trim.isEmpty) "" else " " + args.trim)
					case Left(searched) =>
						ZScripts.showError(name, searched)
						return None
				}
			case _ => cmd
		}
		val insertAt = if (op == "<") {
			if (insertPos.isDefined) {
				insertPos.map(new AtomicInteger(_))
			} else {
				val selStart = body.peer.getSelectionStart
				val selEnd   = body.peer.getSelectionEnd
				if (selStart != selEnd) {
					body.peer.replaceSelection("")
					Some(new AtomicInteger(selStart))
				} else {
					Some(new AtomicInteger(body.peer.getCaretPosition))
				}
			}
		} else {
			insertPos.map(new AtomicInteger(_))
		}
		val onOutput: String => Unit = s => SwingUtilities.invokeLater(() => {
			insertAt match {
				case Some(pos) =>
					try { body.peer.getDocument.insertString(pos.getAndAdd(s.length), s, null) }
					catch { case _: Throwable => body.append(s) }
				case None if !scroll => body.append(s)
				case None =>
					val current = body.caret.dot
					body.selected = s
					body.caret.dot = current + s.length
			}
		})

		val wd  = cmdWorkDir
		val env = cmdEnv()
		tag.text = tag.text + ZWnd.CmdExecIndicator
		try {
			op match {
				case "<" => ZUtilities.extCmd(resolved, onOutput, cmdOnDone, redirectErrStream = true, workdir = Some(wd), env = Some(env))
				case ">" => ZUtilities.extCmd(resolved, onOutput, cmdOnDone, redirectErrStream = true, input = in, workdir = Some(wd), env = Some(env))
				case "|" =>
					val selStart = body.peer.getSelectionStart
					val selEnd   = body.peer.getSelectionEnd
					val input = if (selStart != selEnd) {
						val sel = body.selected
						body.selected = ""
						sel
					} else {
						val content = body.text
						body.text = ""
						content
					}
					ZUtilities.extCmd(resolved, onOutput, cmdOnDone, redirectErrStream = true, input = Some(input), workdir = Some(wd), env = Some(env))
				case "!" =>
					body.text = ""
					ZUtilities.extCmd(resolved, onOutput, cmdOnDone, redirectErrStream = true, workdir = Some(wd), env = Some(env))
			}
		} catch {
			case e: Throwable =>
				JOptionPane.showMessageDialog(null, e.getMessage, "External Command Error", JOptionPane.ERROR_MESSAGE)
				tag.text = tag.text.replaceAll(ZWnd.CmdExecIndicator, "")
				None
		}
	}

	def runScript(scriptPath: String, args: String, extraEnv: Map[String, String] = Map.empty): Option[Process] = {
		val cmd = if (args.isEmpty) scriptPath else s"$scriptPath $args"
		val wd  = cmdWorkDir
		val env = cmdEnv(extraEnv)
		val onOutput: String => Unit = s => SwingUtilities.invokeLater(() => {
			val current = body.caret.dot
			body.selected = s
			body.caret.dot = current + s.length
		})
		tag.text = tag.text + ZWnd.CmdExecIndicator
		try {
			ZUtilities.extCmd(cmd, onOutput, cmdOnDone, redirectErrStream = true, workdir = Some(wd), env = Some(env))
		} catch {
			case e: Throwable =>
				JOptionPane.showMessageDialog(null, e.getMessage, "Script Error", JOptionPane.ERROR_MESSAGE)
				tag.text = tag.text.replaceAll(ZWnd.CmdExecIndicator, "")
				None
		}
	}

	private def applyDir(d: String): Unit = {
		val ed = ZUtilities.expandPath(d, root)
		val f  = new File(if(ZUtilities.isFullPath(ed)) ed else root + File.separator + ed)
		if(f.isDirectory) {
			val newRoot = f.getCanonicalPath
			if(newRoot != root) {
				val rp = tag.text match {
					case ZWnd.reQuotedPath(_, p) => p
					case ZWnd.rePath(_, p)       => p
					case _                       => ""
				}
				if(rp.nonEmpty && !ZUtilities.isFullPath(ZUtilities.expandPath(rp, root)))
					dirty = true
				root = newRoot
			}
		}
		else JOptionPane.showMessageDialog(null, s"Dir: not a directory: $d", "Dir Error", JOptionPane.ERROR_MESSAGE)
	}

	// Called by ZCol.closeWnd to clean up LSP before removing the window.
	def close(): Unit = lsp.close()

	def root = rootPath
	def root_=(s : String) = { rootPath = new File(s).getCanonicalPath }

	private def resolvePath(p: String): String = ZPathResolver.resolvePath(p, root)

	def path = tag.text match {
		case ZWnd.reQuotedScratch(_, p) => resolvePath(p)
		case ZWnd.reScratch(_, p)       => resolvePath(p)
		case ZWnd.reQuotedPath(_, p)    => resolvePath(p)
		case ZWnd.rePath(_, p)          => resolvePath(p)
		case _                          => new File(root).getCanonicalPath
	}

	def path_=(p: String) = {
		val old = rawPath
		tag.text = tag.text.replace(old, p)
		if (rawPath != old) publish(new ZPathChangedEvent(this, old, rawPath))
	}

	def rawPath = tag.text match {
		case ZWnd.reQuotedPath(dirty, p) => p
		case ZWnd.rePath(dirty, p)       => p
		case _                           => "+"
	}

	def dirty = tag.text match {
		case ZWnd.reRawTagLine(dirty, line) => dirty.equals("*")
		case _ => false
	}

	def dirty_=(b : Boolean) = tag.text match {
		case ZWnd.reRawTagLine(dirty, line) => if(dirty.equals("*") && !b)  tag.text = line
			else if(!dirty.equals("*") && b) tag.text = "* " + tag.text
		case _ => tag.text = "* " + tag.text
	}

	def clean = dirty == false

	def put(f: String): Boolean =
		f != null && !f.trim.isEmpty && !new File(f).isDirectory && !ZWnd.isScratchBuffer(f) && {
			ZFileIO.writeFile(f, body.text) match {
				case Right(_)    => true
				case Left(msg)   =>
					JOptionPane.showMessageDialog(null, msg, "Put Error", JOptionPane.ERROR_MESSAGE)
					false
			}
		}

	def get(f: String = path): Boolean = f != null && !f.trim.isEmpty && {
		val ef = ZUtilities.expandPath(f, root)
		val o  = new File(if (ef.startsWith(ZUtilities.separator)) ef else root + File.separator + ef)
		val result = if (o.isDirectory) ZFileIO.readDir(o.getPath) else ZFileIO.readFile(f)
		result match {
			case Right(content) =>
				if (!o.isDirectory) {
					val newPath = o.getCanonicalPath
					if (lsp.enabled && newPath != path) {
						lsp.updatePath(newPath)
						lsp.didOpen(content)
					}
				}
				body.text = content
				body.caret.position = 0
				val style = ZLangRegistry.forPath(f)
				if (!indHilite && !indHiliteOff && style != SyntaxConstants.SYNTAX_STYLE_NONE) {
					indHilite = true
					body.hilite(style)
					ZTheme(indTheme, body)
				} else if (indHilite) {
					body.hilite(style)
					ZTheme(indTheme, body)
				}
				if (!body.lineWrap && ZLangRegistry.autoWrap(f)) {
					body.lineWrap = true
				}
				true
			case Left(msg) =>
				JOptionPane.showMessageDialog(null, s"$f $msg", "Get Error", JOptionPane.ERROR_MESSAGE)
				false
		}
	}

	def scroll = indScroll
	def scroll_=(b : Boolean) = {
		body.peer.getCaret match {
			case dc: DefaultCaret =>
				dc.setUpdatePolicy(if(b) DefaultCaret.ALWAYS_UPDATE else DefaultCaret.UPDATE_WHEN_ON_EDT)
			case _ =>
		}
		indScroll = b
	}

	private def styleGutter(): Unit = {
		val g = bodyScroll.getGutter
		g.setBackground(new Color(
			math.max(0, bodyScheme.back.getRed   - 15),
			math.max(0, bodyScheme.back.getGreen - 15),
			math.max(0, bodyScheme.back.getBlue  - 15)
		))
		g.setLineNumberColor(new Color(
			bodyScheme.fore.getRed   / 2 + bodyScheme.back.getRed   / 2,
			bodyScheme.fore.getGreen / 2 + bodyScheme.back.getGreen / 2,
			bodyScheme.fore.getBlue  / 2 + bodyScheme.back.getBlue  / 2
		))
		g.setLineNumberFont(body.peer.getFont)
		g.setBorderColor(bodyScheme.back)
	}

	// Hot path — emitted on every keystroke via ZStatusEvent.
	def statusProperties: Map[String, String] = {
		val (lineNo, col) = body.currLineAndColumn
		Map(
		"line.current"            -> (lineNo + 1).toString,
		"lines"                   -> body.lineCount.toString,
		"column.current"          -> col.toString,
		"tab.size"                -> body.tabSize.toString,
		"line.wrap"               -> body.lineWrap.toString,
		"indent.auto"             -> indIndent.toString,
		"scroll"                  -> scroll.toString,
		"body.font.current"       -> body.font.getFontName,
		"body.font.current.size"  -> body.font.getSize.toString,
		"bind"                    -> indBind.toString,
		"lsp"                     -> lsp.enabled.toString,
		"lsp.root"                -> lsp.root,
		"lsp.status"              -> lsp.status,
		"lsp.indexing"            -> lsp.indexing.toString,
		"hilite"                  -> indHilite.toString,
		"hilite.explicit.off"     -> indHiliteOff.toString,
		"theme"                   -> indTheme,
		"interactive"             -> indInteractive.toString,
		"interactive.prompt"      -> rePrompt.pattern.pattern(),
		)
	}

	def properties: Map[String, String] = statusProperties ++ Map(
		"path"                    -> path,
		"path.root"               -> root,
		"path.rawpath"            -> rawPath,
		"dirty"                   -> dirty.toString,
		"selection.start"         -> body.selectionStart.toString,
		"selection.end"           -> body.selectionEnd.toString,
		"line.numbers"            -> indLineNums.toString,
		"body.color.back"         -> bodyScheme.back.getRGB.toString,
		"body.color.fore"         -> bodyScheme.fore.getRGB.toString,
		"body.color.caret"        -> bodyScheme.caret.getRGB.toString,
		"body.color.selback"      -> bodyScheme.selBack.getRGB.toString,
		"body.color.selfore"      -> bodyScheme.selFore.getRGB.toString,
		"body.font.fixed"         -> fontFixed.getFontName,
		"body.font.fixed.size"    -> fontFixed.getSize.toString,
		"body.font.variable"      -> fontVar.getFontName,
		"body.font.variable.size" -> fontVar.getSize.toString,
		"tag.color.back"          -> tagScheme.back.getRGB.toString,
		"tag.color.fore"          -> tagScheme.fore.getRGB.toString,
		"tag.color.caret"         -> tagScheme.caret.getRGB.toString,
		"tag.color.selback"       -> tagScheme.selBack.getRGB.toString,
		"tag.color.selfore"       -> tagScheme.selFore.getRGB.toString,
		"tag.font"                -> tag.font.getFontName,
		"tag.size"                -> tag.font.getSize.toString,
	)

	def dump: Map[String, String] = {
		val tagDiv = peer.getDividerLocation
		properties ++ Map(
			"body.text" -> (if(dirty || ZWnd.isScratchBuffer(tag.text)) body.text else ""),
			"tag.text"  -> tag.text,
		) ++ (if (tagDiv > 0) Map("tag.divider" -> tagDiv.toString) else Map.empty)
	}

	def load(p: Map[String, String], prefix : String = "") = {
		def int(key: String, default: Int): Int =
			p.get(prefix + key).flatMap(_.toIntOption).getOrElse(default)

		path = p.getOrElse(prefix + "path", "+")
		root = p.getOrElse(prefix + "path.root", ".")
		body.tabSize = int("tab.size", 4)
		body.lineWrap = p.getOrElse(prefix + "line.wrap", "false") == "true"

		fontFixed = new Font(p.getOrElse(prefix + "body.font.fixed", fontFixed.getFontName), Font.PLAIN, int("body.font.fixed.size", fontFixed.getSize))
		fontVar = new Font(p.getOrElse(prefix + "body.font.variable", fontVar.getFontName), Font.PLAIN, int("body.font.variable.size", fontVar.getSize))
		body.font = new Font(p.getOrElse(prefix + "body.font.current", body.font.getFontName), Font.PLAIN, int("body.font.current.size", body.font.getSize))

		bodyScheme = ZColorScheme(
			new Color(int("body.color.back",    bodyScheme.back.getRGB)),
			new Color(int("body.color.fore",    bodyScheme.fore.getRGB)),
			new Color(int("body.color.caret",   bodyScheme.caret.getRGB)),
			new Color(int("body.color.selback", bodyScheme.selBack.getRGB)),
			new Color(int("body.color.selfore", bodyScheme.selFore.getRGB)))
		bodyScheme.applyTo(body)

		tag.font = new Font(p.getOrElse(prefix + "tag.font", body.font.getFontName), Font.PLAIN, int("tag.size", body.font.getSize))
		tagScheme = ZColorScheme(
			new Color(int("tag.color.back",    tagScheme.back.getRGB)),
			new Color(int("tag.color.fore",    tagScheme.fore.getRGB)),
			new Color(int("tag.color.caret",   tagScheme.caret.getRGB)),
			new Color(int("tag.color.selback", tagScheme.selBack.getRGB)),
			new Color(int("tag.color.selfore", tagScheme.selFore.getRGB)))
		tagScheme.applyTo(tag)
		tagHandle.background = ZColors.handleFor(tagScheme.back)
		tag.text = p.getOrElse(prefix + "tag.text", "+ " + ZCol.wndTagLine.trim)

		indIndent      = p.getOrElse(prefix + "indent.auto",  "false") == "true"
		indInteractive = p.getOrElse(prefix + "interactive",  "false") == "true"
		indBind        = p.getOrElse(prefix + "bind",         "false") == "true"
		dirty          = p.getOrElse(prefix + "dirty",        "false") == "true"
		scroll         = p.getOrElse(prefix + "scroll",       "false") == "true"

		indHilite    = p.getOrElse(prefix + "hilite",             "false") == "true"
		indHiliteOff = p.getOrElse(prefix + "hilite.explicit.off", "false") == "true"
		indTheme     = p.getOrElse(prefix + "theme",               "z")
		indLineNums  = p.getOrElse(prefix + "line.numbers",        "false") == "true"
		if(indLineNums) bodyScroll.setLineNumbersEnabled(true)
		styleGutter()
		// indHilite must be set before Get so the highlighting is applied when the file loads.
		if(!dirty)  command("Get") else  body.text = p.getOrElse(prefix + "body.text", "")

		if(body.lineCount > 0) {
			body.selectionStart = int("selection.start", 0)
			body.selectionEnd   = int("selection.end",   0)
		}
		val tagDiv = int("tag.divider", 0)
		if (tagDiv > 0) SwingUtilities.invokeLater(() => peer.setDividerLocation(tagDiv))
	}
}

object ZWnd {
	private val patternCache = collection.mutable.LinkedHashMap.empty[String, Pattern]
	private val PatternCacheMax = 32

	def compiledPattern(re: String): Pattern =
		patternCache.getOrElse(re, {
			val p = Pattern.compile(re, Pattern.MULTILINE)
			if (patternCache.size >= PatternCacheMax) patternCache.remove(patternCache.keys.head)
			patternCache(re) = p
			p
		})

	val defaultPromptRegex = """[^\$%>\?#]*[\$%>\?#]\s*(.+)\s*""".r
	val reInput = """Input\s+(.+)""".r

	val rePre = """.*?(\S*)$""".r
	val rePath = """(?s)\s*(\*?)\s*(\S+).*""".r
	val reQuotedPath = """(?s)\s*(\*?)\s*'\s*([^']*)'.*$""".r
	val reScratch = """^(?s)\s*(\*?)\s*([^+\s]*)[+].*$""".r
	val reQuotedScratch = """^(?s)\s*(\*?)\s*'([^'+]*)[+].*'.*$""".r
	val reRawTagLine = """(?s)\s*(\*?)\s*(.*)""".r

	val reTab = """Tab\s+([0-9]+)""".r
	val reQuotedGet = """Get\s+'(.+)'""".r
	val reGet = """Get\s+(\S+)""".r
	val reNewZQuoted = """NewZ\s+'(.+)'""".r
	val reNewZ       = """NewZ\s+(\S+)""".r
	val reQuotedPut = """Put\s+'(.+)'.*""".r
	val rePut = """Put\s+(\S+).*""".r
	val reLineNo = """^:([0-9]+)$""".r
	val reRegExp = """^:/(.+)$""".r
	val reFilePath = """(.+)(:[0-9]+)$""".r
	val reFilePath2 = """(.+)(:/.+)$""".r
	val reQuotedFilePath = """'(.+)'(:[0-9]+)$""".r
	val reQuotedFilePath2 = """'(.+)'(:/.+)$""".r
	val reExternalCmd = """(?s)([\|<!])\s*(.+)\s*$""".r
	val reWhiteSpace = """(?s)^(\s+).*$""".r

	val reExplicitCmd = """%\s*(.+)$""".r

	val reHilite = """Hilite\s+(\S+)""".r
	val reLsp    = """Lsp\s+(.+)""".r
	val reTheme  = """Theme\s+(\S+)""".r
	val reColors    = """Color(TBack|TFore|TCaret|TSelFore|TSelBack|Back|Fore|Caret|SelFore|SelBack)\s+(\d{1,3})\s+(\d{1,3})\s+(\d{1,3})""".r
	val reColorAll  = """ColorAll(TBack|TFore|TCaret|TSelFore|TSelBack|Back|Fore|Caret|SelFore|SelBack)\s+(\d{1,3})\s+(\d{1,3})\s+(\d{1,3})""".r

	val CmdExecIndicator = " <!> "

	def isScratchBuffer(p: String) = p match {
		case reScratch(i, p) => true
		case reQuotedScratch(i, p) => true
		case _ => false
	}
}

