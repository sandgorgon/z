/*
Copyright (c) 2011-2014. Ramon de Vera Jr.
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
import collection.immutable.List
import collection.immutable.HashMap

import java.io.File
import java.awt.{Color, Font}
import javax.swing.{JOptionPane, SwingUtilities, BorderFactory}

class ZCol extends BorderPanel {
	var wnds : List[ZWnd] = Nil

	var colorTBack = new Color(0xFF, 0xFF, 0xFF)
	var colorTFore = new Color(0x00, 0x00, 0x00)
	var colorTCaret = new Color(0x00, 0x00, 0x00)
	var colorTSelBack =new Color(0x96, 0x96, 0x96)
	var colorTSelFore = new Color(0xFF, 0xFF, 0xFF)
	var prevCmd = ""
	var dragSel = false
	var dragSelMark = -1

	var tag = new ZTextArea(ZCol.colTagLine)
	tag.colors(colorTBack, colorTFore,  colorTCaret, colorTSelBack, colorTSelFore )
	tag.border = BorderFactory.createMatteBorder(0,0,1,0, Color.BLACK)
	tag.font = new Font("Bitstream Vera Sans", Font.PLAIN, 12)

	var body : Panel = new BorderPanel

	layout(tag) = BorderPanel.Position.North
	layout(body) = BorderPanel.Position.Center

	deafTo(this)
	listenTo(tag.mouse.moves, tag.mouse.clicks) 
	reactions += {
		case e : MouseEntered =>
			e.source.requestFocus
			publish(new ZColStatusEvent(this, properties))
		case e : MousePressed => if(SwingUtilities.isMiddleMouseButton(e.peer) || SwingUtilities.isRightMouseButton(e.peer))
			dragSelMark = tag.peer.viewToModel(e.point)
		case e : MouseDragged => if(SwingUtilities.isMiddleMouseButton(e.peer) || SwingUtilities.isRightMouseButton(e.peer)) {
			dragSel = true
			if(dragSelMark != -1) {
				tag.peer.setCaretPosition(dragSelMark)
				dragSelMark = -1
			}

			tag.peer.moveCaretPosition(tag.peer.viewToModel(e.point))
		}
		case e : MouseReleased =>
			if(dragSel) {
				if(SwingUtilities.isMiddleMouseButton(e.peer))  command(ZUtilities.selectedText(tag, e))
				else if(SwingUtilities.isRightMouseButton(e.peer))  look(ZUtilities.selectedText(tag, e))
			}
			dragSel = false
			dragSelMark = -1
		case e : MouseClicked =>			
			if(SwingUtilities.isMiddleMouseButton(e.peer)
				|| (e.peer.isShiftDown && SwingUtilities.isRightMouseButton(e.peer))) 
				command(ZUtilities.selectedText(e.source.asInstanceOf[ZTextArea], e))
			else if(SwingUtilities.isRightMouseButton(e.peer)) 
				look(ZUtilities.selectedText(e.source.asInstanceOf[ZTextArea], e))
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
					val o = rawPathWindow(n)
					var w : ZWnd = null

					if(o == None) {
						w = wnd(n)
						this += w
						w.root = src.root
						w.body.text = ""
					} else w = o.get

					var p = src.rawPath
					if(p.lastIndexOf(ZUtilities.separator) != -1)  p = p.substring(p.lastIndexOf(ZUtilities.separator) + 1)

					w.body.text = w.body.text + p + ":" + (src.body.currLineNo + 1) + " " + src.body.line()
				case ZCol.reExternalCmd(op, cmd) if(op.equals(">")) =>
					val n = src.rawPath + "+Results"
					val o =  rawPathWindow(n)
					var w : ZWnd = null

					if(o == None) {
						w = wnd(n)
						this += w
					} else w = o.get

					w.root = src.root
					w.externalCmd(">", cmd, if(src.body.selected == null) src.body.text else src.body.selected)
				case ZCol.reExternalCmd(op, cmd) if(op.equals("!")) => command("! " + cmd)
				case cmd => 
					val n = src.rawPath + "+Results"
					val o = rawPathWindow(n)
					var w : ZWnd = null

					if(o == None) {
						w =  wnd(n)
						w.command("Scroll")
						this += w
					} else w = o.get

					if(w.tag.text.indexOf(cmd) == -1)  w.tag.text += " ! " + cmd
					w.root = src.root
					w.command("< " + cmd)
			}
		case e : ZLookEvent =>
			val src = e.source.asInstanceOf[ZWnd]
			
			look(e.path, false)
			/*
			e.path match {
				case  ZCol.reFileLoc(f, loc) => fileLook(f, loc)
				case f => 
					if(new File(f).exists) {
						var o  = pathWindow(f)
						if(o == None)  {
							var w = wnd( if(f.startsWith(src.root + ZUtilities.separator)) f.substring(src.root.length + 1) else f)
							w.root = src.root
							this += w
							w.command("Get")
						}
					}
			}
			*/
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
		for(cmd <- cmds.lines.map(_.trim)) {
			cmd.trim match {
				case "Lt" => publish(new ZMoveColEvent("Lt", this))
				case "Rt" => publish(new ZMoveColEvent("Rt", this))
				case "New" => this += wnd()
				case "Sort" => 
					wnds = wnds.sortWith((a, b) => a.path.compareTo(b.path) > 0)
					refresh
				case "CloseCol" => 
					var cancel = false
					wnds.foreach((w) => if(!cancel) {
						if(!closeWnd(w)) cancel = true
					})

					if(!cancel)  publish(new ZCmdCloseColEvent(this))
				case ZCol.reExternalCmd(op, cmd) =>
					var w = wnd("+Cmd")
					this += w
					w.tag.text = w.tag.text + " ! " + cmd
					w.command("! " + cmd)
				case ZCol.reFilteredExec(p, re, c)  if(p.equals("X")) =>
					wnds.filter(_.rawPath.matches(re)).foreach(_.command(c))
				case ZCol.reFilteredExec(p, re, c)  if(p.equals("Y")) =>
					wnds.filterNot(_.rawPath.matches(re)).foreach(_.command(c))
				case c => wnds.foreach(_.command(c))
			}

			prevCmd = "Cmd: " + cmd
		}
	}

	def look(txt : String, traverse : Boolean = true) {
		if(txt == null || txt.trim.isEmpty)  return

		txt match {
			case ZCol.reFileLoc(f, loc) => fileLook(f, loc)				
			case s => 
				if(new File(s).exists) {
					var o  = pathWindow(s)
					if(o == None)  {
						var w = wnd(s)
						this += w
						w.command("Get")
					}
				} else if(traverse)  wnds.foreach((w) => w.look(s))
		}

		prevCmd = "Look: " + txt
	}

	def +=(w : ZWnd) = {
		wnds = wnds :+ w
		listenTo(w)
		refresh
	}

	def refresh = {
		layout(render(wnds)) = BorderPanel.Position.Center
		revalidate
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

	def wnd(p : String = "+") = new ZWnd(p + " " + ZCol.wndTagLine,  "")

	def closeWnd(w : ZWnd) : Boolean  = {
		if(w.dirty && !ZWnd.isScratchBuffer(w.rawPath) && (new File(w.path)).isFile) {
			if(JOptionPane.showConfirmDialog(null, "[" + w.path + " is dirty]. Continue?", "Close Confirmation", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.CANCEL_OPTION)
				return false
		}
			
		this -= w
		true
	}

	def fileLook(f : String, loc : String) = {
		var  l = rawPathWindow(f)
		if(l == None)  l = pathWindow(f)

		val w = if(l != None)  l.get else { 
				var n = wnd(f)
				this += n
				n.command("Get")
				n
			}

		if(!loc.isEmpty()) w.look(loc)
		w.tag.text = w.tag.text + " " + loc
	}

	def pathWindow(p : String) = {
		val cp = new File(p).getCanonicalPath
		wnds.find( (w) => new File(w.path).getCanonicalPath.equals(cp) )
	}

	def rawPathWindow(p : String) = wnds.find( (w) => w.rawPath.equals(p) )

	def properties : Map[String, String] = {
		var p = new HashMap[String, String]
		p += "window.count" -> String.valueOf(wnds.length)
		p += "command.prev" -> prevCmd
		p
	}

	def dump : Map[String, String] = {
		var p = properties
		var i = 1
		wnds.foreach((w) => {
			w.dump.foreach((t) => p += "window." + i.toString + "." + t._1 -> t._2)
			i = i + 1
		})

		p
	}

	def load(p : Map[String, String], prefix : String = "") = {
		var cnt = p.getOrElse(prefix + "window.count", "0").toInt
		prevCmd = "Cmd: " + p.getOrElse("command.prev", "")
		for(i <- 1 to cnt)
		{
			var w = wnd()
			this += w
			w.load(p, prefix + "window." + i.toString + ".")
		}
	}
}

object ZCol {
	val colTagLine = "CloseCol Close New Sort"
	val wndTagLine = "Get Put Zerox Close | Undo Redo Wrap Indent Mark"
	val cmdTagLine = "Close | Undo Redo Wrap Kill Clear Font Scroll Input"

	val reExternalCmd = """(?s)\s*([>!])\s*(.+)\s*$""".r
	val reFileLoc = """(.+)(:[0-9]+|:/.+)""".r
	val reFilteredExec = """(?s)\s*(X|Y)\s+'([^']+)'\s+(.+)\s*$""".r
}

class ZCmdCloseColEvent(val source : ZCol) extends Event
class ZMoveWndEvent(val dir : String, val source : ZCol, val wnd : ZWnd) extends Event
class ZMoveColEvent(val dir : String, val source : ZCol) extends Event
class ZColStatusEvent(val source : ZCol, val properties : Map[String, String]) extends Event
