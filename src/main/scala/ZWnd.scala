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

class ZWnd(initTagText : String, initBodyText : String = "", currDir : String = ".") extends SplitPane(Orientation.Horizontal) {
	var rootPath = new File(currDir).getAbsolutePath
	var indIndent = false
	var indScroll = true
	var indInteractive = false
	var indBind = false
	var indHilite   = false
	var indLineNums = false

	var bodyScheme = ZColorScheme(
		new Color(0xFF, 0xFF, 0xE0),
		new Color(0x00, 0x00, 0x00),
		new Color(0x00, 0x00, 0x00),
		new Color(0xC8, 0x75, 0x9F),
		new Color(0xFF, 0xFF, 0xFF))

	var tagScheme = ZColorScheme(
		new Color(0x4A, 0x61, 0x95),
		new Color(0xFF, 0xFF, 0xFF),
		new Color(0xC7, 0xC7, 0xC7),
		new Color(0xFF, 0xFF, 0xFF),   // white — inverted from tag blue
		new Color(0x4A, 0x61, 0x95))   // tag blue — inverted from tag white

	var cmdProcess : Option[Process] = None
	var cmdProcessWriter : Option[BufferedWriter] = None
	var dragSel = false
	var dragSelMark = -1

	val tag = new ZTextArea(initTagText, true)
	tag.font = ZFonts.defaultTag
	tagScheme.applyTo(tag)
	tag.rows = 1

	val body = new ZTextArea(initBodyText)
	bodyScheme.applyTo(body)

	val lsp = new ZLspSupport(body, () => path,
		() => publish(new ZStatusEvent(this, properties)),
		content => publish(new ZDiagnosticsReadyEvent(this, content)))

	var fontVar   = ZFonts.defaultVar
	var fontFixed = ZFonts.defaultFixed
	body.font = fontFixed

