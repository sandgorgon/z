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

import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.jsonrpc.services.{JsonNotification, JsonRequest}
import java.util.concurrent.{CompletableFuture, CopyOnWriteArrayList, TimeUnit}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

// One ZLspClient per (rootUri, langId) pair — shared across all windows in the same workspace.
// Uses full-content sync (TextDocumentSyncKind.Full).
//
// We do NOT implement the LanguageClient interface directly because in lsp4j 0.21+
// both LanguageClient and LanguageClientExtensions declare workspace/applyEdit,
// causing ServiceEndpoints.getSupportedMethods to throw "Duplicate RPC method".
// Instead, we annotate each method once and pass `this` to LSPLauncher.Builder
// which accepts any Object as the local service.
//
// Threading: all public methods are safe to call from the EDT.
// Callbacks are invoked on LSP4J background threads — callers must dispatch to EDT themselves.
// Params for Metals' proprietary metals/status notification.
class MetalsStatusParams {
	var text:    String  = ""
	var show:    Boolean = false
	var hide:    Boolean = false
	var tooltip: String  = ""
}

class ZLspClient(langId: String, serverCmd: String, rootUri: String) {

	private var server:  LanguageServer = scala.compiletime.uninitialized
	private var process: Process        = scala.compiletime.uninitialized
	@volatile private var ready         = false

	private val progressTokens   = ConcurrentHashMap.newKeySet[String]()
	private val diagCallbacks    = new ConcurrentHashMap[String, List[Diagnostic] => Unit]()
	private val onReadyListeners = new CopyOnWriteArrayList[() => Unit]()
	private val indexListeners   = new CopyOnWriteArrayList[() => Unit]()
	private val statusListeners  = new CopyOnWriteArrayList[MetalsStatusParams => Unit]()

	def indexing: Boolean = !progressTokens.isEmpty

	// Number of files currently registered — used by ZLspManager for reference-counting.
	def subscriberCount: Int = diagCallbacks.size()

	// ── per-file registration ────────────────────────────────────────────────

	def registerDiag(uri: String, cb: List[Diagnostic] => Unit): Unit = diagCallbacks.put(uri, cb)
	def unregisterDiag(uri: String): Unit                             = diagCallbacks.remove(uri)

	def addIndexListener(cb: () => Unit): Unit                        = indexListeners.add(cb)
	def removeIndexListener(cb: () => Unit): Unit                     = indexListeners.remove(cb)
	def addStatusListener(cb: MetalsStatusParams => Unit): Unit       = statusListeners.add(cb)
	def removeStatusListener(cb: MetalsStatusParams => Unit): Unit    = statusListeners.remove(cb)

	// ── lifecycle ────────────────────────────────────────────────────────────

	def start(): Unit = {
		if (ready) return
		val tokens = ZUtilities.tokenize(serverCmd)
		process = new ProcessBuilder(tokens*).start()

		val launcher = new LSPLauncher.Builder[LanguageServer]()
			.setLocalService(this)
			.setRemoteInterface(classOf[LanguageServer])
			.setInput(process.getInputStream)
			.setOutput(process.getOutputStream)
			.create()

		server = launcher.getRemoteProxy
		launcher.startListening()

		val params = new InitializeParams
		params.setCapabilities(clientCapabilities())
		params.setRootUri(rootUri)
		params.setWorkspaceFolders(java.util.List.of(new WorkspaceFolder(rootUri, "workspace")))
		params.setProcessId(ProcessHandle.current().pid().toInt)

		server.initialize(params).thenAccept { _ =>
			server.initialized(new InitializedParams)
			this.synchronized {
				ready = true
				onReadyListeners.forEach(_.apply())
				onReadyListeners.clear()
			}
		}.exceptionally { ex =>
			System.err.println(s"[z] LSP initialize failed: ${ex.getMessage}")
			null
		}
	}

	// Invoke cb immediately if already ready; otherwise queue for when initialize completes.
	// Safe to call from any thread.
	def whenReady(cb: () => Unit): Unit = this.synchronized {
		if (ready) cb() else onReadyListeners.add(cb)
	}

	// ── LSP document operations (callers supply uri and version) ─────────────

	def didOpen(uri: String, content: String, version: Int): Unit = if (ready) {
		val item = new TextDocumentItem(uri, langId, version, content)
		server.getTextDocumentService.didOpen(new DidOpenTextDocumentParams(item))
	}

	def didChange(uri: String, content: String, version: Int): Unit = if (ready) {
		val docId  = new VersionedTextDocumentIdentifier(uri, version)
		val change = new TextDocumentContentChangeEvent(content)
		server.getTextDocumentService.didChange(
			new DidChangeTextDocumentParams(docId, java.util.List.of(change))
		)
	}

