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

import java.io.File
import util.Properties

// Manages LSP server command resolution and active client lifecycle.
// Reads ~/.z/lsp.conf (key=value, e.g. "go = gopls") to override built-in defaults.
// Call loadConf() at startup and shutdown() on app exit.
object ZLspManager {

	// Built-in defaults: LSP language ID → server launch command
	private val defaults: Map[String, String] = Map(
		"go"          -> "gopls",
		"python"      -> "pylsp",
		"java"        -> "jdtls",
		"scala"       -> "metals",
		"shellscript" -> "bash-language-server start",
		"typescript"  -> "typescript-language-server --stdio",
		"javascript"  -> "typescript-language-server --stdio",
		"rust"        -> "rust-analyzer",
		"kotlin"      -> "kotlin-language-server",
	)

	private var userConf: Map[String, String] = Map.empty
	private var clients: List[ZLspClient]     = Nil

	def loadConf(): Unit = {
		val f = new File(
			Properties.userHome + ZUtilities.separator + ".z" +
			ZUtilities.separator + "lsp.conf"
		)
		if (f.exists()) userConf = ZSettings.load(f)
	}

	// Returns the server launch command for a language ID, user conf overrides defaults.
	def serverCmd(langId: String): Option[String] =
		userConf.get(langId).orElse(defaults.get(langId))

	def register(c: ZLspClient): Unit   = synchronized { clients = c :: clients }
	def unregister(c: ZLspClient): Unit = synchronized { clients = clients.filterNot(_ == c) }

	// Called on app exit — shuts down all active LSP servers cleanly.
	def shutdown(): Unit = synchronized {
		clients.foreach { c =>
			try { c.shutdown() } catch { case _: Throwable => }
		}
		clients = Nil
	}
}
