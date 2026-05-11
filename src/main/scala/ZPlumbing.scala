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
import scala.util.Using
import scala.util.matching.Regex

// ── Public API ────────────────────────────────────────────────────────────────

case class PlumbMessage(
	data:  String,
	wdir:  String,
	src:   String              = "",
	attrs: Map[String, String] = Map.empty
)

sealed trait PlumbPort
case object PlumbPortEdit extends PlumbPort  // open in editor (look)
case object PlumbPortExec extends PlumbPort  // run shell command

case class PlumbResult(
	port:    PlumbPort,
	message: PlumbMessage,         // data/attrs as mutated by rule actions
	cmd:     Option[String] = None // exec command (from `plumb start`)
)

// Loads and evaluates plumbing rules from ~/.z/plumbing.
//
// Rule file format: blank-line-separated blocks. Each block is a sequence of
// condition lines followed by action lines. All conditions must pass; actions
// are applied in order to produce the result. First matching block wins.
//
// Condition verbs:
//   data matches <regex>        — match msg.data; sets $0..$n from groups
//   arg isfile <path-template>  — expanded path must be an existing file; sets $arg
//   arg isdir  <path-template>  — expanded path must be an existing directory; sets $arg
//   wdir matches <regex>        — match msg.wdir
//   src is <value>              — exact match against msg.src
//   type is <value>             — always passes (accepted for Plan 9 compat)
//
// Action verbs:
//   data set <template>         — rewrite msg.data
//   attr add <key>=<template>   — add/update msg.attrs entry
//   attr set <key>=<template>   — alias for attr add
//   plumb to edit|exec          — set destination port
//   plumb start <cmd-template>  — command to run (required when port is exec);
//                                  only one per block — if repeated, last one wins;
//                                  use `sh -c "cmd1 && cmd2"` to chain commands
//   plumb client <program>      — ignored (Plan 9 compat)
//
// Template variables:
//   $0     — full match from data matches (or msg.data if no data matches)
//   $1..$n — capture groups from data matches
//   $wdir  — working directory (msg.wdir)
//   $arg   — absolute path resolved by arg isfile/isdir ($file is an alias)
//   $file  — alias for $arg
//
// Lines starting with '#' and blank lines are ignored within any block.
// Old single-line format is accepted:
//   match <label> /<regex>/ exec <template>
//   match <label> /<regex>/ look <template>
// If ~/.z/plumbing does not exist, built-in rules are used.
// If it exists but contains no valid blocks, no rules are active.
object ZPlumbing {

	// ── Internal rule model ───────────────────────────────────────────────────

	sealed trait Cond
	case class CondDataMatches(re: Regex)    extends Cond
	case class CondArgIsFile(tmpl: String)   extends Cond
	case class CondArgIsDir(tmpl: String)    extends Cond
	case class CondWdirMatches(re: Regex)    extends Cond
	case class CondSrcIs(value: String)      extends Cond
	case class CondTypeIs(value: String)     extends Cond

	sealed trait Act
	case class ActAttrAdd(key: String, valTmpl: String) extends Act
	case class ActDataSet(tmpl: String)                  extends Act
	case class ActPlumbTo(port: String)                  extends Act
	case class ActPlumbStart(cmdTmpl: String)            extends Act

	case class Block(conds: List[Cond], acts: List[Act])

	// State accumulated during condition evaluation of one block
	private case class CondState(groups: IndexedSeq[String], arg: String)

	// Context available to interpolate() at any point during evaluation
	private case class InterpCtx(
		groups: IndexedSeq[String],
		wdir:   String,
		arg:    String,
		data:   String,
		attrs:  Map[String, String]
	)

	// ── Built-in rules ────────────────────────────────────────────────────────

