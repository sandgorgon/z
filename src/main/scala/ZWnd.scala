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

import util.Properties
import collection.immutable.HashMap
import io.Source
import swing.{SplitPane, ScrollPane, Orientation, FileChooser}
import swing.event.{KeyPressed, KeyReleased, Key, MouseClicked, MouseEntered, MousePressed, MouseDragged, MouseReleased, Event}

import java.io.{FileWriter, File, BufferedWriter, OutputStreamWriter}
import java.awt.{Font, Color}
import java.awt.ComponentOrientation.RIGHT_TO_LEFT
import javax.swing.text.{Utilities, DefaultCaret}
import javax.swing.{JOptionPane, ScrollPaneConstants, SwingUtilities}
import java.util.regex.Pattern
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.eclipse.lsp4j.{Diagnostic, DiagnosticSeverity, CompletionItem}

class ZWnd(initTagText : String, initBodyText : String = "", currDir : String = ".") extends SplitPane(Orientation.Horizontal) {
	var rootPath = new File(currDir).getAbsolutePath
	var indIndent = false
	var indScroll = true
	var indInteractive = false
	var indBind = false
	var indHilite   = false
	var indLsp      = false
	var indLineNums = false

	var lspClient: Option[ZLspClient] = None
	val lspParser = new ZLspDiagnosticParser
	val hoverTimer     = new javax.swing.Timer(500, null)
	val didChangeTimer = new javax.swing.Timer(400, null)
	var cmdProcess : Option[Process] = None
	var cmdProcessWriter : Option[BufferedWriter] = None
	var dragSel = false
	var dragSelMark = -1

	var colorBack =new Color(0xFF, 0xFF, 0xE0)
	var colorFore = new Color(0x00, 0x00, 0x00)
	var colorCaret = new Color(0x00, 0x00, 0x00)
	var colorSelBack = new Color(0xC8, 0x75, 0x9F)
	var colorSelFore = new Color(0xFF, 0xFF, 0xFF)

	var colorTBack = new Color(0x4A, 0x61, 0x95)
	var colorTFore = new Color(0xFF, 0xFF, 0xFF)
	var colorTCaret = new Color(0xC7, 0xC7, 0xC7)
	var colorTSelBack = new Color(0xFF, 0xFF, 0xFF)   // white — inverted from tag blue
	var colorTSelFore = new Color(0x4A, 0x61, 0x95)   // tag blue — inverted from tag white

	val tag = new ZTextArea(initTagText, true)
	tag.font = ZFonts.defaultTag
	tag.colors(colorTBack, colorTFore,  colorTCaret, colorTSelBack, colorTSelFore )
	tag.rows = 1

	val body = new ZTextArea(initBodyText)
	body.colors(colorBack, colorFore, colorCaret, colorSelBack, colorSelFore)

	// LSP parser (squiggly underlines) — registered even before Lsp is enabled
	body.peer.addParser(lspParser)

	var hoverPoint: java.awt.Point = new java.awt.Point(0, 0)

