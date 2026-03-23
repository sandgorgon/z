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

import swing.{SplitPane, BorderPanel, Orientation, Panel, Component, FileChooser}
import swing.event.{KeyPressed, Key, MouseEvent, MouseEntered, MousePressed, MouseDragged, MouseReleased, MouseClicked, Event}
import javax.swing.JOptionPane
import collection.immutable.List
import collection.immutable.HashMap

import java.io.File
import java.awt.{Color, Font}
import javax.swing.{JOptionPane, SwingUtilities, BorderFactory}

class ZCol(currDir : String) extends BorderPanel {
	val wnd = genWnd(_ : String)
	val cmdWnd = genWnd(_ : String, ZCol.cmdTagLine)

	var wnds : List[ZWnd] = Nil

	var colorTBack = new Color(0xFF, 0xFF, 0xFF)
	var colorTFore = new Color(0x00, 0x00, 0x00)
	var colorTCaret = new Color(0x00, 0x00, 0x00)
	var colorTSelBack =new Color(0x96, 0x96, 0x96)
	var colorTSelFore = new Color(0xFF, 0xFF, 0xFF)
	var prevCmd = ""
	var currentDir = new File(currDir).getCanonicalPath
	var dragSel = false
	var dragSelMark = -1

	val tag = new ZTextArea(ZCol.colTagLine)
	tag.colors(colorTBack, colorTFore,  colorTCaret, colorTSelBack, colorTSelFore )
	tag.border = BorderFactory.createMatteBorder(0,0,1,0, Color.BLACK)
	tag.font = ZFonts.defaultTag

	val body : Panel = new BorderPanel

	layout(tag) = BorderPanel.Position.North
	layout(body) = BorderPanel.Position.Center

