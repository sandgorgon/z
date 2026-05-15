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
import swing.event.{KeyReleased, Key}

// Ctrl+Enter / Ctrl+F / Escape capture-mode for single-tag-line panels (ZPanel, ZCol).
// ZWnd captures on either tag or body and has additional semantics (body delete, selectAll
// for pipe), so it manages capture mode directly.
// Call wireCaptureMode(tag) during initialization.
trait ZCaptureMode { this: Panel =>
	private var _captureActive = false

	protected def onCaptureCommand(txt: String): Unit
	protected def onCaptureLook(txt: String): Unit

	protected def wireCaptureMode(ta: ZTextArea): Unit = {
		listenTo(ta.keys)
		reactions += {
			case e: KeyReleased if e.key == Key.Enter && e.peer.isControlDown() =>
				if (_captureActive) {
					val txt = ta.endCapture().trim
					_captureActive = false
					if (txt.nonEmpty) onCaptureCommand(txt)
				} else {
					val sel = Option(ta.selected).getOrElse("").trim
					if (sel.nonEmpty) onCaptureCommand(sel)
					else { _captureActive = true; ta.startCapture() }
				}

			case e: KeyReleased if e.key == Key.F && e.peer.isControlDown() && !e.peer.isShiftDown() =>
				if (_captureActive) {
					val txt = ta.endCapture().trim
					_captureActive = false
					if (txt.nonEmpty) onCaptureLook(txt)
				} else {
					val sel = Option(ta.selected).getOrElse("").trim
					if (sel.nonEmpty) onCaptureLook(sel)
				}

			case e: KeyReleased if e.key == Key.Escape =>
				ta.abortCapture()
				_captureActive = false
		}
	}
}
