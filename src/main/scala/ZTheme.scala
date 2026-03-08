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

import org.fife.ui.rsyntaxtextarea.{Theme, TokenTypes, SyntaxScheme}
import java.awt.Color

object ZTheme {

	// "z" is z's own palette; the rest are RSTA built-in themes.
	// Built-in names map directly to /org/fife/ui/rsyntaxtextarea/themes/<name>.xml
	val available: Seq[String] = Seq("z", "dark", "druid", "eclipse", "idea", "monokai", "vs")

	def apply(name: String, ta: ZTextArea): Unit = name.toLowerCase match {
		case "z" | "default" => applyZ(ta)
		case n               => loadBuiltin(n, ta)
	}

	// z theme — tokens tuned for z's pale-yellow body background (#FFFFE0)
	private def applyZ(ta: ZTextArea): Unit = {
		ta.peer.setBackground(new Color(0xFF, 0xFF, 0xE0))
		ta.peer.setForeground(new Color(0x00, 0x00, 0x00))
		ta.peer.setCurrentLineHighlightColor(new Color(0xEB, 0xEB, 0xC0))
		ta.peer.setCaretColor(new Color(0x00, 0x00, 0x00))
		ta.peer.setSelectionColor(new Color(0xC8, 0x75, 0x9F))
		ta.peer.setSelectedTextColor(new Color(0xFF, 0xFF, 0xFF))

		val scheme = ta.peer.getSyntaxScheme.clone().asInstanceOf[SyntaxScheme]

		def set(ttype: Int, r: Int, g: Int, b: Int): Unit = {
			val s = scheme.getStyle(ttype)
			if (s != null) s.foreground = new Color(r, g, b)
		}

		set(TokenTypes.RESERVED_WORD,              0x00, 0x00, 0xAA)  // keywords
		set(TokenTypes.RESERVED_WORD_2,            0x00, 0x00, 0xAA)  // secondary keywords
		set(TokenTypes.DATA_TYPE,                  0x00, 0x66, 0x66)  // types
		set(TokenTypes.ANNOTATION,                 0x88, 0x55, 0x00)  // @annotations
		set(TokenTypes.FUNCTION,                   0x00, 0x44, 0x88)  // function names
		set(TokenTypes.VARIABLE,                   0x66, 0x00, 0x66)  // variables
		set(TokenTypes.PREPROCESSOR,               0x88, 0x44, 0x00)  // preprocessor / #directives
		set(TokenTypes.LITERAL_BOOLEAN,            0x00, 0x00, 0xAA)  // true / false
		set(TokenTypes.LITERAL_NUMBER_DECIMAL_INT, 0xAA, 0x00, 0x00)  // integers
		set(TokenTypes.LITERAL_NUMBER_FLOAT,       0xAA, 0x00, 0x00)  // floats
		set(TokenTypes.LITERAL_NUMBER_HEXADECIMAL, 0xAA, 0x00, 0x00)  // hex
		set(TokenTypes.LITERAL_CHAR,               0x00, 0x77, 0x00)  // char literals
		set(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE,0x00, 0x77, 0x00)  // double-quoted strings
		set(TokenTypes.LITERAL_BACKQUOTE,          0x00, 0x77, 0x00)  // backtick / raw strings (Go, Scala)
		set(TokenTypes.COMMENT_EOL,               0x77, 0x77, 0x77)  // // line comments
		set(TokenTypes.COMMENT_MULTILINE,         0x77, 0x77, 0x77)  // /* block comments */
		set(TokenTypes.COMMENT_DOCUMENTATION,     0x55, 0x88, 0x55)  // /** javadoc / docstrings */
		set(TokenTypes.COMMENT_KEYWORD,           0x55, 0x88, 0x55)  // @param, @return in doc comments
		set(TokenTypes.OPERATOR,                  0x00, 0x00, 0x00)  // operators

		ta.peer.setSyntaxScheme(scheme)
	}

	// Load one of RSTA's bundled themes by name (dark, eclipse, idea, monokai, vs, druid)
	private def loadBuiltin(name: String, ta: ZTextArea): Unit = {
		val path = s"/org/fife/ui/rsyntaxtextarea/themes/$name.xml"
		val is   = getClass.getResourceAsStream(path)
		if (is != null) {
			try { Theme.load(is).apply(ta.peer) }
			finally { is.close() }
		}
	}
}