	private val builtinBlocks: List[Block] = {
		val openCmd = System.getProperty("os.name", "").toLowerCase match {
			case mac if mac.startsWith("mac") => "open"
			case _                            => "xdg-open"
		}
		List(
			// URL → open in browser
			Block(
				conds = List(CondDataMatches("""https?://\S+""".r)),
				acts  = List(ActPlumbTo("exec"), ActPlumbStart(s"$openCmd $$0"))
			),
			// file:line:col — file must exist; navigate to line
			Block(
				conds = List(CondDataMatches("""^(.+):(\d+):(\d+)$""".r), CondArgIsFile("$1")),
				acts  = List(ActDataSet("$1"), ActAttrAdd("addr", "$2"), ActPlumbTo("edit"))
			),
			// file:line — file must exist; navigate to line
			Block(
				conds = List(CondDataMatches("""^(.+):(\d+)$""".r), CondArgIsFile("$1")),
				acts  = List(ActDataSet("$1"), ActAttrAdd("addr", "$2"), ActPlumbTo("edit"))
			),
		)
	}

	// ── Public state & API ────────────────────────────────────────────────────

	var rules: List[Block] = builtinBlocks

	def load(): Unit = {
		val f = new File(Properties.userHome + ZUtilities.separator + ".z" + ZUtilities.separator + "plumbing")
		if (!f.exists()) { rules = builtinBlocks; return }
		rules = Using(io.Source.fromFile(f)) { src =>
			parseBlocks(src.mkString)
		}.getOrElse(builtinBlocks)
	}

	// Returns the first matching PlumbResult, or None.
	def plumb(msg: PlumbMessage): Option[PlumbResult] =
		rules.iterator.flatMap(evalBlock(_, msg)).nextOption()

	// ── Rule evaluation ───────────────────────────────────────────────────────

