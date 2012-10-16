/*
$Id$

Copyright (c) 2011. Ramon de Vera Jr.
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

import java.awt.{GraphicsEnvironment, Font}
import java.io.File

object ZFonts {
	val SANS_SERIF_MONO = Font.createFont(Font.TRUETYPE_FONT, this.getClass.getResourceAsStream("fonts/VeraMono.ttf")).deriveFont(Font.PLAIN, 12)
	val SANS_SERIF = Font.createFont(Font.TRUETYPE_FONT, this.getClass.getResourceAsStream("fonts/Vera.ttf")).deriveFont(Font.PLAIN, 12)
	val SERIF = Font.createFont(Font.TRUETYPE_FONT, this.getClass.getResourceAsStream("fonts/VeraSe.ttf")).deriveFont(Font.PLAIN, 12)
	
	val ge = GraphicsEnvironment.getLocalGraphicsEnvironment

	def registerFonts = {
		ge.registerFont(SANS_SERIF_MONO)
		ge.registerFont(SANS_SERIF)
		ge.registerFont(SERIF)
	}

	def familyNames = ge.getAvailableFontFamilyNames
}
