/*
Copyright (c) 2011-2015. Ramon de Vera Jr.
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
import util.Properties
import util.control.Breaks._
import actors.Actor
import actors.Actor._
import swing.TextArea
import swing.event.MouseEvent
import collection.JavaConversions
import collection.immutable.HashMap
import java.io.{File, BufferedReader, BufferedWriter, OutputStreamWriter, InputStreamReader}
import javax.swing.text.Utilities
import java.awt.Point

object ZUtilities {
	def separator = File.separator
	def isFullPath(s: String) = (new File(s)).isAbsolute 

	def selectedText(ta : ZTextArea, e : MouseEvent) : String = selectedText(ta, ta.peer.viewToModel(e.point))
	def selectedText(ta : ZTextArea, pt : Point) : String = selectedText(ta, ta.peer.viewToModel(pt))
	def selectedText(ta : ZTextArea, pos : Int) : String = {
		if( ta.selected != null && pos >= ta.selectionStart && pos <= ta.selectionEnd)  return ta.selected.trim

		var retval = ""
		var end = Utilities.getWordEnd(ta.peer, pos)
		var start = ta.lineStart(ta.lineNo(pos))

		ta.getTextRange(start, end) match {
			case ZWnd.rePre(t) => t
			case _ => ""
		}
	}

	/**
	* Used for brace matching - it selects the text inclusive of the braces used.
	* 
	* Only works if the mouse click is at a position right before the opening
	* brace or right after the closing brace.
	* 
	* Braces known are the following: (), {}, <>, []
	* 
	* If this method is called with a character that is not an opening or 
	* closing brace, it will use the character as the opening and then closing
	* "brace" - direction of matching will always be forward for this case.
	* 
	* @param tc    The text component to work with
	* @param evt   The mouse event that we will be using
	*/
	def symMatch(ta : TextArea, evt : MouseEvent) : Unit = {
		val pos = ta.peer.viewToModel(evt.point)
		val buf = ta.text

		if(buf  == "" || buf.length < 2)  return

		var lastndx = buf.length - 1
		var i = pos
		var move = 1

		if(i < 0)  return
		if(i > lastndx)  i = lastndx

		var start = buf.charAt(i)
		var expected = start

		start match {
			case '(' => expected = ')'
			case '{' => expected = '}'
			case '[' => expected = ']'
			case '<' => expected = '>'
			case _ =>if(i - 1 > 0) {
				i = i - 1
				start = buf.charAt(i);
				start match {
					case ')' => 
						expected = '('
						move = -1
					case '}' => 
						expected = '{'
						move = -1
					case ']' => 
						expected = '['
						move = -1
					case '>' => 
						expected = '<'
						move = -1
					case _ =>
						i = i  + 1
						start = buf.charAt(i)
						expected = start
						move = 1
				}
			}
		}

		var cnt = 0
		var init = i
		var mark = -1

		breakable {
		while(i < lastndx && i > -1) {
			val c = buf.charAt(i)

			if(c == start) {
				if(start == expected && cnt == 1) {
					cnt = 0
					mark = i
					break
				}

				cnt = cnt + 1
			}
			else if(c == expected) {
				cnt = cnt - 1
				if(cnt == 0) {
					mark = i
					break
				}
			}

			i += move
		}
		}

		if(cnt != 0)  return

		if(move == 1)  mark += move
		else init += 1
        
		// Select from initial position (init) to mark
		ta.caret.position = init
		ta.caret.moveDot(mark)
	}

	def extCmd(cmd : String, a : Actor, redirectErrStream : Boolean = false, input : String = null, workdir : String = null, env : HashMap[String, String] = null) : Process = {
		var tokens = tokenize(cmd)
		if(tokens.length == 0) {
			a ! ZWnd.CMD_DONE;
			return null
		}

		var pb = new ProcessBuilder(JavaConversions.seqAsJavaList(tokens))
		if(workdir != null)  pb.directory(new File(workdir))
		if(redirectErrStream) pb.redirectErrorStream(true)
		if(env != null) env.foreach((x) => pb.environment.put(x._1, x._2))
		var proc = pb.start

		if(input != null && !input.trim.isEmpty) {
			val osr = new OutputStreamWriter(proc.getOutputStream)
			val bw = new BufferedWriter(osr)
			bw.write(input);
			bw.close();
		}

		var exec = actor {
			loop {
				react {
					case p : Process =>
						// NOTE: We can't get the results line-by-line because it is possible that
						// 	are getting a string with no EOL yet.
						val br = new BufferedReader(new InputStreamReader(proc.getInputStream))
						val buffer  = new Array[Char](4096)
						var len = 0
						while(len != -1) {
							len = br.read(buffer)
							if(len != -1)  a ! String.copyValueOf(buffer, 0, len)
						}
						br.close
						a ! ZWnd.CMD_DONE
						proc.destroy
						exit
				}
			}
		}

		exec ! proc
		proc
	}

	def tokenize(s : String = "") = {
		var input = s
		if(input == null) input = ""

		var tokens : List[String] = Nil
		var prev = ' '
		var token = ""
		var compound = false
		
		input.foreach( c => {
			if(c  == ' ') {
				if(!compound) {
					if(token != "")  tokens = token :: tokens
					token = ""
				}
				else token += c
			} else if (c == '\'') {
				if(prev != '\\') {
					if(compound) {
						tokens = token :: tokens
						compound = false
					}
					else {
						compound = true
						if(token != "")  tokens = token :: tokens
					}
					
					token = ""
				}
				else token += c			
			} else token += c
			
			prev = c
		})

		if(token != "")  tokens = token :: tokens
		tokens.reverse
	}
}