	private def evalBlock(block: Block, msg: PlumbMessage): Option[PlumbResult] = {
		// Phase 1: evaluate all conditions, threading state through
		val condResult = block.conds.foldLeft(Option(CondState(IndexedSeq.empty, ""))) {
			case (None, _) => None
			case (Some(st), cond) =>
				val ctx = InterpCtx(st.groups, msg.wdir, st.arg, msg.data, msg.attrs)
				cond match {
					case CondDataMatches(re) =>
						re.findFirstMatchIn(msg.data).map { m =>
							val gs = (0 to m.groupCount).map(i => if (i == 0) m.matched else m.group(i)).toIndexedSeq
							st.copy(groups = gs)
						}
					case CondArgIsFile(tmpl) =>
						val f = scala.util.Try(resolveCanonical(msg.wdir, interpolate(tmpl, ctx))).toOption
						f match {
							case Some(file) if file.exists() && file.isFile => Some(st.copy(arg = file.getPath))
							case _                                           => None
						}
					case CondArgIsDir(tmpl) =>
						val f = scala.util.Try(resolveCanonical(msg.wdir, interpolate(tmpl, ctx))).toOption
						f match {
							case Some(file) if file.exists() && file.isDirectory => Some(st.copy(arg = file.getPath))
							case _                                                => None
						}
					case CondWdirMatches(re) =>
						if (re.findFirstIn(msg.wdir).isDefined) Some(st) else None
					case CondSrcIs(v) =>
						if (msg.src == v) Some(st) else None
					case CondTypeIs(_) => Some(st)  // always passes
				}
		}

		// Phase 2: apply actions to produce PlumbResult
		condResult.flatMap { st =>
			var data  = msg.data
			var attrs = msg.attrs
			var port: Option[PlumbPort] = None
			var cmd:  Option[String]    = None

			block.acts.foreach { act =>
				val ctx = InterpCtx(st.groups, msg.wdir, st.arg, data, attrs)
				act match {
					case ActDataSet(tmpl)    => data  = interpolate(tmpl, ctx)
					case ActAttrAdd(k, vt)   => attrs = attrs + (k -> interpolate(vt, ctx))
					case ActPlumbTo("exec")  => port  = Some(PlumbPortExec)
					case ActPlumbTo(_)       => port  = Some(PlumbPortEdit)
					case ActPlumbStart(tmpl) => cmd   = Some(interpolate(tmpl, ctx))
				}
			}

			port.map(p => PlumbResult(p, msg.copy(data = data, attrs = attrs), cmd))
		}
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	// Resolve an expanded path against wdir. Absolute paths are used directly;
	// relative paths are joined to wdir. Uses canonical form to resolve symlinks.
	private def resolveCanonical(wdir: String, expanded: String): File =
		(if (new File(expanded).isAbsolute) new File(expanded)
		 else new File(wdir, expanded)).getCanonicalFile

	// ── Template interpolation ────────────────────────────────────────────────

	private def interpolate(template: String, ctx: InterpCtx): String = {
		var r = template
			.replace("$wdir", ctx.wdir)
			.replace("$arg",  ctx.arg)
			.replace("$file", ctx.arg)
		r = r.replace("$0", ctx.data)  // $0 = full data field (Plan 9 semantics)
		// Substitute groups highest-first to avoid $1 corrupting $10, $12, etc.
		(ctx.groups.length - 1 to 1 by -1).foreach(i => r = r.replace(s"$$$i", ctx.groups(i)))
		r
	}

	// ── Rule file parser ──────────────────────────────────────────────────────

	// Line-level patterns
	private val pCondDataMatches = """^data\s+matches\s+(.+)$""".r
	private val pCondArgIsFile   = """^arg\s+isfile\s+(.+)$""".r
	private val pCondArgIsDir    = """^arg\s+isdir\s+(.+)$""".r
	private val pCondWdirMatches = """^wdir\s+matches\s+(.+)$""".r
	private val pCondSrcIs       = """^src\s+is\s+(.+)$""".r
	private val pCondTypeIs      = """^type\s+is\s+(.+)$""".r
	private val pActAttrAddSet   = """^attr\s+(?:add|set)\s+(\w+)=(.*)$""".r
	private val pActDataSet      = """^data\s+set\s+(.+)$""".r
	private val pActPlumbTo      = """^plumb\s+to\s+(\S+)$""".r
	private val pActPlumbStart   = """^plumb\s+start\s+(.+)$""".r
	// Old single-line format (backward compat)
	private val pLegacy          = """^match\s+\S+\s+/([^/]+)/\s+(exec|look)\s+(.+)$""".r

	// Parse a full rules file (string) into blocks.
	def parseBlocks(src: String): List[Block] = {
		// Split into groups of non-blank lines (blank lines separate blocks)
		val lineGroups = src.linesIterator
			.map(_.trim)
			.foldLeft(List(List.empty[String])) { (acc, line) =>
				if (line.isEmpty) List.empty[String] :: acc
				else              (line :: acc.head) :: acc.tail
			}
			.map(_.reverse)
			.reverse
			.filter(_.nonEmpty)

		lineGroups.flatMap(parseBlockLines).toList
	}

	// Parse one group of lines (a single block) into a Block, or None if invalid.
	private def parseBlockLines(lines: List[String]): Option[Block] = {
		var conds = List.empty[Cond]
		var acts  = List.empty[Act]

		lines.filterNot(_.startsWith("#")).foreach {
			case pCondDataMatches(re) =>
				scala.util.Try(new Regex(re)).foreach(r => conds :+= CondDataMatches(r))
			case pCondArgIsFile(tmpl)   => conds :+= CondArgIsFile(tmpl.trim)
			case pCondArgIsDir(tmpl)    => conds :+= CondArgIsDir(tmpl.trim)
			case pCondWdirMatches(re)   =>
				scala.util.Try(new Regex(re)).foreach(r => conds :+= CondWdirMatches(r))
			case pCondSrcIs(v)          => conds :+= CondSrcIs(v.trim)
			case pCondTypeIs(v)         => conds :+= CondTypeIs(v.trim)
			case pActAttrAddSet(k, vt)  => acts  :+= ActAttrAdd(k, vt)
			case pActDataSet(tmpl)      => acts  :+= ActDataSet(tmpl.trim)
			case pActPlumbTo(port)      => acts  :+= ActPlumbTo(port)
			case pActPlumbStart(tmpl)   => acts  :+= ActPlumbStart(tmpl.trim)
			case pLegacy(re, action, tmpl) =>
				// Convert old single-line format into equivalent block
				scala.util.Try(new Regex(re)).foreach { r =>
					conds :+= CondDataMatches(r)
					if (action == "exec") {
						acts :+= ActPlumbTo("exec")
						acts :+= ActPlumbStart(tmpl.trim)
					} else {
						acts :+= ActDataSet(tmpl.trim)
						acts :+= ActPlumbTo("edit")
					}
				}
			case _ => // unknown line — ignore
		}

		if (acts.exists { case _: ActPlumbTo => true; case _ => false })
			Some(Block(conds, acts))
		else
			None
	}
}
