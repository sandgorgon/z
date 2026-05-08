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

import java.io.{File, FileWriter}
import util.Properties

// Pure file I/O operations factored out of ZWnd.get() and ZWnd.put(). No Swing dependencies.
object ZFileIO {

	// Read a text file. Returns Right(content) or Left(errorMessage).
	def readFile(path: String): Either[String, String] =
		scala.util.Try(scala.util.Using(io.Source.fromFile(path))(_.mkString).get)
			.toEither.left.map(_.getMessage)

	// List a directory as a newline-separated string, directories suffixed with /.
	// Returns Right(listing) or Left(errorMessage).
	def readDir(dirPath: String): Either[String, String] =
		scala.util.Try {
			val dir = new File(dirPath)
			dir.list.toList.sorted
				.map(e => if (new File(dirPath + File.separator + e).isDirectory) e + File.separator else e)
				.mkString(Properties.lineSeparator)
		}.toEither.left.map(_.getMessage)

	// Write content to a file. Returns Right(()) or Left(errorMessage).
	def writeFile(path: String, content: String): Either[String, Unit] =
		scala.util.Try(scala.util.Using(new FileWriter(path))(_.write(content)).get)
			.toEither.left.map(_.getMessage)
}