	deafTo(this)
	listenTo(tag.mouse.moves, tag.mouse.clicks) 
	reactions += {
		case e : MouseEntered =>
			e.source.requestFocus()
			publish(new ZColStatusEvent(this, properties))
		case e : MousePressed => if(SwingUtilities.isMiddleMouseButton(e.peer) || SwingUtilities.isRightMouseButton(e.peer))
			dragSelMark = tag.peer.viewToModel2D(e.point).toInt
		case e : MouseDragged => if(SwingUtilities.isMiddleMouseButton(e.peer) || SwingUtilities.isRightMouseButton(e.peer)) {
			dragSel = true
			if(dragSelMark != -1) {
				tag.peer.setCaretPosition(dragSelMark)
				dragSelMark = -1
			}

			tag.peer.moveCaretPosition(tag.peer.viewToModel2D(e.point).toInt)
		}
		case e : MouseReleased =>
			if(dragSel) {
				if(SwingUtilities.isMiddleMouseButton(e.peer))  command(ZUtilities.selectedText(tag, e))
				else if(SwingUtilities.isRightMouseButton(e.peer))  look(ZUtilities.selectedText(tag, e))
			}
			dragSel = false
			dragSelMark = -1
		case e : MouseClicked =>
			if(SwingUtilities.isRightMouseButton(e.peer)) {
				try {
					e.source match {
						case ta: ZTextArea => command(ZUtilities.selectedText(ta, e))
						case _ =>
					}
				} catch {
					case e : Throwable => JOptionPane.showMessageDialog(null, e.getMessage, "Look Error", JOptionPane.ERROR_MESSAGE)
				}
			}

			publish(new ZColStatusEvent(this, properties))
		case e : ZCmdEvent =>
			val src = e.source
			e.command match {
				case "Up" if(wnds.contains(src) && wnds.length > 1) => 
					wnds.indexOf(src) match {
						case 0 =>
							wnds = wnds.tail :+ wnds.head
							refresh
						case i => 
							wnds = wnds.filterNot(_ == src)
							wnds = wnds.patch(i-1, src :: Nil, 0)
							refresh
					}
				case "Dn" if(wnds.contains(src) && wnds.length > 1) => 
					val i = wnds.indexOf(src) 
					if((i+1) == wnds.length) {
						wnds = wnds.last :: wnds.init
						refresh
					} else {
						wnds = wnds.filterNot(_ == src)
						wnds = wnds.patch(i+1, src :: Nil, 0)
						refresh
					}
				case "Lt" =>
					this -= src
					publish(new ZMoveWndEvent("Lt", this, src))
				case "Rt" => 
					this -= src
					publish(new ZMoveWndEvent("Rt", this, src))
				case "Close" => closeWnd(src)
				case "CLOSE" => this -= src
				case "Zerox" =>
					val w = wnd(src.rawPath + "+Zerox")
					this += w
					w.body.text = src.body.text
					w.root = src.root
				case "Mark" =>
					val n = src.rawPath + "+Mark"
					val w = rawPathWindow(n).getOrElse {
						val nw = wnd(n)
						this += nw
						nw.root = src.root
						nw.body.text = ""
						nw
					}
					val rawP = src.rawPath
					val baseName = if(rawP.lastIndexOf(ZUtilities.separator) != -1)
						rawP.substring(rawP.lastIndexOf(ZUtilities.separator) + 1)
					else rawP

					w.body.text = w.body.text + baseName + ":" + (src.body.currLineNo + 1) + " " + src.body.line()
				case ZCol.reExternalCmd(op, cmd) if(op.equals(">")) =>
					val n = src.rawPath + "+Results"
					val w = rawPathWindow(n).getOrElse {
						val nw = wnd(n)
						this += nw
						nw
					}
					w.root = src.root
					w.externalCmd(">", cmd, Some(Option(src.body.selected).getOrElse(src.body.text)))
				case ZCol.reExternalCmd(op, cmd) if(op.equals("!")) => command("! " + cmd)
				case "Props" =>
					val n = src.rawPath + "+Props"
					val w = rawPathWindow(n).getOrElse {
						val nw = wnd(n + " Close")
						this += nw
						nw
					}
					w.root = src.root
					w.body.text = src.properties.toSeq.sortBy(_._1)
						.map { case (k, v) => s"$k = $v" }
						.mkString(util.Properties.lineSeparator)
					w.dirty = false
				case cmd =>
					val n = src.rawPath + "+Results"
					val w = rawPathWindow(n).getOrElse {
						val nw = cmdWnd(n)
						nw.command("Scroll")
						this += nw
						nw
					}
					if(w.tag.text.indexOf(cmd) == -1)  w.tag.text += " ! " + cmd
					w.root = src.root
					w.command("< " + cmd)
			}
		case e : ZScriptEvent =>
			val w = resultsWindowFor(e.source)
			w.runScript(e.scriptPath, e.args, Map(
				"Z_FILE"      -> e.source.path,
				"Z_DIR"       -> e.source.rootPath,
				"Z_SELECTION" -> Option(e.source.body.selected).getOrElse("")))
		case e : ZDiagnosticsReadyEvent =>
			val src = e.source
			val n   = src.rawPath + "+Diagnostics"
			val w   = rawPathWindow(n).getOrElse {
				val nw = wnd(n)
				this += nw
				nw
			}
			w.root = src.root
			w.body.text = e.content
		case e : ZLookEvent =>
			look(e.path, false)
		case e : ZStatusEvent => publish(new ZStatusEvent(e.source, e.properties))
	}

	listenTo(tag.keys)
	reactions += {
		case e : KeyPressed if((e.key == Key.F) && e.peer.isControlDown()) =>	
			var p = ZUtilities.selectedText(tag, tag.caret.dot)
			if(p.isEmpty)  p = "."

			val fc = new FileChooser(new File(p)) {
				title = "Path Selection"
				fileHidingEnabled = false
				multiSelectionEnabled = false
				fileSelectionMode = FileChooser.SelectionMode.FilesAndDirectories
			}

			if(fc.showOpenDialog(this) == FileChooser.Result.Approve)  {
				var fcp = fc.selectedFile.getPath
				val cp = new File(p).getCanonicalPath

				if(fcp.startsWith(cp) && fcp.length != cp.length)  fcp = fcp.substring(cp.length + 1).trim
				tag.selected = fcp
			}
	}

