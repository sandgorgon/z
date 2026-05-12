/*
Copyright (c) 2011-2026. Ramon de Vera Jr.
All Rights Reserved

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to use,
modify, merge, publish, distribute, sublicense, and/or sell copies of the
Software, and to permit persons to whom the Software is furnished to do so,
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

import swing.Panel
import java.awt.{Color, Cursor, Dimension}
import javax.swing.{BorderFactory, SwingUtilities}
import java.awt.event.{MouseAdapter, MouseEvent}

class ZDragHandle(initColor: Color) extends Panel {
  // Called on release with (dx, dy) in screen pixels; only fires if drag exceeds threshold
  var onDragRelease: (Int, Int) => Unit = (_, _) => ()

  private var pressX = 0
  private var pressY = 0

  background = initColor
  preferredSize = new Dimension(8, 0)
  peer.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR))
  peer.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.DARK_GRAY))

  peer.addMouseListener(new MouseAdapter {
    override def mousePressed(e: MouseEvent): Unit =
      if (SwingUtilities.isLeftMouseButton(e)) {
        pressX = e.getXOnScreen
        pressY = e.getYOnScreen
      }
    override def mouseReleased(e: MouseEvent): Unit =
      if (SwingUtilities.isLeftMouseButton(e)) {
        val dx = e.getXOnScreen - pressX
        val dy = e.getYOnScreen - pressY
        if (math.abs(dx) > 20 || math.abs(dy) > 20)
          onDragRelease(dx, dy)
      }
  })
}
