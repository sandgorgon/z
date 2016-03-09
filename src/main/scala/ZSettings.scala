/*
Copyright (c) 2011-2016. Ramon de Vera Jr.
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
import scala.collection.JavaConversions
import scala.collection.immutable.{Map, HashMap}

import java.util.Properties
import java.io.{File, FileReader, FileWriter}

object ZSettings {
	def load(f : File) : Map[String, String] = {
		var settings = new HashMap[String, String]
		var properties = new Properties
		properties.load(new FileReader(f))

		JavaConversions.propertiesAsScalaMap(properties).toMap
	}

	def dump(m : Map[String, String], f : File, comments : String) = {
		var p = new Properties
		m.foreach((e) => p.put(e._1, e._2))

		p.store(new FileWriter(f), comments)
	}
}
