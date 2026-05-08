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

import java.io.File

// Pure path-resolution utilities factored out of ZWnd. No Swing dependencies.
object ZPathResolver {

	// Expand and canonicalize a path relative to root.
	def resolvePath(p: String, root: String): String = {
		val ep = ZUtilities.expandPath(p, root)
		if (ZUtilities.isFullPath(ep)) ep
		else new File(root + ZUtilities.separator + ep).getCanonicalPath
	}

	// True when stxt is a prefix segment of the window's own rawPath.
	// fromTag=true tightens the guard: stxt must start at position 0 of tagText,
	// ruling out command arguments that coincidentally share the same prefix.
	def isWndPathPrefix(rawPath: String, stxt: String, fromTag: Boolean, tagText: String): Boolean =
		!ZUtilities.isFullPath(rawPath) &&
		rawPath.startsWith(stxt) &&
		(!fromTag || tagText.startsWith(stxt))

	// Returns root when stxt is a prefix segment of the window's own path
	// (tag-line segment navigation), otherwise returns baseDir.
	// Prevents double-prefix when resolving relative paths from a relative window.
	def resolveBase(rawPath: String, stxt: String, fromTag: Boolean, tagText: String,
	                root: String, baseDir: String): String =
		if (isWndPathPrefix(rawPath, stxt, fromTag, tagText)) root else baseDir
}
