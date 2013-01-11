/*
$Id$

Copyright (c) 2011. Ramon de Vera Jr.
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
import actors.Actor
import actors.Actor._
import swing.{SplitPane, ScrollPane, Orientation, FileChooser}
import swing.event.{KeyPressed, KeyReleased, Key, MouseClicked, MouseEntered, MousePressed, MouseDragged, MouseReleased, Event}

import java.io.{FileWriter, File, BufferedWriter, OutputStreamWriter}
import java.awt.{Font, Color}
import java.awt.ComponentOrientation.RIGHT_TO_LEFT
import javax.swing.text.{Utilities, DefaultCaret}
import javax.swing.{JOptionPane, ScrollPaneConstants, SwingUtilities}
import java.util.regex.Pattern

class ZWnd(initTagText : String, initBodyText : String = "") extends SplitPane(Orientation.Horizontal) {
	var rootPath = new File(".").getAbsolutePath
	var indIndent = false
	var indScroll = true
	var indInteractive = false
	var indBind = false
	var cmdProcess : Process = null
	var cmdProcessWriter : BufferedWriter = null
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
	var colorTSelBack =new Color(0xFF, 0xFF, 0xE0)
	var colorTSelFore = new Color(0x23, 0x2E, 0x6C)

	var tag = new ZTextArea(initTagText, true)
	tag.font = new Font("Bitstream Vera Sans", Font.PLAIN, 12)
	tag.colors(colorTBack, colorTFore,  colorTCaret, colorTSelBack, colorTSelFore )

	var body = new ZTextArea(initBodyText)
	body.colors(colorBack, colorFore, colorCaret, colorSelBack, colorSelFore)

	var fontVar = new Font("Bitstream Vera Serif", Font.PLAIN, 13)
	var fontFixed = new Font("Bitstream Vera Sans Mono", Font.PLAIN, 13)
	body.font = fontVar
	
	dividerSize = 2
	topComponent =new ScrollPane(tag) {
			peer.setComponentOrientation(RIGHT_TO_LEFT)
	}

	bottomComponent = new ScrollPane(body) {
			peer.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS)
			peer.setComponentOrientation(RIGHT_TO_LEFT)
	}

	listenTo(tag.mouse.moves, body.mouse.moves)
	reactions += {
		case e : MouseEntered => publish(new ZStatusEvent(this, properties))
		case e : MousePressed => if(SwingUtilities.isMiddleMouseButton(e.peer) || SwingUtilities.isRightMouseButton(e.peer)) 
			dragSelMark = e.source.asInstanceOf[ZTextArea].peer.viewToModel(e.point)
		case e : MouseDragged => if(SwingUtilities.isMiddleMouseButton(e.peer) || SwingUtilities.isRightMouseButton(e.peer)) {
			dragSel = true
			val ta = e.source.asInstanceOf[ZTextArea]

			if(dragSelMark != -1) {
				ta.peer.setCaretPosition(dragSelMark)
				dragSelMark = -1
			}

			ta.peer.moveCaretPosition(ta.peer.viewToModel(e.point))
		}
		case e : MouseReleased =>
			if(dragSel) {
				if(SwingUtilities.isMiddleMouseButton(e.peer))  command(ZUtilities.selectedText(e.source.asInstanceOf[ZTextArea], e))
				else if(SwingUtilities.isRightMouseButton(e.peer))  look(ZUtilities.selectedText(e.source.asInstanceOf[ZTextArea], e))
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
			if(e.key == Key.Enter && indInteractive && cmdProcess != null) {
				body.line(body.currLineNo - 1) match {
					case ZWnd.rePrompt(cmd) => 
						cmdProcessWriter.write(cmd);
						cmdProcessWriter.newLine();
						cmdProcessWriter.flush();
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
			if(SwingUtilities.isMiddleMouseButton(e.peer)
				|| (e.peer.isShiftDown && SwingUtilities.isRightMouseButton(e.peer))) 
				command(ZUtilities.selectedText(e.source.asInstanceOf[ZTextArea], e))
			else if(SwingUtilities.isRightMouseButton(e.peer)) {
				try {
					look(ZUtilities.selectedText(e.source.asInstanceOf[ZTextArea], e))
				} catch {
					case e => JOptionPane.showMessageDialog(null, e.getMessage, "Look Error", JOptionPane.ERROR_MESSAGE)
				}
			}

			publish(new ZStatusEvent(this, properties))
	}

	listenTo(body)
	reactions += {
		case e : ZCleanTextEvent => dirty = false
		case e : ZDirtyTextEvent => dirty = true
	}

	def command(cmds : String) = if(cmds != null && !cmds.trim.isEmpty) {
		for(cmd <- cmds.lines.map(_.trim)) {
			cmd.trim match {
				case "Get" => 
					get(if(ZWnd.isScratchBuffer(tag.text)) "" else path)
					dirty = false
				case ZWnd.reQuotedGet(f) => get(f)
				case ZWnd.reGet(f) => get(f)
				case "Put" =>
					put(if(ZWnd.isScratchBuffer(tag.text)) "" else path)
					dirty = false
				case ZWnd.reQuotedPut(f) => put(f)
				case ZWnd.rePut(f) => put(f)
				case _ => cmd match {
					case "Dirty" => dirty = !dirty
					case "Clean" => dirty = !dirty
					case "Scroll" => scroll = !scroll
					case "Cut" => body.cut
					case "Paste" => body.paste
					case "Snarf" => body.copy
					case "Redo" => if(body.undomgr.canRedo) { body.undomgr.redo; dirty }
					case "Undo" => if(body.undomgr.canUndo) body.undomgr.undo else clean
					case "Wrap" => body.lineWrap = !body.lineWrap
					case "Indent" => indIndent = !indIndent
					case "Clear" => body.text = ""
					case "Bind" => indBind = !indBind
					case ZWnd.reTab(t) => if(t != null && !t.isEmpty) body.tabSize = t.toInt
					case ZWnd.reFont(font, pt) =>
						fontVar = new Font(font, Font.PLAIN, pt.toInt)
						body.font = fontVar
					case ZWnd.reFONT(font,pt) =>
						fontFixed = new Font(font, Font.PLAIN, pt.toInt)
						body.font = fontFixed
					case ZWnd.reTagFont(font, pt) =>
						tag.font = new Font(font, Font.PLAIN, pt.toInt)
					case "Font" => body.font = fontVar
					case "FONT" => body.font = fontFixed
					case _ => cmd match {
						case "Input" => indInteractive = !indInteractive
						case ZWnd.reInput(prompt) => ZWnd.rePrompt = prompt.r
						case "Kill" => 
							if(cmdProcess != null)  {
								cmdProcessWriter.close
								cmdProcess.destroy
								tag.text = tag.text.replaceAll(ZWnd.CmdExecIndicator, "")
							}
							cmdProcess = null
							cmdProcessWriter = null
						case ZWnd.reExternalCmd(op, cmd) =>
							if(cmdProcess != null)  cmdProcess.destroy()
							cmdProcess = externalCmd(op, cmd)
							if(cmdProcess != null)  cmdProcessWriter = new BufferedWriter(new OutputStreamWriter(cmdProcess.getOutputStream))
						case ZWnd.reColors(t, r, g, b) =>
							if(t.equals("TBack"))  colorTBack = applyColor(colorTBack, (r.toInt, g.toInt, b.toInt))
							if(t.equals("TFore"))  colorTFore = applyColor(colorTFore, (r.toInt, g.toInt, b.toInt))
							if(t.equals("TCaret"))  colorTCaret = applyColor(colorTCaret, (r.toInt, g.toInt, b.toInt))
							if(t.equals("TSelBack"))  colorTSelBack = applyColor(colorTSelBack, (r.toInt, g.toInt, b.toInt))
							if(t.equals("TSelFore"))  colorTSelFore = applyColor(colorTSelFore, (r.toInt, g.toInt, b.toInt))
							if(t.equals("Back"))  colorBack = applyColor(colorBack, (r.toInt, g.toInt, b.toInt))
							if(t.equals("Fore"))  colorFore = applyColor(colorFore, (r.toInt, g.toInt, b.toInt))
							if(t.equals("Caret"))  colorCaret = applyColor(colorCaret, (r.toInt, g.toInt, b.toInt))
							if(t.equals("SelBack"))  colorSelBack = applyColor(colorSelBack, (r.toInt, g.toInt, b.toInt))
							if(t.equals("SelFore"))  colorSelFore = applyColor(colorSelFore, (r.toInt, g.toInt, b.toInt))

							if(t.startsWith("T")) tag.colors(colorTBack, colorTFore, colorTCaret, colorTSelBack, colorTSelFore)
							else body.colors(colorBack, colorFore, colorCaret, colorSelBack, colorSelFore)
						case _ => publish(new ZCmdEvent(this, cmd))
					}
				}
			}
		}
	}

	def look(txt: String) : Boolean  = {
		if(txt == null || txt.trim.isEmpty)  return false

		var stxt = ""
		var loc = ""
		var matchre = false

		txt match {
			case ZWnd.reLineNo(no) =>
				var i = no.toInt
				if(i >= 1 && i <= body.lineCount) {
					i = i - 1
					body.caret.dot = body.lineStart(i)
					body.caret.moveDot(body.lineEnd(i)-1)
					body.requestFocus
					return true
				}
				return false
			case ZWnd.reRegExp(re) => 
				stxt = re
				matchre = true
			case ZWnd.reFilePath(f, l) => 
				stxt = f
				loc = l
			case ZWnd.reFilePath2(f, l) =>
				stxt = f
				loc = l
			case _ =>
				stxt = txt
		}

		if(!matchre) {
			var np = stxt

			if(!ZUtilities.isFullPath(np)) {
				var rp  = rawPath

				rp match {
					case ZWnd.reScratch(d, p) => rp = p
					case ZWnd.reQuotedScratch(d, p) => rp = p
					case _ =>
				}

				if(!rp.startsWith(np)) np = rp + (if(rp.endsWith(ZUtilities.separator)) "" else ZUtilities.separator) + np
			} 

			if(new File(np).exists) {
				if(indBind)
				{
					path = np
					command("Get")
					look(loc)
					return true
				}
				else
				{
					publish(new ZLookEvent(this, np + loc))
					return false
				}
			}
		}

		var pos = body.caret.position
		var t = body.text.substring(pos)
		var p = Pattern.compile(stxt, Pattern.MULTILINE)
		var m = p.matcher(t)
		var found = false
		if(m.find())  found = true
		else {
			t = body.text.substring(0, pos)
			m = p.matcher(t)
			if(m.find())  {
				found = true
				pos = 0
			}
		}

		if(found) {
			body.caret.dot = pos + m.start()
			body.caret.moveDot(pos + m.end())
			body.requestFocus
			return true
		}

		return false
	}

	def externalCmd(op : String, cmd : String, in : String = null) : Process = {
		val a = actor { 
				loop {
					react {
						case ZWnd.CMD_DONE =>
							tag.text = tag.text.replaceAll(ZWnd.CmdExecIndicator, "") 	
							exit
						case s: String =>
							if(!scroll) body.append(s)
							else {	
								var current = body.caret.dot 
								body.selected = s
								body.caret.dot = current + s.length
							}
					}
				}
			}

		var p = path
		var f = new File(p)
		var e = new HashMap[String, String]

		e += "Z_LOCAL_FP" -> p
		if(f.isFile())  p = f.getParentFile.getCanonicalPath
		e += "Z_FP" -> f.getCanonicalPath

		tag.text = tag.text + ZWnd.CmdExecIndicator
		try {
			op match {
				case "<" => return ZUtilities.extCmd(cmd, a, redirectErrStream = true, workdir = p, env = e)
				case ">" => return ZUtilities.extCmd(cmd, a, redirectErrStream = true, input = in, workdir = p, env = e)
				case "|" =>
					val sel = body.selected
					body.selected = ""
					return ZUtilities.extCmd(cmd, a, redirectErrStream = true, input = sel, workdir = p, env = e)
				case "!" =>
					body.text = ""
					return ZUtilities.extCmd(cmd, a, redirectErrStream = true, workdir = p, env = e)
			}
		} catch {
			case e => 
				JOptionPane.showMessageDialog(null, e.getMessage, "External Command Error", JOptionPane.ERROR_MESSAGE)
				tag.text = tag.text.replaceAll(ZWnd.CmdExecIndicator, "")
				null
		}
	}

	def root = rootPath
	def root_=(s : String) = { rootPath = new File(s).getCanonicalPath }

	def path = tag.text match {
		case ZWnd.reQuotedScratch(dirty, p) =>if(ZUtilities.isFullPath(p)) p else new File(root + ZUtilities.separator + p).getCanonicalPath
		case ZWnd.reScratch(dirty, p) => if(ZUtilities.isFullPath(p)) p else new File(root + ZUtilities.separator + p).getCanonicalPath
		case ZWnd.reQuotedPath(dirty, p) => if(ZUtilities.isFullPath(p))  p else new File(root + ZUtilities.separator + p).getCanonicalPath
		case ZWnd.rePath(dirty, p) => if(ZUtilities.isFullPath(p))  p else new File(root + ZUtilities.separator + p).getCanonicalPath
		case _ => root
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
			case e => JOptionPane.showMessageDialog(null, e.getMessage, "Put Error", JOptionPane.ERROR_MESSAGE)
		}

		valid
	}

	def get(f : String = path) = if(f != null && !f.trim.isEmpty){
		var valid = false
		try {
			val o = new File(f)
			if(o.isDirectory) 
				body.text = o.list.toList.sortWith((a,b) => a < b ).
						map((e) => if(new File(f + File.separator + e) isDirectory) { e + File.separator }  else e).
							mkString(Properties lineSeparator)
			else body.text = Source.fromFile(f).mkString
			body.caret.position = 0
			valid = true
		} catch {
			case e => JOptionPane.showMessageDialog(null, e.getMessage, "Get Error", JOptionPane.ERROR_MESSAGE)
		}

		valid
	}

	def scroll = indScroll
	def scroll_=(b : Boolean) = {
		if(b)
			body.peer.getCaret.asInstanceOf[DefaultCaret].setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE)
		else
			body.peer.getCaret.asInstanceOf[DefaultCaret].setUpdatePolicy(DefaultCaret.UPDATE_WHEN_ON_EDT)

		indScroll = b
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
		p += "bind" -> (if(indBind) "true" else "false")
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
		p += "body.text" -> (if(dirty) body.text else "")
		p += "tag.text" -> tag.text
		p
	}

	def load(p: Map[String, String], prefix : String = "") = {
		path = p.getOrElse(prefix + "path", "+")
		root = p.getOrElse(prefix + "path.root", ".")
		body.tabSize = p.getOrElse(prefix + "tab.size", "4").toInt
		body.lineWrap = if(p.getOrElse(prefix + "line.wrap", "false").equals("true")) true else false

		fontFixed = new Font(p.getOrElse(prefix + "body.font.fixed", fontFixed.getFontName), Font.PLAIN, p.getOrElse(prefix + "body.font.size", fontFixed.getSize.toString).toInt)
		fontVar = new Font(p.getOrElse(prefix + "body.font.variable", fontVar.getFontName), Font.PLAIN, p.getOrElse(prefix + "body.font.variable.size", fontVar.getSize.toString).toInt)
		body.font = new Font(p.getOrElse(prefix + "body.font.current", body.font.getFontName), Font.PLAIN, p.getOrElse(prefix + "body.font.current.size", body.font.getSize.toString).toInt)
		colorBack = new Color(p.getOrElse(prefix + "body.color.back", String.valueOf(colorBack.getRGB())).toInt)
		colorFore  = new Color(p.getOrElse(prefix + "body.color.fore", String.valueOf(colorFore.getRGB())).toInt)
		colorCaret  = new Color(p.getOrElse(prefix + "body.color.caret", String.valueOf(colorCaret.getRGB())).toInt)
		colorSelBack = new Color(p.getOrElse(prefix + "body.color.selback", String.valueOf(colorSelBack.getRGB())).toInt)
		colorSelFore = new Color(p.getOrElse(prefix + "body.color.selfore", String.valueOf(colorSelFore.getRGB())).toInt)
		body.colors(colorBack, colorFore, colorCaret, colorSelBack, colorSelFore)

		tag.font = new Font(p.getOrElse(prefix + "tag.font", body.font.getFontName), Font.PLAIN, p.getOrElse(prefix + "tag.font.size", body.font.getSize.toString).toInt)
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

		if(!dirty)  command("Get") else  body.text = p.getOrElse(prefix + "body.text", "")

		if(body.lineCount > 0) {
			body.selectionStart = p.getOrElse(prefix + "selection.start", "0").toInt
			body.selectionEnd = p.getOrElse(prefix + "selection.end", "0").toInt
		}
	}
}

object ZWnd {
	var rePrompt = """[^\$%>\?]*[\$%>\?]\s*(.+)\s*""".r
	val reInput = """Input\s+(.+)""".r

	val rePre = """.*?(\S*)$""".r
	val rePath = """(?s)\s*(\*?)\s*(\S+).*""".r
	val reQuotedPath = """(?s)\s*(\*?)\s*'\s*([^']+).*'""".r
	val reScratch = """(?s)\s*(\*?)\s*([^+\s]*)[+].*""".r
	val reQuotedScratch = """(?s)\s*(\*?)\s*'([^+]*)[+].*'.*""".r
	val reRawTagLine = """(?s)\s*(\*?)\s*(.*)""".r

	val reFont = """Font\s+'(.+)'\s+([0-9]+)""".r
	val reFONT = """FONT\s+'(.+)'\s+([0-9]+)""".r
	val reTagFont = """TagFont\s+'(.+)'\s+([0-9]+)""".r
	val reTab = """Tab\s+([0-9]+)?""".r
	val reQuotedGet = """Get\s+'(.+)'""".r
	val reGet = """Get\s+(\S+)""".r
	val reQuotedPut = """Put\s+'(.+)'.*""".r
	val rePut = """Put\s+(\S+).*""".r
	val reLineNo = """^:([0-9]+)$""".r
	val reRegExp = """^:/(.+)$""".r
	val reFilePath = """(.+)(:[0-9]+)""".r
	val reFilePath2 = """(.+)(:/.+)""".r
	val reExternalCmd = """(?s)([\|<!])\s*(.+)\s*$""".r
	val reWhiteSpace = """(?s)^(\s+).*$""".r

	val reColors = """Color(TBack|TFore|TCaret|TSelFore|TSelBack|Back|Fore|Caret|SelFore|SelBack)\s+(\d{1,3})\s+(\d{1,3})\s+(\d{1,3})""".r

	val CmdExecIndicator = " <!> "

	def isScratchBuffer(p: String) = p match {
		case reScratch(i, p) => true
		case reQuotedScratch(i, p) => true
		case _ => false
	}

	val CMD_DONE = new ZCmdDoneEvent
}

class ZCmdDoneEvent extends Event
class ZCmdEvent(val source : ZWnd, val command : String) extends Event
class ZLookEvent(val source : ZWnd, val path : String) extends Event
class ZStatusEvent(val source : ZWnd, val properties : Map[String, String]) extends Event
