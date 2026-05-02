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
import scala.util.{Using, matching}
import scala.util.matching.Regex

sealed trait PlumbAction
case object PlumbExec extends PlumbAction
case object PlumbLook extends PlumbAction

case class PlumbRule(label: String, pattern: Regex, action: PlumbAction, template: String)

// Loads and evaluates user-defined plumbing rules from ~/.z/plumbing.
// Rules run top-to-bottom; first match wins.
//
// Rule file format (one rule per line):
//   match <label> /<regex>/ exec <shell-command-template>
//   match <label> /<regex>/ look <look-text-template>
//
// Template variables: $0 = full match, $1..$N = capture groups, $cwd = working dir.
// Regex may not contain a literal '/'; use a character class instead (e.g. [/]).
// Lines starting with '#' and blank lines are ignored.
// If ~/.z/plumbing does not exist, built-in rules are used.
// If it exists but contains no valid rules, no rules are active.
//
// Built-in rules (active when no plumbing file exists):
//   match url     /https?:\/\/\S+/        exec <open-cmd> $0
//   match filecol /^(.+):(\d+):(\d+)/     look $1:$2
object ZPlumbing {
	private val builtinRules: List[PlumbRule] = {
		val openCmd = System.getProperty("os.name", "") match {
			case mac if mac.toLowerCase.startsWith("mac os x") => "open"
			case _                                             => "xdg-open"
		}
		List(
			PlumbRule("url",     """https?://\S+""".r,       PlumbExec, s"$openCmd $$0"),
			PlumbRule("filecol", """^(.+):(\d+):(\d+)""".r, PlumbLook, "$1:$2"),
		)
	}

	var rules: List[PlumbRule] = builtinRules

	private val ruleRe = """^match\s+(\S+)\s+/([^/]+)/\s+(exec|look)\s+(.+)$""".r

	def load(): Unit = {
		val f = new File(Properties.userHome + ZUtilities.separator + ".z" + ZUtilities.separator + "plumbing")
		if (!f.exists()) { rules = builtinRules; return }
		rules = Using(io.Source.fromFile(f)) { src =>
			src.getLines()
				.map(_.trim)
				.filterNot(l => l.isEmpty || l.startsWith("#"))
				.flatMap {
					case ruleRe(label, pat, action, tmpl) =>
						scala.util.Try(PlumbRule(
							label,
							new Regex(pat),
							if (action == "exec") PlumbExec else PlumbLook,
							tmpl.trim
						)).toOption
					case _ => None
				}
				.toList
		}.getOrElse(builtinRules)
	}

	// Returns Some((action, expanded-template)) for the first matching rule, or None.
	def plumb(txt: String, cwd: String): Option[(PlumbAction, String)] =
		rules.iterator.flatMap { rule =>
			rule.pattern.findFirstMatchIn(txt).map(m => (rule.action, interpolate(rule.template, m, cwd)))
		}.nextOption()

	private def interpolate(template: String, m: Regex.Match, cwd: String): String = {
		var r = template.replace("$0", m.matched).replace("$cwd", cwd)
		// Substitute groups highest-first so $1 does not corrupt $10, $12, etc.
		(m.groupCount to 1 by -1).foreach(i => r = r.replace(s"$$$i", m.group(i)))
		r
	}
}
