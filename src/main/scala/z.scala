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

import swing.{SwingApplication, BorderPanel, Label, Alignment, MainFrame, Orientation}
import collection.immutable.{Map, HashMap}

import java.io.File
import java.awt.{Toolkit, Dimension, Font, Window}
import java.lang.reflect.Method;

object z extends SwingApplication {
	ZFonts.registerFonts

	var frame:MainFrame = null

	val mainPanel = new ZPanel("Help NewCol Put Dump Load ")

	val status = new Label("Plan 9 acme inspired") {
		horizontalAlignment = Alignment.Left
		font = ZFonts.SANS_SERIF_MONO
	}

	val MainWindow = new BorderPanel {
		layout(mainPanel) = BorderPanel.Position.Center
		layout(status) = BorderPanel.Position.South
	}

	listenTo(mainPanel)
	reactions += {
		case e : ZStatusEvent =>
			status.text = e.properties.get("line.current").get + "/" + e.properties.get("lines").get + "@" + e.properties.get("column.current").get +
						" |  Tab: " + e.properties.get("tab.size").get + 
						" | " + (if(e.properties.get("line.wrap").get == "true") "Wrap" else "NoWrap") + 
						" | " + (if(e.properties.get("indent.auto").get == "true") "Indent" else "NoIndent") + 
						" | " + (if(e.properties.get("scroll").get == "true") "Scroll" else "NoScroll") +
						" | " + e.properties.get("body.font.current").get + " " + e.properties.get("body.font.current.size").get +
						(if(e.properties.get("bind").get == "true") " | Bind" else "") + 
						(if(e.properties.get("interactive").get == "true") " | Input: " + ZWnd.rePrompt else "")

		case e : ZColStatusEvent => status.text = e.properties.get("command.prev").get
		case e : ZPanelStatusEvent =>
						status.text = e.properties.get("command.prev").get
	}

	def top = new MainFrame {
		title = new File(".").getCanonicalPath + " - z editor"
		iconImage = Toolkit.getDefaultToolkit().createImage(resourceFromClassloader("images/z.png"))

		contents = MainWindow

		override def closeOperation() = {
			var d = size
			var p = new HashMap[String, String]
			p += "app.width" -> d.getWidth.toInt.toString
			p += "app.height" ->d.getHeight.toInt.toString

			ZSettings.dump(p, new File(util.Properties.userHome + ZUtilities.separator + ".z"), "Z Global Settings")
			System.exit(0)
		}
	}

	override def startup(args: Array[String]) = {
		var f = new File(util.Properties.userHome + ZUtilities.separator + ".z")
		var p : Map[String, String] = null

		if(f.exists) {
			p = ZSettings.load(f)

			if(p.get("app.width") == None || p.get("app.width").get.toInt < 10)  p += "app.width" -> "600"
			if(p.get("app.height") == None || p.get("app.height").get.toInt < 10)  p += "app.height" -> "400"
		} else {
			p = new HashMap[String, String]
			p += "app.width" -> "600"
			p += "app.height" -> "400"
		}

		frame = top
		frame.preferredSize = new Dimension(p.get("app.width").get.toInt, p.get("app.height").get.toInt)
		frame.pack
		frame.centerOnScreen
		frame.visible = true
		mainPanel.populate(args)

    System.getProperty("os.name") match {
      case mac if mac.toLowerCase().startsWith("mac os x")=> enableOSXFullscreen(frame.peer);setOSXDockIcon(frame)
    }
	}

  def enableOSXFullscreen(window: Window) {
    try {
      val util = Class.forName("com.apple.eawt.FullScreenUtilities");
      val method = util.getMethod("setWindowCanFullScreen", classOf[java.awt.Window], java.lang.Boolean.TYPE)
      method.invoke(null, window, Boolean.box(true))
    } catch {
      case e: Exception => e.printStackTrace(System.err);
    }
  }

  def setOSXDockIcon(frame: MainFrame) {
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
