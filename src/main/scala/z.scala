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

import swing.{SwingApplication, BorderPanel, Label, Alignment, MainFrame, Orientation}
import collection.immutable.{Map, HashMap}

import java.io.File
import java.awt.{Toolkit, Dimension, Font, Window}
import java.lang.reflect.Method;

object z extends SwingApplication {
	ZFonts.registerFonts

	var frame: MainFrame = scala.compiletime.uninitialized

	val mainPanel = new ZPanel("Help NewCol History Put Dump Load Dir ")

	val statusLeft = new Label("") {
		horizontalAlignment = Alignment.Left
		font = ZFonts.SANS_SERIF_MONO
	}
	val statusRight = new Label("") {
		horizontalAlignment = Alignment.Right
		font = ZFonts.SANS_SERIF_MONO
	}
	val statusBar = new BorderPanel {
		layout(statusLeft)  = BorderPanel.Position.Center
		layout(statusRight) = BorderPanel.Position.East
	}

	val MainWindow = new BorderPanel {
		layout(mainPanel) = BorderPanel.Position.Center
		layout(statusBar) = BorderPanel.Position.South
	}

	listenTo(mainPanel)
	reactions += {
		case e : ZStatusEvent =>
			val p = e.properties
			statusLeft.text =
						p.getOrElse("line.current", "?") + "/" + p.getOrElse("lines", "?") + "@" + p.getOrElse("column.current", "?") +
						" |  Tab: " + p.getOrElse("tab.size", "?") +
						" | " + (if(p.getOrElse("line.wrap",    "false") == "true") "Wrap"   else "NoWrap") +
						" | " + (if(p.getOrElse("indent.auto",  "false") == "true") "Indent" else "NoIndent") +
						" | " + (if(p.getOrElse("scroll",       "false") == "true") "Scroll" else "NoScroll") +
						" | " + p.getOrElse("body.font.current", "?") + " " + p.getOrElse("body.font.current.size", "?") +
						(if(p.getOrElse("bind",        "false") == "true") " | Bind"   else "") +
						(if(p.getOrElse("lsp",         "false") == "true") " | LSP: " + p.getOrElse("lsp.root", "") +
						{ val st = p.getOrElse("lsp.status", ""); if (st.nonEmpty) s" ($st)" else "" } else "") +
						(if(p.getOrElse("hilite",      "false") == "true") " | Hilite" else "") +
						(if(p.getOrElse("interactive", "false") == "true") " | Input: " + p.getOrElse("interactive.prompt", "") else "")

		case e : ZStatusClearEvent => statusLeft.text = ""

		case e : ZPanelStatusEvent =>
						e.properties.get("app.dir").foreach { d =>
							val f = new java.io.File(d)
							val label = Option(f.getParentFile).map(_.getName).filter(_.nonEmpty)
								.fold(f.getName)(par => s"$par/${f.getName}")
							frame.title = label + " — z"
						}

		case e : ZCmdEchoEvent =>
						statusRight.text = s"[${e.timestamp}] ${e.cmd}"
	}

	def top = new MainFrame {
		title = {
			val f = new File(".").getCanonicalFile
			val label = Option(f.getParentFile).map(_.getName).filter(_.nonEmpty)
				.fold(f.getName)(par => s"$par/${f.getName}")
			label + " — z"
		}
		iconImage = Toolkit.getDefaultToolkit().createImage(resourceFromClassloader("images/z.png"))

		contents = MainWindow

		override def closeOperation() = {
			val zDir     = new File(util.Properties.userHome + ZUtilities.separator + ".z")
			val settings = new File(zDir, "settings")
			if(!zDir.exists) zDir.mkdirs()
			var p = if(settings.exists) ZSettings.load(settings) else new HashMap[String, String]
			p += "app.width"   -> size.getWidth.toInt.toString
			p += "app.height"  -> size.getHeight.toInt.toString
			p += "view.rotated" -> mainPanel.rotated.toString
			for (elem <- ZMarkdownTheme.elements) {
				val font = ZMarkdownTheme.fontFor(elem)
				if (font != null) {
					p += s"md.font.$elem"      -> font.getFamily
					p += s"md.font.$elem.size" -> font.getSize.toString
				}
			}
			ZSettings.dump(p, settings, "Z Global Settings")
			ZLspManager.shutdown()
			System.exit(0)
		}
	}