	def didClose(uri: String): Unit = if (ready) {
		server.getTextDocumentService.didClose(
			new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri))
		)
	}

	// Async completion. callback invoked on a background thread; caller marshals to EDT.
	def completion(uri: String, line: Int, col: Int, callback: List[org.eclipse.lsp4j.CompletionItem] => Unit): Unit = if (ready) {
		val params = new CompletionParams(new TextDocumentIdentifier(uri), new Position(line, col))
		server.getTextDocumentService.completion(params).thenAccept { result =>
			val items =
				if (result == null) List.empty
				else if (result.isLeft) result.getLeft.asScala.toList
				else result.getRight.getItems.asScala.toList
			callback(items)
		}.exceptionally { ex =>
			System.err.println(s"[z] LSP completion failed: ${ex.getMessage}")
			callback(List.empty)
			null
		}
	}

	// Async completionItem/resolve — fetches full documentation for a single item.
	// Most LSP servers (including Metals) defer documentation to this call.
	// callback is invoked on a background thread; caller must dispatch to EDT for UI.
	def resolveCompletion(item: org.eclipse.lsp4j.CompletionItem, callback: org.eclipse.lsp4j.CompletionItem => Unit): Unit = if (ready) {
		server.getTextDocumentService.resolveCompletionItem(item).thenAccept { resolved =>
			callback(if (resolved != null) resolved else item)
		}.exceptionally { _ =>
			callback(item)
			null
		}
	}

	// Async hover. callback invoked on a background thread; caller marshals to EDT.
	def hover(uri: String, line: Int, col: Int, callback: String => Unit): Unit = if (ready) {
		val params = new HoverParams(new TextDocumentIdentifier(uri), new Position(line, col))
		server.getTextDocumentService.hover(params).thenAccept { h =>
			if (h != null) {
				val text = extractHoverText(h)
				if (text.nonEmpty) callback(text)
			}
		}
	}

	def shutdown(): Unit = {
		ready = false
		try { server.shutdown().get(2, TimeUnit.SECONDS) } catch { case _: Throwable => }
		try { server.exit() }                              catch { case _: Throwable => }
		try { process.destroy() }                          catch { case _: Throwable => }
	}

	// ── LSP4J incoming callbacks (annotated directly to avoid LanguageClient hierarchy) ──────

	@JsonNotification("textDocument/publishDiagnostics")
	def publishDiagnostics(params: PublishDiagnosticsParams): Unit =
		Option(diagCallbacks.get(params.getUri)).foreach(_(params.getDiagnostics.asScala.toList))

	@JsonNotification("$/progress")
	def notifyProgress(params: ProgressParams): Unit = {
		val token  = if (params.getToken.isLeft) params.getToken.getLeft else params.getToken.getRight.toString
		val before = indexing
		Option(params.getValue).filter(_.isLeft).map(_.getLeft).foreach {
			case _: WorkDoneProgressBegin => progressTokens.add(token)
			case _: WorkDoneProgressEnd   => progressTokens.remove(token)
			case _ =>
		}
		if (indexing != before) indexListeners.forEach(_.apply())
	}

	@JsonNotification("metals/status")
	def metalsStatus(params: MetalsStatusParams): Unit = statusListeners.forEach(_(params))

	@JsonNotification("window/showMessage")
	def showMessage(params: MessageParams): Unit = {}

	// Auto-accept the first action (e.g. Metals' "Import build" prompt).
	@JsonRequest("window/showMessageRequest")
	def showMessageRequest(params: ShowMessageRequestParams): CompletableFuture[MessageActionItem] =
		CompletableFuture.completedFuture(
			Option(params.getActions).flatMap(a => Option(a.get(0))).orNull
		)

	@JsonNotification("window/logMessage")
	def logMessage(params: MessageParams): Unit = {}

	@JsonNotification("telemetry/event")
	def telemetryEvent(obj: AnyRef): Unit = {}

	// Stub: let server know we don't apply workspace edits (returns applied=false).
	@JsonRequest("workspace/applyEdit")
	def applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture[ApplyWorkspaceEditResponse] = {
		val r = new ApplyWorkspaceEditResponse
		r.setApplied(false)
		CompletableFuture.completedFuture(r)
	}

	// ── Helpers ──────────────────────────────────────────────────────────────────────

	// Declare hover and completion capabilities.
	private def clientCapabilities(): ClientCapabilities = {
		val hoverCap = new HoverCapabilities
		hoverCap.setContentFormat(java.util.List.of("plaintext", "markdown"))

		val resolveSupport = new org.eclipse.lsp4j.CompletionItemResolveSupportCapabilities
		resolveSupport.setProperties(java.util.List.of("documentation", "detail"))
		val completionItemCap = new CompletionItemCapabilities
		completionItemCap.setSnippetSupport(false)
		completionItemCap.setResolveSupport(resolveSupport)
		val completionCap = new CompletionCapabilities
		completionCap.setCompletionItem(completionItemCap)

		val textDoc = new TextDocumentClientCapabilities
		textDoc.setHover(hoverCap)
		textDoc.setCompletion(completionCap)
		val caps = new ClientCapabilities
		caps.setTextDocument(textDoc)
		caps
	}

	// Extract human-readable text from Either<List<Either<String,MarkedString>>, MarkupContent>.
	private def extractHoverText(h: Hover): String = {
		val contents = h.getContents
		if (contents == null) return ""
		if (contents.isRight) {
			Option(contents.getRight).map(_.getValue).getOrElse("")
		} else {
			Option(contents.getLeft).map(_.asScala.map { e =>
				if (e.isRight) e.getRight.getValue else e.getLeft
			}.mkString("\n")).getOrElse("")
		}
	}
}
