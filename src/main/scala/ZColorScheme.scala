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

import java.awt.Color

// Immutable color scheme for a ZTextArea (body or tag).
// withComponent returns a new scheme with one color replaced; out-of-range RGB
// values leave the scheme unchanged (same behaviour as the old applyColor helper).
case class ZColorScheme(back: Color, fore: Color, caret: Color, selBack: Color, selFore: Color) {

	def applyTo(ta: ZTextArea): Unit =
		ta.colors(back, fore, caret, selBack, selFore)

	def withComponent(component: String, r: Int, g: Int, b: Int): ZColorScheme = {
		if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) return this
		val c = new Color(r, g, b)
		component match {
			case "Back"    => copy(back    = c)
			case "Fore"    => copy(fore    = c)
			case "Caret"   => copy(caret   = c)
			case "SelBack" => copy(selBack = c)
			case "SelFore" => copy(selFore = c)
			case _         => this
		}
	}
}