	def command(cmds : String) = {
		for(cmd <- cmds.linesIterator.map(_.trim)) {
			cmd.trim match {
				case "Lt" => publish(new ZMoveColEvent("Lt", this))
				case "Rt" => publish(new ZMoveColEvent("Rt", this))
				case "New" => this += genWnd()
				case "Sort" => 
					wnds = wnds.sortWith((a, b) => a.path.compareTo(b.path) < 0)
					refresh
				case "Props" =>
					val w = rawPathWindow("+Props").getOrElse {
						val nw = wnd("+Props Close")
						this += nw
						nw
					}
					w.body.text = properties.toSeq.sortBy(_._1)
						.map { case (k, v) => s"$k = $v" }
						.mkString(util.Properties.lineSeparator)
					w.dirty = false
				case "CloseCol" => 
					var cancel = false
					wnds.foreach((w) => if(!cancel) {
						if(!closeWnd(w)) cancel = true
					})

					if(!cancel)  publish(new ZCmdCloseColEvent(this))
				case ZCol.reExternalCmd(op, cmd) =>
					val w = cmdWnd("+Cmd")
					this += w
					w.tag.text = w.tag.text + " ! " + cmd
					w.command("! " + cmd)
				case ZCol.reFilteredExec(p, re, c)  if(p.equals("X")) =>
					wnds.filter(_.rawPath.matches(re)).foreach(_.command(c))
				case ZCol.reFilteredExec(p, re, c)  if(p.equals("Y")) =>
					wnds.filterNot(_.rawPath.matches(re)).foreach(_.command(c))
				case ZUtilities.reTagFont(font, pt) =>
					tag.font = new Font(font, Font.PLAIN, pt.toInt)
					wnds.foreach(_.command(cmd))
				case ZUtilities.reDirQuoted(d) =>
					val ed = ZUtilities.expandPath(d, currentDir)
					val f = if(ZUtilities.isFullPath(ed)) ed else (currentDir + ZUtilities.separator + ed)
					if(new File(f).isDirectory) currentDir = new File(f).getCanonicalPath
					wnds.foreach(_.command(cmd))
				case ZUtilities.reDir(d) =>
					val ed = ZUtilities.expandPath(d, currentDir)
					val f = if(ZUtilities.isFullPath(ed)) ed else (currentDir + ZUtilities.separator + ed)
					if(new File(f).isDirectory) currentDir = new File(f).getCanonicalPath
					wnds.foreach(_.command(cmd))
				case ZScripts.reScriptAll(name, args) =>
					ZScripts.resolve(name, currentDir) match {
						case Right(f) => runScriptOnWindows(f.getPath, args)
						case Left(searched) => ZScripts.showError(name, searched)
					}
				case ZScripts.reScript(name, args) =>
					ZScripts.resolve(name, currentDir) match {
						case Right(f) =>
							val w = cmdWnd("+Cmd")
							w.command("Scroll")
							this += w
							w.runScript(f.getPath, args, Map("Z_DIR" -> currentDir))
						case Left(searched) => ZScripts.showError(name, searched)
					}
				case c =>
					if(!look(c, false)) wnds.foreach((w) => if(!w.look(c)) w.command(c))
			}

			prevCmd = "Cmd: " + cmd
		}
	}

	def look(txt : String, traverse : Boolean = true) : Boolean = {
		if(txt == null || txt.trim.isEmpty)  return true

		txt match {
			case ZCol.reFileLoc(f, loc) => fileLook(f, loc)
			case s =>
				val expanded = ZUtilities.expandPath(s, currentDir)
				val f = if(ZUtilities.isFullPath(expanded)) expanded else (currDir + ZUtilities.separator + expanded)

				if(new File(f).exists) {
					val o  = pathWindow(expanded)
					if(o.isEmpty)  {
						val w = wnd(expanded)
						this += w
						w.command("Get")
					}
				} else {
					if(traverse) wnds.foreach((w) => if(!w.look(txt)) w.command(txt))
					else return false
				}
		}

		prevCmd = "Look: " + txt
		return true
	}

	def +=(w : ZWnd) = {
		wnds = wnds :+ w
		listenTo(w)
		refresh
	}

	def runScriptOnWindows(scriptPath: String, args: String): Unit =
		wnds.foreach { src =>
			resultsWindowFor(src).runScript(scriptPath, args, Map(
				"Z_FILE"      -> src.path,
				"Z_DIR"       -> src.rootPath,
				"Z_SELECTION" -> Option(src.body.selected).getOrElse("")))
		}

	private def resultsWindowFor(src: ZWnd): ZWnd = {
		val n = src.rawPath + "+Results"
		val w = rawPathWindow(n).getOrElse {
			val nw = cmdWnd(n)
			nw.command("Scroll")
			this += nw
			nw
		}
		w.root = src.root
		w
	}