	dividerSize = 2
	topComponent = new ScrollPane(tag) {
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
					if (t.startsWith("T")) {
						tagScheme = tagScheme.withComponent(t.drop(1), r.toInt, g.toInt, b.toInt)
						tagScheme.applyTo(tag)
					} else {
						bodyScheme = bodyScheme.withComponent(t, r.toInt, g.toInt, b.toInt)
						bodyScheme.applyTo(body)
						styleGutter()
					}
				case "Lsp"                       =>
					JOptionPane.showMessageDialog(null,
						"Lsp requires a project root path, e.g:  Lsp ~/myapp",
						"LSP Error", JOptionPane.ERROR_MESSAGE)
				case ZWnd.reLsp("off")           =>
					lsp.stop()
				case ZWnd.reLsp(p)               =>
					val projRoot = ZUtilities.expandPath(p.trim, root)
					val langId   = ZLangRegistry.langIdFor(path)
					lsp.start(projRoot, langId)
				case "Check"                     => lsp.check()
				case "Complete"                  => lsp.complete()
				case ZScripts.reAnyScript(name, args) =>
					ZScripts.resolve(name, rootPath) match {
						case Right(f) => publish(new ZScriptEvent(this, f.getPath, args.trim))
						case Left(searched) => ZScripts.showError(name, searched)
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
		val baseDir = if (ZWnd.isScratchBuffer(tag.text)) new File(path).getParent else rawPath
		val ep = (if(ZUtilities.isFullPath(sp)) "" else (baseDir + ZUtilities.separator)) + sp

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

		if(m.find() && m.end() > m.start())  {
			body.caret.dot = pos + m.start()
			body.caret.moveDot(pos + m.end())
			body.requestFocus()
			return true
		}

		command(stxt)
		return true
	}

	def externalCmd(op : String, cmd : String, in : Option[String] = None) : Option[Process] = {
		val resolved = cmd.trim match {
			case ZScripts.reScript(name, args) =>
				ZScripts.resolve(name, rootPath) match {
					case Right(f) => f.getPath + (if (args.trim.isEmpty) "" else " " + args.trim)
					case Left(searched) =>
						tag.text = tag.text.replaceAll(ZWnd.CmdExecIndicator, "")
						ZScripts.showError(name, searched)
						return None
				}
			case _ => cmd
		}
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
		val wd  = if(f.isDirectory) f.getCanonicalPath else f.getParentFile.getCanonicalPath
		val sel = Option(body.selected).getOrElse("")
		val env = new HashMap[String, String] +
			("Z_FILE"      -> localFp) +
			("Z_FP"        -> f.getCanonicalPath) +
			("Z_DIR"       -> wd) +
			("Z_SELECTION" -> sel)

		tag.text = tag.text + ZWnd.CmdExecIndicator
		try {
			op match {
				case "<" => ZUtilities.extCmd(resolved, onOutput, onDone, redirectErrStream = true, workdir = Some(wd), env = Some(env))
				case ">" => ZUtilities.extCmd(resolved, onOutput, onDone, redirectErrStream = true, input = in, workdir = Some(wd), env = Some(env))
				case "|" =>
					val sel = Option(body.selected).getOrElse("")
					body.selected = ""
					ZUtilities.extCmd(resolved, onOutput, onDone, redirectErrStream = true, input = Some(sel), workdir = Some(wd), env = Some(env))
				case "!" =>
					body.text = ""
					ZUtilities.extCmd(resolved, onOutput, onDone, redirectErrStream = true, workdir = Some(wd), env = Some(env))
			}
		} catch {
			case e : Throwable =>
				JOptionPane.showMessageDialog(null, e.getMessage, "External Command Error", JOptionPane.ERROR_MESSAGE)
				tag.text = tag.text.replaceAll(ZWnd.CmdExecIndicator, "")
				None
		}
	}

	def runScript(scriptPath: String, args: String, extraEnv: Map[String, String] = Map.empty): Option[Process] = {
		val cmd = if (args.isEmpty) scriptPath else s"$scriptPath $args"
		val localFp = path
		val f = new File(localFp)
		val wd = if(f.isDirectory) f.getCanonicalPath else f.getParentFile.getCanonicalPath
		val sel = Option(body.selected).getOrElse("")
		val env = (new HashMap[String, String] +
			("Z_FILE"      -> localFp) +
			("Z_FP"        -> f.getCanonicalPath) +
			("Z_DIR"       -> wd) +
			("Z_SELECTION" -> sel)) ++ extraEnv
		val onOutput: String => Unit = s => SwingUtilities.invokeLater(() => {
			val current = body.caret.dot
			body.selected = s
			body.caret.dot = current + s.length
		})
		val onDone: () => Unit = () => SwingUtilities.invokeLater(() =>
			tag.text = tag.text.replaceAll(ZWnd.CmdExecIndicator, "")
		)
		tag.text = tag.text + ZWnd.CmdExecIndicator
		try {
			ZUtilities.extCmd(cmd, onOutput, onDone, redirectErrStream = true, workdir = Some(wd), env = Some(env))
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

	private def resolvePath(p: String): String = {
		val ep = ZUtilities.expandPath(p, root)
		if (ZUtilities.isFullPath(ep)) ep else new File(root + ZUtilities.separator + ep).getCanonicalPath
	}

	def path = tag.text match {
		case ZWnd.reQuotedScratch(_, p) => resolvePath(p)
		case ZWnd.reScratch(_, p)       => resolvePath(p)
		case ZWnd.reQuotedPath(_, p)    => resolvePath(p)
		case ZWnd.rePath(_, p)          => resolvePath(p)
		case _                          => new File(root).getCanonicalPath
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
			scala.util.Using(new FileWriter(f))(_.write(body.text)).get
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
			else {
				// Notify LSP of the file switch before loading new content.
				val newPath = o.getCanonicalPath
				if (lsp.enabled && newPath != path) lsp.updatePath(newPath)
				body.text = scala.util.Using(Source.fromFile(f))(_.mkString).get
				if (lsp.enabled && newPath != path) lsp.didOpen(body.text)
			}

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
		p += "bind"         -> (if(indBind)        "true" else "false")
		p += "lsp"          -> (if(lsp.enabled)    "true" else "false")
		p += "lsp.root"     -> lsp.root
		p += "lsp.indexing" -> (if(lsp.indexing)   "true" else "false")
		p += "lsp.status"   -> lsp.status
		p += "hilite"       -> (if(indHilite)      "true" else "false")
		p += "line.numbers" -> (if(indLineNums)    "true" else "false")
		p += "lines" -> String.valueOf(body.lineCount)
		p += "line.current" -> String.valueOf(body.currLineNo + 1)
		p += "line.wrap" -> (if(body.lineWrap) "true" else "false")
		p += "column.current" -> String.valueOf(body.currColumn)
		p += "selection.start" -> String.valueOf(body.selectionStart)
		p += "selection.end" -> String.valueOf(body.selectionEnd)
		p += "body.color.back"    -> bodyScheme.back.getRGB.toString
		p += "body.color.fore"    -> bodyScheme.fore.getRGB.toString
		p += "body.color.caret"   -> bodyScheme.caret.getRGB.toString
		p += "body.color.selback" -> bodyScheme.selBack.getRGB.toString
		p += "body.color.selfore" -> bodyScheme.selFore.getRGB.toString
		p += "body.font.fixed" -> fontFixed.getFontName
		p += "body.font.fixed.size" -> fontFixed.getSize.toString
		p += "body.font.variable" -> fontVar.getFontName
		p += "body.font.variable.size" -> fontVar.getSize.toString
		p += "body.font.current" -> body.font.getFontName
		p += "body.font.current.size" -> String.valueOf(body.font.getSize)
		p += "tag.color.back"    -> tagScheme.back.getRGB.toString
		p += "tag.color.fore"    -> tagScheme.fore.getRGB.toString
		p += "tag.color.caret"   -> tagScheme.caret.getRGB.toString
		p += "tag.color.selback" -> tagScheme.selBack.getRGB.toString
		p += "tag.color.selfore" -> tagScheme.selFore.getRGB.toString
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
		tag.text = p.getOrElse(prefix + "tag.text", "+ " + ZCol.wndTagLine.trim)

		indIndent      = p.getOrElse(prefix + "indent.auto",  "false") == "true"
		indInteractive = p.getOrElse(prefix + "interactive",  "false") == "true"
		indBind        = p.getOrElse(prefix + "bind",         "false") == "true"
		dirty          = p.getOrElse(prefix + "dirty",        "false") == "true"
		scroll         = p.getOrElse(prefix + "scroll",       "false") == "true"

		indHilite   = p.getOrElse(prefix + "hilite",       "false") == "true"
		indLineNums = p.getOrElse(prefix + "line.numbers", "false") == "true"
		if(indLineNums) bodyScroll.setLineNumbersEnabled(true)
		styleGutter()
		if(!dirty)  command("Get") else  body.text = p.getOrElse(prefix + "body.text", "")

		if(body.lineCount > 0) {
			body.selectionStart = int("selection.start", 0)
			body.selectionEnd   = int("selection.end",   0)
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

class ZScriptEvent(val source: ZWnd, val scriptPath: String, val args: String) extends Event
class ZCmdEvent(val source : ZWnd, val command : String) extends Event
class ZDiagnosticsReadyEvent(val source: ZWnd, val content: String) extends Event
class ZLookEvent(val source : ZWnd, val path : String) extends Event
class ZStatusEvent(val source : ZWnd, val properties : Map[String, String]) extends Event
