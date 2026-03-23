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
import util.Properties
import javax.swing.JOptionPane

// Manages user script discovery and resolution for ,cmd and ,,cmd invocations.
// Search order: .z/scripts/ in cwd (project-local) → ~/.z/scripts/ (global) → extras from ~/.z/scripts.conf
object ZScripts {

	val reScript    = """^,(\S+)(.*)$""".r   // ,scriptname[ args]
	val reScriptAll = """^,,(\S+)(.*)$""".r  // ,,scriptname[ args]
	val reAnyScript = """^,{1,2}(\S+)(.*)$""".r  // either , or ,,

	def showError(name: String, searched: List[String]): Unit =
		JOptionPane.showMessageDialog(null,
			"Script '" + name + "' not found in:\n" + searched.mkString("\n"),
			"Script Error", JOptionPane.ERROR_MESSAGE)


	private var extraDirs: List[String] = Nil

	// Read at startup — loads scripts.path from ~/.z/scripts.conf if it exists.
	def load(): Unit = {
		val f = new File(
			Properties.userHome + ZUtilities.separator + ".z" +
			ZUtilities.separator + "scripts.conf"
		)
		if (f.exists()) {
			val p = ZSettings.load(f)
			p.get("scripts.path").foreach { raw =>
				extraDirs = raw.split(":").toList
					.map(_.trim)
					.filter(_.nonEmpty)
					.map(d => ZUtilities.expandPath(d, Properties.userHome))
			}
		}
	}

	// Ordered list of directories to search, given the current working dir.
	def dirs(cwd: String): List[String] = {
		val projectLocal = cwd + ZUtilities.separator + ".z" + ZUtilities.separator + "scripts"
		val global       = Properties.userHome + ZUtilities.separator + ".z" + ZUtilities.separator + "scripts"
		(projectLocal :: global :: extraDirs).distinct
	}

	// Resolves a script name to an executable file, or None if not found.
	// Reports the searched dirs in the returned Left for error messaging.
	def resolve(name: String, cwd: String): Either[List[String], File] = {
		val searchDirs = dirs(cwd)
		searchDirs
			.map(d => new File(d, name))
			.find(f => f.exists() && f.isFile && f.canExecute)
			match {
				case Some(f) => Right(f)
				case None    => Left(searchDirs)
			}
	}
}
