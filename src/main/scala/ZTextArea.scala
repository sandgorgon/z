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

import swing.TextArea
import swing.event.{MouseEntered, MouseClicked, Key, KeyReleased, Event}
import java.awt.Color
import java.awt.event.{KeyEvent, InputEvent}
import javax.swing.{SwingUtilities, KeyStroke}
import javax.swing.event.{UndoableEditListener, UndoableEditEvent}
import org.fife.ui.rsyntaxtextarea.{RSyntaxTextArea, SyntaxConstants}

class ZTextArea(txt : String = "", wrap : Boolean = false) extends TextArea(txt) {
	var lspTooltip: String = null

	override lazy val peer: RSyntaxTextArea = new RSyntaxTextArea(txt) with SuperMixin {
		override def getToolTipText(e: java.awt.event.MouseEvent): String = lspTooltip
	}

	border = null
	tabSize = 4
	caret.position = 0
	lineWrap = wrap

	// RSTA setup — disable features that conflict with z's interaction model
	peer.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE)
	peer.setHighlightCurrentLine(true)
	peer.setPopupMenu(null)
	peer.setBracketMatchingEnabled(false)
	peer.setAutoIndentEnabled(false)
	peer.setCloseCurlyBraces(false)
	peer.setCloseMarkupTags(false)
	peer.setAntiAliasingEnabled(true)

	// Strip RSTA's built-in undo/redo key bindings — z owns Ctrl+Z and Ctrl+R
	peer.getInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "none")
	peer.getInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "none")
	peer.getInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "none")

	listenTo(mouse.moves, mouse.clicks)
	reactions += {
		case e : MouseEntered =>
			e.source.requestFocus()
		case e : MouseClicked =>
			if(e.peer.isControlDown && SwingUtilities.isLeftMouseButton(e.peer))
				braceMatch(e)
	}

	// UndoableEditListener for dirty tracking only — undo stack is RSTA's CompoundUndoManager
	peer.getDocument.addUndoableEditListener(new UndoableEditListener() {
		def undoableEditHappened(e: UndoableEditEvent): Unit = publish(new ZDirtyTextEvent)
	})

	listenTo(keys)
	reactions += {
		case e : KeyReleased =>
			if((e.key == Key.Z) && ((e.modifiers & Key.Modifier.Control) == Key.Modifier.Control))
				undo()
			if((e.key == Key.R) && ((e.modifiers & Key.Modifier.Control) == Key.Modifier.Control))
				redo()
		case _ =>
	}

	def canUndo: Boolean = peer.canUndo
	def canRedo: Boolean = peer.canRedo

	def undo(): Unit = {
		if(peer.canUndo) {
			peer.undoLastAction()
			if(!peer.canUndo) publish(new ZCleanTextEvent)
		}
	}

	def redo(): Unit = {
		if(peer.canRedo) {
			peer.redoLastAction()
			publish(new ZDirtyTextEvent)
		}
	}

	def hilite(style: String): Unit          = peer.setSyntaxEditingStyle(style)
	def clineHighlight: Boolean               = peer.getHighlightCurrentLine
	def clineHighlight_=(b: Boolean): Unit    = peer.setHighlightCurrentLine(b)

	def lineNo(offset : Int)                  = peer.getLineOfOffset(offset)
	def lineEnd(line : Int)                   = peer.getLineEndOffset(line)
	def lineStart(line : Int)                 = peer.getLineStartOffset(line)
	def currLineNo                            = lineNo(caret.dot)
	def currColumn                            = caret.dot - lineStart(lineNo(caret.dot)) + 1
	def getTextRange(oStart : Int, oEnd : Int) = peer.getText(oStart, oEnd - oStart)
	def line(line: Int = currLineNo)          = getTextRange(lineStart(line), lineEnd(line))
	def lineSet(line: Int, s: String)         = peer.replaceRange(s, lineStart(line), lineEnd(line))
	def selected_=(s: String)                 = peer.replaceSelection(s)
	def selectionStart                        = if(peer.getSelectionEnd < peer.getSelectionStart) peer.getSelectionEnd else peer.getSelectionStart
	def selectionStart_=(i : Int)             = peer.setSelectionStart(i)
	def selectionEnd                          = if(peer.getSelectionStart > peer.getSelectionEnd) peer.getSelectionStart else peer.getSelectionEnd
	def selectionEnd_=(i: Int)                = peer.setSelectionEnd(i)
	def linePrefix(offset : Int = caret.dot)  = getTextRange(lineStart(lineNo(offset)), offset)
	def braceMatch(e: MouseClicked)           = ZUtilities.symMatch(this, e)

	def colors(back : Color, fore : Color, crt : Color, backSel : Color, foreSel : Color): Unit = {
		background = back
		foreground = fore
		caret.color = crt
		peer.setSelectionColor(backSel)
		peer.setSelectedTextColor(foreSel)
		peer.setCurrentLineHighlightColor(new Color(
			math.max(0, back.getRed   - 20),
			math.max(0, back.getGreen - 20),
			math.max(0, back.getBlue  - 20)
		))
	}
}

class ZCleanTextEvent extends Event
class ZDirtyTextEvent extends Event