	hoverTimer.addActionListener(_ => {
		lspClient.foreach { c =>
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

	didChangeTimer.addActionListener(_ => lspClient.foreach(_.didChange(body.text)))
	didChangeTimer.setRepeats(false)

	body.peer.addMouseMotionListener(new java.awt.event.MouseMotionAdapter {
		override def mouseMoved(e: java.awt.event.MouseEvent): Unit = {
			if (indLsp) { hoverPoint = e.getPoint; hoverTimer.restart() }
		}
	})

	body.peer.getDocument.addDocumentListener(new javax.swing.event.DocumentListener {
		def insertUpdate(e: javax.swing.event.DocumentEvent): Unit  = if (indLsp) didChangeTimer.restart()
		def removeUpdate(e: javax.swing.event.DocumentEvent): Unit  = if (indLsp) didChangeTimer.restart()
		def changedUpdate(e: javax.swing.event.DocumentEvent): Unit = {}
	})

	var fontVar   = ZFonts.defaultVar
	var fontFixed = ZFonts.defaultFixed
	body.font = fontFixed
	
	dividerSize = 2
	topComponent =new ScrollPane(tag) {
			peer.setComponentOrientation(RIGHT_TO_LEFT)
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

	listenTo(tag.mouse.moves, body.mouse.moves)
	reactions += {
		case e : MouseEntered => publish(new ZStatusEvent(this, properties))
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
						else if(SwingUtilities.isRightMouseButton(e.peer))  look(ZUtilities.selectedText(ta, e))
					case _ =>
				}
			}
			dragSel = false
			dragSelMark = -1
	}

	listenTo(tag.keys, body.keys)
	reactions += {
		case e : KeyPressed if e.key == Key.Space && e.peer.isControlDown() =>
			command("Complete")
		case e : KeyPressed if((e.key == Key.F) && e.peer.isControlDown()) =>
			val ta = if(e.source.hashCode == tag.hashCode) tag else body
			var p = path

			if(ta.selected != null && !ta.selected.trim.isEmpty) {
				val sel = ta.selected.trim
				if(!(new File(sel)).isAbsolute)
				{
					p = p + File.separator + sel
				}
				else p = sel
			}

			val fc = new FileChooser(new File(p)) {
				title = "Path Selection"
				fileHidingEnabled = false
				multiSelectionEnabled = false
				fileSelectionMode = FileChooser.SelectionMode.FilesAndDirectories
			}

			if(fc.showOpenDialog(this) == FileChooser.Result.Approve)  {
				var fcp = fc.selectedFile.getPath
				val cp = new File(path).getCanonicalPath

				if(fcp.startsWith(cp) && fcp.length != cp.length)  fcp = fcp.substring(cp.length + 1).trim
				ta.selected = fcp
			}

		case e : KeyReleased =>			
			if(e.key == Key.Enter && indInteractive && cmdProcess.isDefined) {
				body.line(body.currLineNo - 1) match {
					case ZWnd.rePrompt(cmd) =>
						cmdProcessWriter.foreach { w => w.write(cmd); w.newLine(); w.flush() }
					case _ => /* Do nothing, if not a valid prompt and command */
				}
			} else if(e.key == Key.Enter && indIndent) {
				val p = body.line(body.currLineNo - 1)
				p match {
					case ZWnd.reWhiteSpace(spc) => body.selected = spc
					case _ => /* Do Nothing  */
				}
					
				if(p.trim().isEmpty)  body.lineSet(body.currLineNo -1, "")
			}

			publish(new ZStatusEvent(this, properties))
	}

	listenTo(tag.mouse.clicks, body.mouse.clicks)
	reactions += {
		case e : MouseClicked =>
			if(SwingUtilities.isRightMouseButton(e.peer)) {
				try {
					e.source match {
						case ta: ZTextArea =>
							val txt = ZUtilities.selectedText(ta, e)
							if(!look(txt)) command(txt)
						case _ =>
					}
				} catch {
					case e : Throwable => JOptionPane.showMessageDialog(null, e.getMessage, "Look Error", JOptionPane.ERROR_MESSAGE)
				}
			}

			publish(new ZStatusEvent(this, properties))
	}

	listenTo(body)
	reactions += {
		case e : ZCleanTextEvent => dirty = false
		case e : ZDirtyTextEvent => dirty = true
	}

	def command(cmds : String) : Unit = if(cmds != null && !cmds.trim.isEmpty) {
		for(cmd <- cmds.linesIterator.map(_.trim)) {
			cmd match {
				case ZWnd.reExplicitCmd(c)      => command(c)
				case "Get"                       =>
					get(if(ZWnd.isScratchBuffer(tag.text)) "" else path)
					dirty = false
				case ZWnd.reQuotedGet(f)         => get(f)
				case ZWnd.reGet(f)               => get(f)
				case "Put"                       =>
					put(if(ZWnd.isScratchBuffer(tag.text)) "" else path)
					dirty = false
				case ZWnd.reQuotedPut(f)         => put(f)
				case ZWnd.rePut(f)               => put(f)
				case "Dirty" | "Clean"           => dirty = !dirty
				case "Scroll"                    => scroll = !scroll
				case "Cut"                       => body.cut()
				case "Paste"                     => body.paste()
				case "Snarf"                     => body.copy()
				case "Redo"                      => body.redo()
				case "Undo"                      => body.undo()
				case "Wrap"                      => body.lineWrap = !body.lineWrap
				case "CLine"                     => body.clineHighlight = !body.clineHighlight
				case "Ln"                        =>
					indLineNums = !indLineNums
					bodyScroll.setLineNumbersEnabled(indLineNums)
					styleGutter()
				case "Hilite"                    =>
					val first = !indHilite
					indHilite = true
					body.hilite(ZLangRegistry.forPath(path))
					if(first) ZTheme("z", body)
				case ZWnd.reHilite("off")        =>
					indHilite = false
					body.hilite(SyntaxConstants.SYNTAX_STYLE_NONE)
				case ZWnd.reHilite(lang)         =>
					val first = !indHilite
					indHilite = true
					body.hilite(ZLangRegistry.forLang(lang))
					if(first) ZTheme("z", body)
				case ZWnd.reTheme(theme)         => ZTheme(theme, body)
				case "Indent"                    => indIndent = !indIndent
				case "Clear"                     => body.text = ""
				case "Bind"                      => indBind = !indBind
				case ZUtilities.reDirQuoted(d)         => applyDir(d)
				case ZUtilities.reDir(d)               => applyDir(d)
				case ZWnd.reTab(t)               => if(t != null && !t.isEmpty) body.tabSize = t.toInt
				case ZUtilities.reFont(font, pt)       =>
					fontVar = new Font(font, Font.PLAIN, pt.toInt)
					body.font = fontVar
					styleGutter()
				case ZUtilities.reFONT(font, pt)       =>
					fontFixed = new Font(font, Font.PLAIN, pt.toInt)
					body.font = fontFixed
					styleGutter()
				case ZUtilities.reTagFont(font, pt)    => tag.font = new Font(font, Font.PLAIN, pt.toInt)
				case "Font"                      => body.font = fontVar; styleGutter()
				case "FONT"                      => body.font = fontFixed; styleGutter()
				case "Input"                     => indInteractive = !indInteractive
				case ZWnd.reInput(prompt)        => ZWnd.rePrompt = prompt.r
				case "Kill"                      =>
					cmdProcess.foreach { p =>
						cmdProcessWriter.foreach(_.close())
						p.destroy()
						tag.text = tag.text.replaceAll(ZWnd.CmdExecIndicator, "")
					}
					cmdProcess = None
					cmdProcessWriter = None
				case ZWnd.reExternalCmd(op, c)   =>
					cmdProcess.foreach(_.destroy())
					cmdProcess = externalCmd(op, c)
					cmdProcessWriter = cmdProcess.map(p => new BufferedWriter(new OutputStreamWriter(p.getOutputStream)))
				case ZWnd.reColors(t, r, g, b)   =>
					if(t.equals("TBack"))    colorTBack    = applyColor(colorTBack,    (r.toInt, g.toInt, b.toInt))
					if(t.equals("TFore"))    colorTFore    = applyColor(colorTFore,    (r.toInt, g.toInt, b.toInt))
					if(t.equals("TCaret"))   colorTCaret   = applyColor(colorTCaret,   (r.toInt, g.toInt, b.toInt))
					if(t.equals("TSelBack")) colorTSelBack = applyColor(colorTSelBack, (r.toInt, g.toInt, b.toInt))
					if(t.equals("TSelFore")) colorTSelFore = applyColor(colorTSelFore, (r.toInt, g.toInt, b.toInt))
					if(t.equals("Back"))     colorBack     = applyColor(colorBack,     (r.toInt, g.toInt, b.toInt))
					if(t.equals("Fore"))     colorFore     = applyColor(colorFore,     (r.toInt, g.toInt, b.toInt))
					if(t.equals("Caret"))    colorCaret    = applyColor(colorCaret,    (r.toInt, g.toInt, b.toInt))
					if(t.equals("SelBack"))  colorSelBack  = applyColor(colorSelBack,  (r.toInt, g.toInt, b.toInt))
					if(t.equals("SelFore"))  colorSelFore  = applyColor(colorSelFore,  (r.toInt, g.toInt, b.toInt))
					if(t.startsWith("T")) tag.colors(colorTBack, colorTFore, colorTCaret, colorTSelBack, colorTSelFore)
					else { body.colors(colorBack, colorFore, colorCaret, colorSelBack, colorSelFore); styleGutter() }
				case "Lsp"                       =>
					JOptionPane.showMessageDialog(null,
						"Lsp requires a project root path, e.g:  Lsp ~/myapp",
						"LSP Error", JOptionPane.ERROR_MESSAGE)
				case ZWnd.reLsp("off")           =>
					lspClient.foreach { c =>
						c.didClose()
						c.shutdown()
						ZLspManager.unregister(c)
					}
					lspClient = None
					indLsp = false
					lspParser.clearDiagnostics()
					body.peer.forceReparsing(0)
				case ZWnd.reLsp(p)               =>
					if (!indLsp) {
						val projRoot = ZUtilities.expandPath(p.trim, root)
						val langId   = ZLangRegistry.langIdFor(path)
						ZLspManager.serverCmd(langId).foreach { cmd =>
							val uri    = s"file://${new File(projRoot).getCanonicalPath}/"
							val client = new ZLspClient(langId, cmd, path, uri, onDiagnostics)
							client.start(() => javax.swing.SwingUtilities.invokeLater(() => {
								client.didOpen(body.text)
								command("Check")
							}))
							lspClient = Some(client)
							ZLspManager.register(client)
							indLsp = true
						}
					}
				case "Check"                     =>
					lspClient.foreach(_.didChange(body.text))
				case "Complete"                  =>
					lspClient.foreach { c =>
						val dot  = body.caret.dot
						val line = body.lineNo(dot)
						val col  = dot - body.lineStart(line)
						c.completion(line, col, items =>
							javax.swing.SwingUtilities.invokeLater(() => showCompletionPopup(items, dot))
						)
					}
				case _                           => publish(new ZCmdEvent(this, cmd))
			}
		}
	}

	def look(txt: String) : Boolean  = {
		if(txt == null || txt.trim.isEmpty)  return true

		var stxt = ""
		var loc = ""

		txt match {
			case ZWnd.reLineNo(no) =>
				var i = no.toInt
				if(i >= 1 && i <= body.lineCount) {
					i = i - 1
					body.caret.dot = body.lineStart(i)
					body.caret.moveDot(body.lineEnd(i)-1)
					body.requestFocus()
					return true
				}
				return false
			case ZWnd.reRegExp(re) => 
				stxt = re

				var pos = body.caret.position
				var t = body.text.substring(pos)
				var p = Pattern.compile(stxt, Pattern.MULTILINE)
				var m = p.matcher(t)

				if(m.find())  {
					body.caret.dot = pos + m.start()
					body.caret.moveDot(pos + m.end())
					body.requestFocus()
					return true
				}

				return false
			case ZWnd.reFilePath(f, l) => 
				stxt = f
				loc = l
			case ZWnd.reFilePath2(f, l) =>
				stxt = f
				loc = l
			case ZWnd.reQuotedFilePath(f, l) => 
				stxt = f
				loc = l
			case ZWnd.reQuotedFilePath2(f, l) =>
				stxt = f
				loc = l
			case _ =>
				stxt = txt
		}

		val sp = ZUtilities.expandPath(stxt, root)
		val ep = (if(ZUtilities.isFullPath(sp)) "" else (rawPath + ZUtilities.separator)) + sp 

		if(new File(ep).exists)
		{
			if(indBind) 
			{
				path = ep
				command("Get")
				if(loc != null && !loc.isEmpty) look(loc)
			} 
			else 
			{
				publish(new ZLookEvent(this, ep + loc))
			}

			return true
		}

		var pos = body.caret.position
		var t = body.text.substring(pos)
		var p = Pattern.compile(stxt, Pattern.MULTILINE)
		var m = p.matcher(t)

		if(m.find())  {
			body.caret.dot = pos + m.start()
			body.caret.moveDot(pos + m.end())
			body.requestFocus()
			return true
		}

		command(stxt)
		return true
	}

	def externalCmd(op : String, cmd : String, in : Option[String] = None) : Option[Process] = {
		val onOutput: String => Unit = s => SwingUtilities.invokeLater(() => {
			if(!scroll) body.append(s)
			else {
				val current = body.caret.dot
				body.selected = s
				body.caret.dot = current + s.length
			}
		})
		val onDone: () => Unit = () => SwingUtilities.invokeLater(() =>
			tag.text = tag.text.replaceAll(ZWnd.CmdExecIndicator, "")
		)

		val localFp = path
		val f = new File(localFp)
		val env = new HashMap[String, String] + ("Z_LOCAL_FP" -> localFp) + ("Z_FP" -> f.getCanonicalPath)

		tag.text = tag.text + ZWnd.CmdExecIndicator
		try {
			op match {
				case "<" => ZUtilities.extCmd(cmd, onOutput, onDone, redirectErrStream = true, workdir = Some(root), env = Some(env))
				case ">" => ZUtilities.extCmd(cmd, onOutput, onDone, redirectErrStream = true, input = in, workdir = Some(root), env = Some(env))
				case "|" =>
					val sel = Option(body.selected).getOrElse("")
					body.selected = ""
					ZUtilities.extCmd(cmd, onOutput, onDone, redirectErrStream = true, input = Some(sel), workdir = Some(root), env = Some(env))
				case "!" =>
					body.text = ""
					ZUtilities.extCmd(cmd, onOutput, onDone, redirectErrStream = true, workdir = Some(root), env = Some(env))
			}
		} catch {
			case e : Throwable =>
				JOptionPane.showMessageDialog(null, e.getMessage, "External Command Error", JOptionPane.ERROR_MESSAGE)
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

	private def onDiagnostics(diags: List[Diagnostic]): Unit = {
		lspParser.setDiagnostics(diags)
		val content = diags.map { d =>
			val sev = Option(d.getSeverity) match {
				case Some(DiagnosticSeverity.Error)       => "error"
				case Some(DiagnosticSeverity.Warning)     => "warning"
				case Some(DiagnosticSeverity.Information) => "info"
				case _                                    => "hint"
			}
			val ln = d.getRange.getStart.getLine + 1
			s"${path}:${ln}: ${sev}: ${d.getMessage}"
		}.mkString("\n")
		javax.swing.SwingUtilities.invokeLater(() => {
			body.peer.forceReparsing(0)
			publish(new ZDiagnosticsReadyEvent(this, content))
		})
	}

	private def showCompletionPopup(items: List[CompletionItem], caretOffset: Int): Unit = {
		try {
			if (items.isEmpty) return
			val popup = new javax.swing.JPopupMenu()
			items.take(50).foreach { item =>
				val detail = Option(item.getDetail).filter(_.nonEmpty).map(" — " + _).getOrElse("")
				val mi = new javax.swing.JMenuItem(item.getLabel + detail)
				mi.addActionListener(_ => applyCompletion(item, caretOffset))
				popup.add(mi)
			}
			val dot  = body.caret.dot
			val rect = body.peer.modelToView2D(dot)
			popup.show(body.peer, rect.getX.toInt, (rect.getY + rect.getHeight).toInt)
		} catch {
			case e: Throwable =>
				javax.swing.JOptionPane.showMessageDialog(null, e.getMessage, "Complete Error", javax.swing.JOptionPane.ERROR_MESSAGE)
		}
	}

	private def applyCompletion(item: CompletionItem, caretOffset: Int): Unit = {
		try {
			val doc = body.peer.getDocument
			Option(item.getTextEdit) match {
				case Some(e) if e.isLeft =>
					val edit = e.getLeft
					val r    = edit.getRange
					val s    = body.lineStart(r.getStart.getLine) + r.getStart.getCharacter
					val end  = body.lineStart(r.getEnd.getLine)   + r.getEnd.getCharacter
					doc.remove(s, end - s)
					doc.insertString(s, edit.getNewText, null)
				case Some(e) if e.isRight =>
					val edit = e.getRight
					val r    = edit.getInsert
					val s    = body.lineStart(r.getStart.getLine) + r.getStart.getCharacter
					val end  = body.lineStart(r.getEnd.getLine)   + r.getEnd.getCharacter
					doc.remove(s, end - s)
					doc.insertString(s, edit.getNewText, null)
				case _ =>
					val text = Option(item.getInsertText).filter(_.nonEmpty).getOrElse(item.getLabel)
					doc.insertString(caretOffset, text, null)
			}
		} catch { case e: Throwable => javax.swing.JOptionPane.showMessageDialog(null, e.getMessage, "Complete Error", javax.swing.JOptionPane.ERROR_MESSAGE) }
		body.requestFocus()
	}

	// Called by ZCol.closeWnd to clean up LSP before removing the window.
	def close(): Unit = {
		hoverTimer.stop()
		didChangeTimer.stop()
		lspClient.foreach { c =>
			try { c.didClose() }  catch { case _: Throwable => }
			try { c.shutdown() }  catch { case _: Throwable => }
			ZLspManager.unregister(c)
		}
		lspClient = None
	}

	def root = rootPath
	def root_=(s : String) = { rootPath = new File(s).getCanonicalPath }

	def path = tag.text match {
		case ZWnd.reQuotedScratch(dirty, p) =>
			val ep = ZUtilities.expandPath(p, root)
			if(ZUtilities.isFullPath(ep)) ep else new File(root + ZUtilities.separator + ep).getCanonicalPath
		case ZWnd.reScratch(dirty, p) =>
			val ep = ZUtilities.expandPath(p, root)
			if(ZUtilities.isFullPath(ep)) ep else new File(root + ZUtilities.separator + ep).getCanonicalPath
		case ZWnd.reQuotedPath(dirty, p) =>
			val ep = ZUtilities.expandPath(p, root)
			if(ZUtilities.isFullPath(ep)) ep else new File(root + ZUtilities.separator + ep).getCanonicalPath
		case ZWnd.rePath(dirty, p) =>
			val ep = ZUtilities.expandPath(p, root)
			if(ZUtilities.isFullPath(ep)) ep else new File(root + ZUtilities.separator + ep).getCanonicalPath
		case _ => new File(root).getCanonicalPath
	}

	def path_=(p : String) = tag.text = tag.text.replace(rawPath, p)

	def rawPath = tag.text match {
		case ZWnd.reQuotedPath(dirty, p) => p
		case ZWnd.rePath(dirty, p) => p
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

	def put(f : String) = if(f != null && !f.trim().isEmpty && !new File(f).isDirectory && !ZWnd.isScratchBuffer(f)) {
		var valid = false
		try {
			val fw = new FileWriter(f)
			fw.write(body.text)
			fw.close
			valid = true
		} catch {
			case e : Throwable => JOptionPane.showMessageDialog(null, e.getMessage, "Put Error", JOptionPane.ERROR_MESSAGE)
		}

		valid
	}

	def get(f : String = path) = if(f != null && !f.trim.isEmpty){
		var valid = false
		try {
			val ef = ZUtilities.expandPath(f, root)
			val o = new File(if(ef.startsWith(ZUtilities.separator)) ef else (root + File.separator + ef)) 

			if(o.isDirectory) 
				body.text = o.list.toList.sortWith((a,b) => a < b ).
						map((e) => if(new File(f + File.separator + e).isDirectory) { e + File.separator }  else e).
							mkString(Properties.lineSeparator)
			else
				body.text = Source.fromFile(f).mkString

			body.caret.position = 0
			valid = true
			if(indHilite) body.hilite(ZLangRegistry.forPath(f))
		} catch {
			case e : Throwable => JOptionPane.showMessageDialog(null, f + " " + e.getMessage, "Get Error", JOptionPane.ERROR_MESSAGE)
		}

		valid
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
			math.max(0, colorBack.getRed   - 15),
			math.max(0, colorBack.getGreen - 15),
			math.max(0, colorBack.getBlue  - 15)
		))
		g.setLineNumberColor(new Color(
			colorFore.getRed / 2 + colorBack.getRed / 2,
			colorFore.getGreen / 2 + colorBack.getGreen / 2,
			colorFore.getBlue / 2 + colorBack.getBlue / 2
		))
		g.setLineNumberFont(body.peer.getFont)
		g.setBorderColor(colorBack)
	}

	def applyColor(c : Color, colors : Tuple3[Int, Int, Int]) : Color = {
		def valid(i : Int) = (i >= 0) && (i  <= 255)
		if(valid(colors._1) && valid(colors._2) && valid(colors._3))  new Color(colors._1, colors._2, colors._3)
		else c
	}

	def properties : Map[String, String] = {
		var p  = new HashMap[String, String]

		p += "path" -> path
		p += "path.root" -> root
		p += "path.rawpath" -> rawPath
		p += "dirty" -> (if(dirty) "true" else "false")
		p += "scroll" -> (if(scroll) "true" else "false")
		p += "tab.size" -> String.valueOf(body.tabSize)
		p += "indent.auto" -> (if(indIndent) "true" else "false")
		p += "interactive" -> (if(indInteractive) "true" else "false")
		p += "bind"         -> (if(indBind)     "true" else "false")
		p += "hilite"       -> (if(indHilite)   "true" else "false")
		p += "line.numbers" -> (if(indLineNums) "true" else "false")
		p += "lines" -> String.valueOf(body.lineCount)
		p += "line.current" -> String.valueOf(body.currLineNo + 1)
		p += "line.wrap" -> (if(body.lineWrap) "true" else "false")
		p += "column.current" -> String.valueOf(body.currColumn)
		p += "selection.start" -> String.valueOf(body.selectionStart)
		p += "selection.end" -> String.valueOf(body.selectionEnd)
		p += "body.color.back" -> String.valueOf(colorBack.getRGB())	
		p += "body.color.fore" -> String.valueOf(colorFore.getRGB())	
		p += "body.color.caret" -> String.valueOf(colorCaret.getRGB())
		p += "body.color.selback" -> String.valueOf(colorSelBack.getRGB())
		p += "body.color.selfore" -> String.valueOf(colorSelFore.getRGB())
		p += "body.font.fixed" -> fontFixed.getFontName
		p += "body.font.fixed.size" -> fontFixed.getSize.toString
		p += "body.font.variable" -> fontVar.getFontName
		p += "body.font.variable.size" -> fontVar.getSize.toString
		p += "body.font.current" ->body.font.getFontName
		p += "body.font.current.size" -> String.valueOf(body.font.getSize)
		p += "tag.color.back" -> String.valueOf(colorTBack.getRGB())	
		p += "tag.color.fore" -> String.valueOf(colorTFore.getRGB())	
		p += "tag.color.caret" -> String.valueOf(colorTCaret.getRGB())
		p += "tag.color.selback" -> String.valueOf(colorTSelBack.getRGB())
		p += "tag.color.selfore" -> String.valueOf(colorTSelFore.getRGB())
		p += "tag.font" -> tag.font.getFontName
		p += "tag.size" -> String.valueOf(tag.font.getSize)
		p
	}

	def dump : Map[String, String] = {
		var p = properties
		p += "body.text" -> (if(dirty || ZWnd.isScratchBuffer(tag.text)) body.text else "")
		p += "tag.text" -> tag.text
		p
	}

	def load(p: Map[String, String], prefix : String = "") = {
		path = p.getOrElse(prefix + "path", "+")
		root = p.getOrElse(prefix + "path.root", ".")
		body.tabSize = p.getOrElse(prefix + "tab.size", "4").toInt
		body.lineWrap = if(p.getOrElse(prefix + "line.wrap", "false").equals("true")) true else false

		fontFixed = new Font(p.getOrElse(prefix + "body.font.fixed", fontFixed.getFontName), Font.PLAIN, p.getOrElse(prefix + "body.font.fixed.size", fontFixed.getSize.toString).toInt)
		fontVar = new Font(p.getOrElse(prefix + "body.font.variable", fontVar.getFontName), Font.PLAIN, p.getOrElse(prefix + "body.font.variable.size", fontVar.getSize.toString).toInt)
		body.font = new Font(p.getOrElse(prefix + "body.font.current", body.font.getFontName), Font.PLAIN, p.getOrElse(prefix + "body.font.current.size", body.font.getSize.toString).toInt)
		colorBack = new Color(p.getOrElse(prefix + "body.color.back", String.valueOf(colorBack.getRGB())).toInt)
		colorFore  = new Color(p.getOrElse(prefix + "body.color.fore", String.valueOf(colorFore.getRGB())).toInt)
		colorCaret  = new Color(p.getOrElse(prefix + "body.color.caret", String.valueOf(colorCaret.getRGB())).toInt)
		colorSelBack = new Color(p.getOrElse(prefix + "body.color.selback", String.valueOf(colorSelBack.getRGB())).toInt)
		colorSelFore = new Color(p.getOrElse(prefix + "body.color.selfore", String.valueOf(colorSelFore.getRGB())).toInt)
		body.colors(colorBack, colorFore, colorCaret, colorSelBack, colorSelFore)

		tag.font = new Font(p.getOrElse(prefix + "tag.font", body.font.getFontName), Font.PLAIN, p.getOrElse(prefix + "tag.size", body.font.getSize.toString).toInt)
		colorTBack = new Color(p.getOrElse(prefix + "tag.color.back", String.valueOf(colorTBack.getRGB())).toInt)
		colorTFore  = new Color(p.getOrElse(prefix + "tag.color.fore", String.valueOf(colorTFore.getRGB())).toInt)
		colorTCaret  = new Color(p.getOrElse(prefix + "tag.color.caret", String.valueOf(colorTCaret.getRGB())).toInt)
		colorTSelBack = new Color(p.getOrElse(prefix + "tag.color.selback", String.valueOf(colorTSelBack.getRGB())).toInt)
		colorTSelFore = new Color(p.getOrElse(prefix + "tag.color.selfore", String.valueOf(colorTSelFore.getRGB())).toInt)	
		tag.colors(colorTBack, colorTFore, colorTCaret, colorTSelBack, colorTSelFore)
		tag.text = p.getOrElse(prefix + "tag.text", "+ Get Put Zerox Close | Undo Redo Wrap Indent Mark")

		indIndent = if(p.getOrElse(prefix + "indent.auto", "false").equals("true")) true  else false
		indInteractive = if(p.getOrElse(prefix + "interactive", "false").equals("true")) true  else false
		indBind = if(p.getOrElse(prefix + "bind", "false").equals("true")) true else false
		dirty = if(p.getOrElse(prefix + "dirty", "false").equals("true"))  true  else  false
		scroll = if(p.getOrElse(prefix + "scroll", "false").equals("true"))  true  else false 

		indHilite   = if(p.getOrElse(prefix + "hilite",       "false").equals("true")) true else false
		indLineNums = if(p.getOrElse(prefix + "line.numbers", "false").equals("true")) true else false
		if(indLineNums) bodyScroll.setLineNumbersEnabled(true)
		styleGutter()
		if(!dirty)  command("Get") else  body.text = p.getOrElse(prefix + "body.text", "")

		if(body.lineCount > 0) {
			body.selectionStart = p.getOrElse(prefix + "selection.start", "0").toInt
			body.selectionEnd = p.getOrElse(prefix + "selection.end", "0").toInt
		}
	}
}

object ZWnd {
	var rePrompt = """[^\$%>\?#]*[\$%>\?#]\s*(.+)\s*""".r
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
	val reColors = """Color(TBack|TFore|TCaret|TSelFore|TSelBack|Back|Fore|Caret|SelFore|SelBack)\s+(\d{1,3})\s+(\d{1,3})\s+(\d{1,3})""".r

	val CmdExecIndicator = " <!> "

	def isScratchBuffer(p: String) = p match {
		case reScratch(i, p) => true
		case reQuotedScratch(i, p) => true
		case _ => false
	}
}

class ZCmdEvent(val source : ZWnd, val command : String) extends Event
class ZDiagnosticsReadyEvent(val source: ZWnd, val content: String) extends Event
class ZLookEvent(val source : ZWnd, val path : String) extends Event
class ZStatusEvent(val source : ZWnd, val properties : Map[String, String]) extends Event

