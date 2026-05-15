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

import swing.event.Event

// ZWnd-sourced events
class ZCmdEvent(val source: ZWnd, val command: String) extends Event
class ZStatusEvent(val source: ZWnd, val properties: Map[String, String]) extends Event
class ZStatusClearEvent(val source: ZWnd) extends Event
class ZCmdEchoEvent(val timestamp: String, val level: String, val source: String, val cmd: String) extends Event
class ZScriptEvent(val source: ZWnd, val scriptPath: String, val args: String) extends Event
class ZPlumbExecEvent(val source: ZWnd, val cmd: String, val cwd: String) extends Event
class ZDiagnosticsReadyEvent(val source: ZWnd, val content: String) extends Event
class ZPathChangedEvent(val source: ZWnd, val oldPath: String, val newPath: String) extends Event

// ZCol-sourced events
class ZCmdCloseColEvent(val source: ZCol) extends Event
class ZMoveWndEvent(val dir: String, val source: ZCol, val wnd: ZWnd) extends Event
class ZMoveColEvent(val dir: String, val source: ZCol) extends Event
class ZColStatusEvent(val source: ZCol, val properties: Map[String, String]) extends Event

// ZPanel-sourced events
class ZPanelStatusEvent(val source: ZPanel, val properties: Map[String, String]) extends Event
