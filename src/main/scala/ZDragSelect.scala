/*
Copyright (c) 2011-2026. Ramon de Vera Jr.
All Rights Reserved

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
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
import swing.event.{MousePressed, MouseDragged, MouseReleased}
import javax.swing.SwingUtilities

// B2/B3 drag-to-select behaviour for single-textarea panels (ZPanel, ZCol).
// ZWnd has two text areas and a fromTag distinction, so it manages drag select directly.
// Call wireDragSelect(tag) during initialization after listenTo is set up.
trait ZDragSelect { this: Panel =>
	private var _dragSel     = false
	private var _dragSelMark = -1

	protected def onDragMiddle(txt: String): Unit
	protected def onDragRight(txt: String): Unit

	protected def wireDragSelect(ta: ZTextArea): Unit = {
		listenTo(ta.mouse.moves)
		reactions += {
			case e: MousePressed
				if SwingUtilities.isMiddleMouseButton(e.peer) || SwingUtilities.isRightMouseButton(e.peer) =>
				_dragSelMark = ta.peer.viewToModel2D(e.point).toInt

			case e: MouseDragged
				if SwingUtilities.isMiddleMouseButton(e.peer) || SwingUtilities.isRightMouseButton(e.peer) =>
				_dragSel = true
				if (_dragSelMark != -1) { ta.peer.setCaretPosition(_dragSelMark); _dragSelMark = -1 }
				ta.peer.moveCaretPosition(ta.peer.viewToModel2D(e.point).toInt)

			case e: MouseReleased =>
				if (_dragSel) {
					val txt = ZUtilities.selectedText(ta, e)
					if (SwingUtilities.isMiddleMouseButton(e.peer))      onDragMiddle(txt)
					else if (SwingUtilities.isRightMouseButton(e.peer))  onDragRight(txt)
				}
				_dragSel = false
				_dragSelMark = -1
		}
	}
}
