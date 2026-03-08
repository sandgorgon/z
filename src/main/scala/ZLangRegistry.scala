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

import org.fife.ui.rsyntaxtextarea.SyntaxConstants

object ZLangRegistry {

	// File extension → RSTA syntax style
	// SQL entries use SYNTAX_STYLE_SQL (ANSI base); PostgreSQL-specific tokens
	// ($$-quoting, :: cast, pg functions) are handled by ZTheme keyword colouring.
	private val byExt: Map[String, String] = Map(
		"java"       -> SyntaxConstants.SYNTAX_STYLE_JAVA,
		"scala"      -> SyntaxConstants.SYNTAX_STYLE_SCALA,
		"sc"         -> SyntaxConstants.SYNTAX_STYLE_SCALA,
		"sbt"        -> SyntaxConstants.SYNTAX_STYLE_SCALA,
		"py"         -> SyntaxConstants.SYNTAX_STYLE_PYTHON,
		"go"         -> SyntaxConstants.SYNTAX_STYLE_GO,
		"sql"        -> SyntaxConstants.SYNTAX_STYLE_SQL,
		"sh"         -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL,
		"bash"       -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL,
		"zsh"        -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL,
		"js"         -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT,
		"ts"         -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT,
		"xml"        -> SyntaxConstants.SYNTAX_STYLE_XML,
		"html"       -> SyntaxConstants.SYNTAX_STYLE_HTML,
		"htm"        -> SyntaxConstants.SYNTAX_STYLE_HTML,
		"json"       -> SyntaxConstants.SYNTAX_STYLE_JSON,
		"yaml"       -> SyntaxConstants.SYNTAX_STYLE_YAML,
		"yml"        -> SyntaxConstants.SYNTAX_STYLE_YAML,
		"c"          -> SyntaxConstants.SYNTAX_STYLE_C,
		"h"          -> SyntaxConstants.SYNTAX_STYLE_C,
		"cpp"        -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS,
		"cc"         -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS,
		"hpp"        -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS,
		"kt"         -> SyntaxConstants.SYNTAX_STYLE_KOTLIN,
		"rb"         -> SyntaxConstants.SYNTAX_STYLE_RUBY,
		"rs"         -> SyntaxConstants.SYNTAX_STYLE_RUST,
		"md"         -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN,
		"properties" -> SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE,
		"groovy"     -> SyntaxConstants.SYNTAX_STYLE_GROOVY,
		"lua"        -> SyntaxConstants.SYNTAX_STYLE_LUA,
		"php"        -> SyntaxConstants.SYNTAX_STYLE_PHP,
		"css"        -> SyntaxConstants.SYNTAX_STYLE_CSS,
		"less"       -> SyntaxConstants.SYNTAX_STYLE_LESS,
	)

	// Human-readable language name → RSTA syntax style (used by Hilite <lang>)
	private val byName: Map[String, String] = byExt ++ Map(
		"java"       -> SyntaxConstants.SYNTAX_STYLE_JAVA,
		"scala"      -> SyntaxConstants.SYNTAX_STYLE_SCALA,
		"python"     -> SyntaxConstants.SYNTAX_STYLE_PYTHON,
		"go"         -> SyntaxConstants.SYNTAX_STYLE_GO,
		"sql"        -> SyntaxConstants.SYNTAX_STYLE_SQL,
		"postgres"   -> SyntaxConstants.SYNTAX_STYLE_SQL,
		"postgresql" -> SyntaxConstants.SYNTAX_STYLE_SQL,
		"bash"       -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL,
		"shell"      -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL,
		"javascript" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT,
		"typescript" -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT,
		"kotlin"     -> SyntaxConstants.SYNTAX_STYLE_KOTLIN,
		"ruby"       -> SyntaxConstants.SYNTAX_STYLE_RUBY,
		"rust"       -> SyntaxConstants.SYNTAX_STYLE_RUST,
		"c"          -> SyntaxConstants.SYNTAX_STYLE_C,
		"cpp"        -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS,
		"xml"        -> SyntaxConstants.SYNTAX_STYLE_XML,
		"html"       -> SyntaxConstants.SYNTAX_STYLE_HTML,
		"json"       -> SyntaxConstants.SYNTAX_STYLE_JSON,
		"yaml"       -> SyntaxConstants.SYNTAX_STYLE_YAML,
		"markdown"   -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN,
		"groovy"     -> SyntaxConstants.SYNTAX_STYLE_GROOVY,
		"lua"        -> SyntaxConstants.SYNTAX_STYLE_LUA,
		"php"        -> SyntaxConstants.SYNTAX_STYLE_PHP,
		"css"        -> SyntaxConstants.SYNTAX_STYLE_CSS,
	)

	def forPath(path: String): String = {
		val ext = path.split('.').lastOption.map(_.toLowerCase).getOrElse("")
		byExt.getOrElse(ext, SyntaxConstants.SYNTAX_STYLE_NONE)
	}

	def forLang(lang: String): String =
		byName.getOrElse(lang.toLowerCase, SyntaxConstants.SYNTAX_STYLE_NONE)

	// File extension → LSP language ID (used for didOpen and server selection)
	private val langIdByExt: Map[String, String] = Map(
		"go"         -> "go",
		"py"         -> "python",
		"java"       -> "java",
		"scala"      -> "scala",
		"sc"         -> "scala",
		"sbt"        -> "scala",
		"sh"         -> "shellscript",
		"bash"       -> "shellscript",
		"zsh"        -> "shellscript",
		"js"         -> "javascript",
		"ts"         -> "typescript",
		"kt"         -> "kotlin",
		"rb"         -> "ruby",
		"rs"         -> "rust",
		"cpp"        -> "cpp",
		"cc"         -> "cpp",
		"hpp"        -> "cpp",
		"c"          -> "c",
		"h"          -> "c",
		"cs"         -> "csharp",
		"sql"        -> "sql",
		"json"       -> "json",
		"yaml"       -> "yaml",
		"yml"        -> "yaml",
		"xml"        -> "xml",
		"html"       -> "html",
		"htm"        -> "html",
		"css"        -> "css",
		"md"         -> "markdown",
		"lua"        -> "lua",
		"php"        -> "php",
	)

	def langIdFor(path: String): String = {
		val ext = path.split('.').lastOption.map(_.toLowerCase).getOrElse("")
		langIdByExt.getOrElse(ext, "plaintext")
	}
}
