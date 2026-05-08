/*
Copyright (c) 2011-2026. Ramon de Vera Jr.
All Rights Reserved

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
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

// Canonical color constants shared across ZWnd, ZFuzzyPicker, ZTheme, and the
// panel/col tag defaults. Changing a constant here propagates everywhere.
object ZColors {
	val TagBack    = new Color(0x4A, 0x61, 0x95)   // window tag background
	val TagFore    = Color.WHITE                    // window tag foreground
	val TagCaret   = new Color(0xC7, 0xC7, 0xC7)   // window tag caret
	val TagSelBack = new Color(0x96, 0x96, 0x96)   // panel/col tag selection background
	val BodyBack   = new Color(0xFF, 0xFF, 0xE0)   // editor body background
	val BodySelBack= new Color(0xC8, 0x75, 0x9F)   // body selection background
}
