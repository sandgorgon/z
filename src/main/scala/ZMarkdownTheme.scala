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

import java.awt.Font

// Mutable font slots for Markdown typography, read by ZTheme.applyMarkdownOverrides
// and written by the MdFont command.  null means "derive from the body font at
// render time" so emphasis elements track whatever body font the user has set.
object ZMarkdownTheme {
    var h1Font:       Font = ZFonts.SERIF.deriveFont(Font.BOLD, 18f)
    var h2Font:       Font = ZFonts.SERIF.deriveFont(Font.BOLD, 15f)
    var h3Font:       Font = ZFonts.SERIF.deriveFont(Font.BOLD, 14f)
    var boldFont:     Font = null   // null → derive bold from body font
    var emFont:       Font = null   // null → derive italic from body font
    var boldItalFont: Font = null   // null → derive bold+italic from body font
    var codeFont:     Font = ZFonts.SANS_SERIF_MONO
    var quoteFont:    Font = null   // null → derive italic from body font

    val elements: Seq[String] = Seq("h1", "h2", "h3", "bold", "em", "bolditalic", "code", "quote")

    def fontFor(element: String): Font = element match {
        case "h1"         => h1Font
        case "h2"         => h2Font
        case "h3"         => h3Font
        case "bold"       => boldFont
        case "em"         => emFont
        case "bolditalic" => boldItalFont
        case "code"       => codeFont
        case "quote"      => quoteFont
        case _            => null
    }

    def setFont(element: String, family: String, size: Int): Unit = element match {
        case "h1"         => h1Font       = new Font(family, Font.BOLD,               size)
        case "h2"         => h2Font       = new Font(family, Font.BOLD,               size)
        case "h3"         => h3Font       = new Font(family, Font.BOLD,               size)
        case "bold"       => boldFont     = new Font(family, Font.BOLD,               size)
        case "em"         => emFont       = new Font(family, Font.ITALIC,             size)
        case "bolditalic" => boldItalFont = new Font(family, Font.BOLD | Font.ITALIC, size)
        case "code"       => codeFont     = new Font(family, Font.PLAIN,              size)
        case "quote"      => quoteFont    = new Font(family, Font.ITALIC,             size)
        case _            =>
    }
}
