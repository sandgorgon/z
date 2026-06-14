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

import org.fife.ui.rsyntaxtextarea.modes.MarkdownTokenMaker
import org.fife.ui.rsyntaxtextarea.{Token, TokenTypes}
import javax.swing.text.Segment

// Post-processes the base RSTA Markdown token chain so every element gets its
// own unambiguous token type, enabling per-level heading fonts in ZTheme.
class ZMarkdownTokenMaker extends MarkdownTokenMaker {

    override def getTokenList(text: Segment, initialTokenType: Int, startOffset: Int): Token = {
        val first = super.getTokenList(text, initialTokenType, startOffset)
        refine(first, initialTokenType)
        first
    }

    // MarkdownTokenMaker.INTERNAL_IN_SYNTAX_HIGHLIGHTING is private; value verified against RSTA 3.4.0
    private val InFencedBlock = -11

    private def refine(first: Token, initialTokenType: Int): Unit = {
        val inFencedBlock = initialTokenType == InFencedBlock
        var t = first
        while (t != null && t.isPaintable) {
            t.getType match {
                case TokenTypes.RESERVED_WORD =>
                    val level = t.getLexeme.takeWhile(_ == '#').length
                    // level == 1 is already RESERVED_WORD; only retype H2+ to free those slots
                    if (level == 2) t.setType(TokenTypes.RESERVED_WORD_2)
                    else if (level > 2) t.setType(TokenTypes.DATA_TYPE)

                case TokenTypes.RESERVED_WORD_2 =>
                    t.setType(TokenTypes.FUNCTION)         // **bold** freed from H2 slot

                case TokenTypes.DATA_TYPE =>
                    t.setType(TokenTypes.VARIABLE)         // *italic* freed from H3+ slot

                case TokenTypes.FUNCTION =>
                    t.setType(TokenTypes.COMMENT_KEYWORD)  // ***bold+italic***

                case TokenTypes.PREPROCESSOR =>
                    if (inFencedBlock)
                        t.setType(TokenTypes.LITERAL_BACKQUOTE)
                    else if (t.getLexeme.startsWith("```"))
                        t.setType(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE)
                    // else: inline `code` — keep as PREPROCESSOR

                case TokenTypes.VARIABLE =>
                    t.setType(TokenTypes.LITERAL_CHAR)     // fenced code lang specifier

                case _ =>
            }
            t = t.getNextToken
        }
    }
}
