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

import org.fife.ui.autocomplete.{BasicCompletion, CompletionProvider}
import org.eclipse.lsp4j.CompletionItem
import javax.swing.Icon
import java.awt.{Color, Component, Font, Graphics, RenderingHints}
import java.util.concurrent.ConcurrentHashMap

// Wraps an LSP CompletionItem as an RSTA BasicCompletion, providing:
//   - getInputText()        → item.getLabel     (shown in the popup list)
//   - getReplacementText()  → insertText | label (inserted on commit)
//   - getShortDescription() → item.getDetail    (type signature, shown in gray)
//   - getIcon()             → colored kind badge (kind drives color + abbreviation)
//   - getSummary()          → HTML panel (detail + documentation), drives the desc window
//
// resolvedSummary is set by ZWnd after a successful completionItem/resolve call.
// getSummary() returns it if present, otherwise falls back to the detail-only initial view.
class ZLspCompletion(provider: CompletionProvider, val lspItem: CompletionItem)
    extends BasicCompletion(
      provider,
      Option(lspItem.getInsertText).filter(_.nonEmpty).getOrElse(lspItem.getLabel),
      Option(lspItem.getDetail).filter(_.nonEmpty).orNull
    ) {

  private val kindVal: Int = Option(lspItem.getKind).map(_.getValue).getOrElse(0)

  @volatile var resolvedSummary: Option[String] = None

  // Display the label (not insertText) in the popup list and use it for prefix filtering.
  override def getInputText: String = lspItem.getLabel

  override def getIcon: Icon = ZLspCompletion.iconForKind(kindVal)

  // HTML shown in the description panel to the right of the popup.
  // Returning null suppresses the panel; non-null triggers it automatically.
  override def getSummary: String =
    resolvedSummary.getOrElse(ZLspCompletion.buildSummary(lspItem))
}

object ZLspCompletion {

  // Build the description-panel HTML from whatever LSP fields are available on the item.
  // Called immediately on selection (detail only) and again after resolve (detail + docs).
  def buildSummary(item: CompletionItem): String = {
    val detail = Option(item.getDetail).filter(_.nonEmpty)
    val doc = Option(item.getDocumentation).flatMap { d =>
      val text = if (d.isLeft) Option(d.getLeft) else Option(d.getRight).map(_.getValue)
      text.filter(_.nonEmpty)
    }
    if (detail.isEmpty && doc.isEmpty) return null
    val sb = new StringBuilder
    sb.append("<html><body style='font-family:sans-serif; font-size:11pt; padding:6px;'>")
    detail.foreach { d =>
      sb.append(s"<p style='font-family:monospace; color:#004488; margin:0 0 4px 0;'>${escHtml(d)}</p>")
      if (doc.isDefined) sb.append("<hr style='margin:4px 0;'/>")
    }
    doc.foreach { d =>
      sb.append(s"<p style='white-space:pre-wrap; font-family:monospace; font-size:10pt; margin:0;'>${escHtml(d)}</p>")
    }
    sb.append("</body></html>")
    sb.toString
  }

  def escHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

  // (2-char abbreviation, badge background) indexed by LSP CompletionItemKind.getValue
  private val kindMeta: Array[(String, Color)] = {
    val arr = Array.fill(26)(("··", new Color(0x99, 0x99, 0x99)))
    arr(1)  = ("tx", new Color(0x99, 0x99, 0x99))  // Text
    arr(2)  = (" m", new Color(0x00, 0x55, 0xAA))  // Method
    arr(3)  = (" f", new Color(0x00, 0x55, 0xAA))  // Function
    arr(4)  = ("ct", new Color(0x00, 0x55, 0xAA))  // Constructor
    arr(5)  = (" F", new Color(0x77, 0x00, 0x77))  // Field
    arr(6)  = (" v", new Color(0x77, 0x00, 0x77))  // Variable
    arr(7)  = (" C", new Color(0x00, 0x77, 0x77))  // Class
    arr(8)  = (" I", new Color(0x00, 0x77, 0x77))  // Interface
    arr(9)  = ("ns", new Color(0x44, 0x44, 0x44))  // Module / Namespace
    arr(10) = (" p", new Color(0x77, 0x00, 0x77))  // Property
    arr(11) = (" u", new Color(0x88, 0x55, 0x00))  // Unit
    arr(12) = ("va", new Color(0x88, 0x55, 0x00))  // Value
    arr(13) = (" E", new Color(0x00, 0x77, 0x77))  // Enum
    arr(14) = ("kw", new Color(0x00, 0x00, 0xAA))  // Keyword
    arr(15) = ("sn", new Color(0x44, 0x44, 0x44))  // Snippet
    arr(16) = ("co", new Color(0x88, 0x55, 0x00))  // Color
    arr(17) = ("fi", new Color(0x44, 0x44, 0x44))  // File
    arr(18) = ("rf", new Color(0x44, 0x44, 0x44))  // Reference
    arr(19) = ("dr", new Color(0x44, 0x44, 0x44))  // Folder
    arr(20) = ("em", new Color(0x00, 0x77, 0x77))  // EnumMember
    arr(21) = (" k", new Color(0xAA, 0x22, 0x00))  // Constant
    arr(22) = ("st", new Color(0x00, 0x77, 0x77))  // Struct
    arr(23) = ("ev", new Color(0x88, 0x55, 0x00))  // Event
    arr(24) = ("op", new Color(0x00, 0x00, 0xAA))  // Operator
    arr(25) = ("tp", new Color(0x00, 0x77, 0x77))  // TypeParameter
    arr
  }

  private val iconCache = new ConcurrentHashMap[Int, Icon]()

  def iconForKind(kind: Int): Icon = iconCache.computeIfAbsent(kind, k => {
    val (lbl, color) = if (k > 0 && k < kindMeta.length) kindMeta(k) else kindMeta(0)
    new ZLspKindIcon(lbl.trim, color)
  })
}

// 20×14 rounded badge with a 2-char white abbreviation — used as the list-row icon.
// Painted with antialiasing so it looks clean at small sizes.
class ZLspKindIcon(label: String, bgColor: Color) extends Icon {
  override def getIconWidth:  Int = 20
  override def getIconHeight: Int = 14

  override def paintIcon(c: Component, g: Graphics, x: Int, y: Int): Unit = {
    val g2 = g.asInstanceOf[java.awt.Graphics2D]
    val savedHints = g2.getRenderingHints
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,     RenderingHints.VALUE_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g2.setColor(bgColor)
    g2.fillRoundRect(x, y + 1, getIconWidth, getIconHeight - 2, 4, 4)
    g2.setColor(Color.WHITE)
    val f = g2.getFont.deriveFont(Font.BOLD, 8.5f)
    g2.setFont(f)
    val fm = g2.getFontMetrics
    val tx = x + (getIconWidth  - fm.stringWidth(label)) / 2
    val ty = y + 1 + (getIconHeight - 2 + fm.getAscent - fm.getDescent) / 2
    g2.drawString(label, tx, ty)
    g2.setRenderingHints(savedHints)
  }
}