	def refresh = {
		layout(render(wnds)) = BorderPanel.Position.Center
		revalidate()
	}

	def -=(w : ZWnd)  = {
		deafTo(w)
		wnds = wnds.filterNot(_ == w)
		refresh
	}

	def render( wl : List[ZWnd]) : Component = {
		if(wl.isEmpty) return new BorderPanel
		if(wl.size == 1) return wl.head

		new SplitPane(Orientation.Horizontal, wl.head, render(wl.tail)) {
			oneTouchExpandable = true
			resizeWeight = 0.5
			continuousLayout = true
		}
	}

	def genWnd(p : String = "+", tag : String = ZCol.wndTagLine) = new ZWnd((if(p.contains(" ")) s"'$p'" else p) + " " + tag, "", currentDir)

	def closeWnd(w : ZWnd) : Boolean  = {
		if(w.dirty && !ZWnd.isScratchBuffer(w.rawPath) && (new File(w.path)).isFile) {
			if(JOptionPane.showConfirmDialog(null, "[" + w.path + " is dirty]. Continue?", "Close Confirmation", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.CANCEL_OPTION)
				return false
		}

		w.close()
		this -= w
		true
	}

	def fileLook(f : String, loc : String) = {
		val ef = ZUtilities.expandPath(f, currentDir)
		val w  = rawPathWindow(ef).orElse(pathWindow(ef)).getOrElse {
			val n = wnd(ef)
			this += n
			n.command("Get")
			n
		}

		if(!loc.isEmpty()) w.look(loc)
		if(!w.tag.text.contains(loc)) w.tag.text = w.tag.text + " " + loc
	}

	def pathWindow(p : String) = {
		val cp = new File(p).getCanonicalPath
		wnds.find((w) => if(ZWnd.isScratchBuffer(w.rawPath)) false else w.path.equals(cp) )
	}

	def rawPathWindow(p : String) = wnds.find( (w) => w.rawPath.equals(p) )

	def properties : Map[String, String] = {
		var p = new HashMap[String, String]
		p += "window.count" -> String.valueOf(wnds.length)
		p += "command.prev" -> prevCmd
		p += "tag.font"     -> tag.font.getFontName
		p += "tag.size"     -> tag.font.getSize.toString
		p
	}

	def dump : Map[String, String] = {
		var p = properties
		var i = 1
		wnds.foreach((w) => {
			w.dump.foreach((t) => p += "window." + i.toString + "." + t._1 -> t._2)
			i = i + 1
		})

		p += "tag.text" -> tag.text
		p
	}

	def load(p : Map[String, String], prefix : String = "") = {
		val cnt = p.getOrElse(prefix + "window.count", "0").toInt
		prevCmd = p.getOrElse(prefix + "command.prev", "")
		tag.text = p.getOrElse(prefix + "tag.text", ZCol.colTagLine)
		tag.font = new Font(
			p.getOrElse(prefix + "tag.font", ZFonts.defaultTag.getFontName),
			Font.PLAIN,
			p.getOrElse(prefix + "tag.size", ZFonts.defaultTag.getSize.toString).toInt)

		for(i <- 1 to cnt)
		{
			val w = genWnd()
			this += w
			w.load(p, prefix + "window." + i.toString + ".")
		}
	}
}

object ZCol {
	val colTagLine = "CloseCol Close New Sort "
	val wndTagLine = "Get Put Zerox Close | Undo Redo Wrap Ln Indent Mark Bind "
	val cmdTagLine = "Close | Undo Redo Wrap Kill Clear Font Scroll Input "

	val reExternalCmd = """(?s)\s*([>!])\s*(.+)\s*$""".r
	val reFileLoc = """(.+)(:[0-9]+|:/.+)""".r
	val reFilteredExec = """(?s)\s*(X|Y)\s+'([^']+)'\s+(.+)\s*$""".r
}

class ZCmdCloseColEvent(val source : ZCol) extends Event
class ZMoveWndEvent(val dir : String, val source : ZCol, val wnd : ZWnd) extends Event
class ZMoveColEvent(val dir : String, val source : ZCol) extends Event
class ZColStatusEvent(val source : ZCol, val properties : Map[String, String]) extends Event
