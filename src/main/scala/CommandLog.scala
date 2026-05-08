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

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

object CommandLog {
	private var maxEntries = 500
	private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")

	private case class Entry(time: String, level: String, source: String, cmd: String)
	private val entries = new CopyOnWriteArrayList[Entry]()

	def setLimit(n: Int): Unit = if (n > 0) maxEntries = n
	def clear(): Unit = { entries.clear(); maxEntries = 500 }

	def record(level: String, source: String, cmd: String): String = {
		val ts = LocalTime.now().format(fmt)
		if (entries.size() >= maxEntries) entries.remove(0)
		entries.add(Entry(ts, level, source, cmd))
		ts
	}

	def render: String = {
		val sb = new StringBuilder
		entries.forEach { e =>
			sb.append(e.time).append("  ").append(e.level).append("  ").append(e.source).append("  ").append(e.cmd).append("\n")
		}
		sb.toString
	}
}
