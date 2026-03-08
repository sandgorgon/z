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

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument
import org.fife.ui.rsyntaxtextarea.parser.{AbstractParser, DefaultParseResult, DefaultParserNotice, ParseResult, ParserNotice}
import org.eclipse.lsp4j.{Diagnostic, DiagnosticSeverity}
import scala.jdk.CollectionConverters._

// RSTA AbstractParser that bridges LSP publishDiagnostics into squiggly underlines.
// Call setDiagnostics() from the LSP callback, then forceReparsing(0) on the RSyntaxTextArea
// to trigger a re-render with the updated notices.
class ZLspDiagnosticParser extends AbstractParser {

	@volatile private var diags: List[Diagnostic] = Nil

	def setDiagnostics(d: List[Diagnostic]): Unit = { diags = d }

	def clearDiagnostics(): Unit = { diags = Nil }

	override def parse(doc: RSyntaxDocument, style: String): ParseResult = {
		val result = new DefaultParseResult(this)
		diags.foreach { d =>
			val line = d.getRange.getStart.getLine  // 0-based, matching RSTA
			val msg  = d.getMessage
			val level = Option(d.getSeverity) match {
				case Some(DiagnosticSeverity.Error)       => ParserNotice.Level.ERROR
				case Some(DiagnosticSeverity.Warning)     => ParserNotice.Level.WARNING
				case Some(DiagnosticSeverity.Information) => ParserNotice.Level.INFO
				case _                                    => ParserNotice.Level.INFO
			}
			val notice = new DefaultParserNotice(this, msg, line)
			notice.setLevel(level)
			result.addNotice(notice)
		}
		result
	}
}
