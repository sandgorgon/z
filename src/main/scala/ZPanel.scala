/*
Copyright (c) 2011-2016. Ramon de Vera Jr.
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

import swing.{BorderPanel, Component, Orientation, SplitPane,FileChooser}
import swing.event.{Event, KeyPressed, Key, MouseEvent, MouseEntered, MousePressed, MouseDragged, MouseReleased, MouseClicked}
import javax.swing.JOptionPane
import collection.immutable.{Map, HashMap}
import io.Source

import java.awt.{Color, Font}
import javax.swing.{SwingUtilities, BorderFactory}
import java.io.File

class ZPanel(initTagText: String) extends BorderPanel {
	var cols : List[ZCol] = Nil

	var colorTBack = new Color(0xFF, 0xFF, 0xFF)
	var colorTFore = new Color(0x00, 0x00, 0x00)
	var colorTCaret = new Color(0x00, 0x00, 0x00)
	var colorTSelBack =new Color(0x96, 0x96, 0x96)
	var colorTSelFore = new Color(0xFF, 0xFF, 0xFF)
	var  dragSel = false
	var dragSelMark = -1
	var prevCmd = ""

	var tag = new ZTextArea(initTagText)
	tag.colors(colorTBack, colorTFore,  colorTCaret, colorTSelBack, colorTSelFore )
	tag.border = BorderFactory.createMatteBorder(0,0,1,0, Color.BLACK)
	tag.font = ZFonts.SANS_SERIF_MONO

	layout(tag) = BorderPanel.Position.North
	layout(render(cols)) = BorderPanel.Position.Center

	deafTo(this)
	listenTo(tag.mouse.moves, tag.mouse.clicks)
	reactions += {
		case e : MouseEntered =>
			e.source.requestFocus
			publish(new ZPanelStatusEvent(this, properties))
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
				if(SwingUtilities.isMiddleMouseButton(e.peer) || SwingUtilities.isRightMouseButton(e.peer))  command(ZUtilities.selectedText(tag, e))
			}
			dragSel = false
			dragSelMark = -1
		case e : MouseClicked =>	
			if(SwingUtilities.isRightMouseButton(e.peer))  {
				try {
					command(ZUtilities.selectedText(e.source.asInstanceOf[ZTextArea], e))
				} catch {
					case e : Throwable => JOptionPane.showMessageDialog(null, e.getMessage, "Look Error", JOptionPane.ERROR_MESSAGE)
				}
			}
		case e : ZCmdCloseColEvent => this -= e.source
		case e : ZMoveColEvent if(cols.length > 1 && cols.contains(e.source))=>
			val src = e.source
			e.dir match {
				case "Lt" =>
					cols.indexOf(src) match {
						case 0 =>
							cols = cols.tail :+ cols.head
							refresh
						case i =>
							cols = cols.filterNot(_ == src)
							cols = cols.patch(i - 1, src :: Nil, 0)
							refresh
					}
				case "Rt" =>
					val i = cols.indexOf(src)
					if((i + 1) == cols.length) {
						cols = cols.last :: cols.init
						refresh
					} else {
						cols = cols.filterNot(_ == src)
						cols = cols.patch(i+1, src :: Nil, 0)
						refresh						
					}
			}
		case e : ZMoveWndEvent => e.dir  match {
			case "Rt" => 
				val col = nextCol(e.source) 
				if(col != null) col += e.wnd
				else e.source += e.wnd
			case "Lt" => 
				val col = prevCol(e.source)
				if(col != null) col += e.wnd
				else e.source += e.wnd
		}

		case e : ZStatusEvent => publish(new ZStatusEvent(e.source, e.properties))
		case e : ZColStatusEvent => 	publish(new ZColStatusEvent(e.source, e.properties))
	}

	listenTo(tag.keys)
	reactions += {
		case e : KeyPressed if((e.key == Key.F) && e.peer.isControlDown()) =>	
			var p = ZUtilities.selectedText(tag, tag.caret.dot)
			if(p.isEmpty) p = "."
		
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

	def command(cmds : String) = if(cmds != null && !cmds.trim.isEmpty) {
		for(cmd <- cmds.split('\n')) {
			cmd.trim match {
				case "NewCol" => this += new ZCol
				case "Load" => load()
				case ZPanel.reLoad(p) => load(p)
				case "Dump" => dump()
				case ZPanel.reDump(p) => dump(p)
				case "Dir" => println("TODO: Dir command")
				case "Fonts" => fonts
				case "Help" => help
				case ZCol.reExternalCmd(op, cmd) =>
					if(cols.length < 1)  this += new ZCol
					cols.last.command("! " + cmd)
				case c => cols.foreach(_.command(c))
			}

			prevCmd = "Cmd: " + cmd
			publish(new ZPanelStatusEvent(this, properties))
		}
	}

	def look(txt : String) : Boolean = {
		var  retval = true
		if(txt == null || txt.trim.isEmpty)  return retval

		txt match {
			case ZCol.reFileLoc(f, loc) =>
				if(cols.length < 1) this += new ZCol
				cols.last.fileLook(f, loc)				
			case s => 
				if(new File(s).exists) {
					if(cols.length < 1)  this += new ZCol
					cols.last.look(s)
				} else {
					cols.foreach((c) => retval = c.look(s))
				}
		}

		prevCmd = "Look: " + txt	
		publish(new ZPanelStatusEvent(this, properties))
		return retval
	}

	def +=(col : ZCol):ZCol = {
		cols = cols :+ col
		refresh
		listenTo(col)
		col
	}

	def refresh = {
		layout(render(cols)) = BorderPanel.Position.Center
		revalidate
	}

	def -=(col : ZCol):ZCol = {
		deafTo(col)
		cols = cols.filterNot(_ == col)
		refresh
		col
	}


	def render(l : List[ZCol] = Nil) : Component = {
		if(l == Nil) return new BorderPanel
		if(l.size == 1) return l.head

		new SplitPane(Orientation.Vertical, l.head, render(l.tail)) {
			oneTouchExpandable = true
			resizeWeight = 0.5
			continuousLayout = true
		}
	}	

	def nextCol(col : ZCol) : ZCol = {
		var found = false
		cols.foreach(c => {
			if(found) return c;
			if(c == col) found = true				
		})

		return null
	}

	def prevCol(col : ZCol) : ZCol = {
		var prev : ZCol = null
		cols.foreach(c => {
			if(c == col)  return prev
			prev = c
		})

		return null
	}

	def fonts = {
		val col = if(cols.isEmpty) this += new ZCol  else cols.last
		val w = col.wnd("+Fonts Close")
		w.body.text = ZFonts.familyNames mkString(util.Properties.lineSeparator)
		w.dirty = false
		col += w
	}

	def help = {
		var col  = if(cols.isEmpty) this += new ZCol else cols.last
		val w = col.wnd("+Help")
		w.command("Scroll")
		w.body.text = Source.fromURL(this.getClass.getResource("help/main.txt")).mkString
		w.dirty = false
		col += w
	}

	def populate(args : Array[String]) = {
		var w : ZWnd = null
		var col : ZCol = null

		var action = ""
		args.foreach((a) => {
			a match {
				case "-c" => 
					col = this += new ZCol
					action = ""
				case "-l" => action = "look"
				case "-cl" => action = "colLook"
				case "-al" => action = "appLook"					
				case "-!" => action = "command"
				case "-c!" => action = "colCommand"
				case "-a!" => action = "appCommand"
				case "-r" => action = ""
				case txt =>
					action match {
						case "look" if(w != null) =>
							w.tag.text = w.tag.text + " " + txt
							w.look(txt)
						case "colLook" if(col != null) => 
							col.tag.text = col.tag.text + " " + txt
							col.look(txt)
						case "appLook" => 
							tag.text = tag.text + " " + txt
							look(txt)
						case "command" if(w != null) => 
							w.tag.text = w.tag.text + " " + txt
							w.command(txt)
						case "colCommand" if(col != null)  => 
							col.tag.text = col.tag.text + " " + txt
							col.command(txt)
						case "appCommand" => 
							tag.text = tag.text + " " + txt
							command(txt) 
						case _ => 
							if(col == null) col = this += new ZCol
							w = col.wnd(a)
							col += w
							w.command("Get")
					}
			}
		})
	}

	def load(s : String = "z.dump") = {
		val path = new File(s)

		if(path.exists) {
			var p = ZSettings.load(path)
			var cnt = p.getOrElse("column.count", "0").toInt
			prevCmd = "Cmd: " + p.getOrElse("command.prev", "")
			for(i <- 1 to cnt) {
				val c = this += new ZCol
				c.load(p, "column." + i.toString + "." )
			}
		}
	}

	def properties : Map[String, String] = {
		var p = new HashMap[String, String]
		p += "column.count" -> String.valueOf(cols.length)
		p += "command.prev" -> prevCmd
		p
	}

	def dump(s : String = "z.dump") = {
		var p = properties
		var i = 1
		cols.foreach((c) => {
			c.dump.foreach((t) => p += "column." + i.toString + "." + t._1 -> t._2)
			i = i + 1
		})

		ZSettings.dump(p, new File(s), "Z Dump") 
	}
}

object ZPanel {
	val reLoad = """Load\s+(.+)""".r
	val reDump = """Dump\s+(.+)""".r
}

class ZPanelStatusEvent(val source : ZPanel, val properties : Map[String, String]) extends Event