	override def startup(args: Array[String]) = {
		val zDir     = new File(util.Properties.userHome + ZUtilities.separator + ".z")
		val settings = new File(zDir, "settings")
		var p : Map[String, String] = Map.empty

		// Migrate: if ~/.z is an old flat file, move its content to ~/.z/settings
		if(zDir.exists && zDir.isFile) {
			val old = ZSettings.load(zDir)
			zDir.delete()
			zDir.mkdirs()
			ZSettings.dump(old, settings, "Z Global Settings")
		} else if(!zDir.exists) {
			zDir.mkdirs()
		}

		p = if (settings.exists) ZSettings.load(settings) else Map.empty
		if (p.get("app.width").flatMap(_.toIntOption).forall(_ < 10))  p += "app.width"  -> "600"
		if (p.get("app.height").flatMap(_.toIntOption).forall(_ < 10)) p += "app.height" -> "400"

		ZCol.colTagLine      = p.getOrElse("tag.col", ZCol.colTagLine)
		ZCol.wndTagLine      = p.getOrElse("tag.wnd", ZCol.wndTagLine)
		ZCol.cmdTagLine      = p.getOrElse("tag.cmd", ZCol.cmdTagLine)
		mainPanel.tag.text   = p.getOrElse("tag.app", mainPanel.tag.text)
		mainPanel.rotated    = p.getOrElse("view.rotated", "false").toBoolean
		p.get("history.limit").flatMap(_.toIntOption).foreach(CommandLog.setLimit)

		// Restore per-element Markdown fonts (only if both family and size keys exist)
		for {
			elem   <- ZMarkdownTheme.elements
			family <- p.get(s"md.font.$elem")
			size   <- p.get(s"md.font.$elem.size").flatMap(_.toIntOption)
		} ZMarkdownTheme.setFont(elem, family, size)

		// Register custom Markdown token maker before any file is opened
		org.fife.ui.rsyntaxtextarea.TokenMakerFactory.getDefaultInstance
			.asInstanceOf[org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory]
			.putMapping("text/markdown", classOf[ZMarkdownTokenMaker].getName)

		ZLspManager.loadConf()
		ZScripts.load()
		ZPlumbing.load()
		val scriptsDir = new File(zDir, "scripts")
		if (!scriptsDir.exists()) scriptsDir.mkdirs()
		frame = top
		frame.preferredSize = new Dimension(p.getOrElse("app.width", "600").toInt, p.getOrElse("app.height", "400").toInt)
		frame.pack()
		frame.centerOnScreen()
		frame.visible = true
		mainPanel.populate(args)

		System.getProperty("os.name") match {
			case mac if mac.toLowerCase().startsWith("mac os x") =>
				enableOSXFullscreen(frame.peer)
				setOSXDockIcon(frame)
			case _ =>
		}
	}

	def enableOSXFullscreen(window: Window): Unit = {
		try {
			val util = Class.forName("com.apple.eawt.FullScreenUtilities");
			val method = util.getMethod("setWindowCanFullScreen", classOf[java.awt.Window], java.lang.Boolean.TYPE)
			method.invoke(null, window, Boolean.box(true))
		} catch {
			case e: Exception => e.printStackTrace(System.err);
		}
	}

	def setOSXDockIcon(frame: MainFrame): Unit = {
		try {
			val appClass = Class.forName("com.apple.eawt.Application");
			val getApplication = appClass.getMethod("getApplication");
			val application = getApplication.invoke(appClass);
			val method = application.getClass().getMethod("setDockIconImage", classOf[java.awt.Image])
			method.invoke(application, frame.iconImage)
		} catch {
			case e: Exception => e.printStackTrace(System.err);
		}
	}

	def resourceFromClassloader(path: String): java.net.URL = this.getClass.getResource(path)
	def resourceFromUserDirectory(path: String): java.io.File = new java.io.File(util.Properties.userDir, path)
}
