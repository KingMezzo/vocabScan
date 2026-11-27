package de.vocabscan.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.commonmark.node.*
import org.commonmark.node.Text as MarkdownText
import org.commonmark.node.Paragraph
import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.*
import java.net.URI

private val markdownParser = Parser.builder()
    .extensions(listOf(StrikethroughExtension.create(), TablesExtension.create()))
    .maxOpenBlockParsers(64).build()

internal fun parseReleaseMarkdown(source: String): Node = markdownParser.parse(source)

/** Legacy bundled notes are separate list entries; a GitHub body is one intact document. */
internal fun releaseNotesMarkdown(entries: List<String>): String = entries.filter { it.isNotBlank() }.let { notes ->
    if (notes.size > 1 && notes.all { !it.contains('\n') && parseReleaseMarkdown(it).firstChild is Paragraph })
        notes.joinToString("\n") { "- $it" }
    else notes.joinToString("\n\n")
}

private fun Node.children(): List<Node> = buildList {
    var child = firstChild
    while (child != null) { add(child); child = child.next }
}

internal fun allowedMarkdownLink(url: String): Boolean = runCatching {
    URI(url).scheme?.lowercase(java.util.Locale.ROOT) in setOf("https", "http", "mailto")
}.getOrDefault(false)

internal fun markdownInline(node: Node, linkColor: Color, codeColor: Color): AnnotatedString = buildAnnotatedString {
    fun appendNode(current: Node) {
        fun contents() { current.children().forEach { appendNode(it) } }
        when (current) {
            is MarkdownText -> append(current.literal)
            is SoftLineBreak -> append(" ")
            is HardLineBreak -> append("\n")
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { contents() }
            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { contents() }
            is Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { contents() }
            is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeColor)) { append(current.literal) }
            is Link -> if (allowedMarkdownLink(current.destination)) {
                withLink(LinkAnnotation.Url(current.destination, TextLinkStyles(SpanStyle(color = linkColor,
                    textDecoration = TextDecoration.Underline)))) { contents() }
            } else contents()
            // Images use their alt text, without fetching remote images in release dialogs.
            is Image -> contents()
            is HtmlInline -> Unit
            else -> contents()
        }
    }
    appendNode(node)
}

@Composable
internal fun MarkdownContent(source: String) {
    val document = remember(source) { parseReleaseMarkdown(source) }
    MarkdownBlocks(document)
}

@Composable
private fun MarkdownBlocks(parent: Node) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parent.children().forEach { node -> MarkdownBlock(node) }
    }
}

@Composable
private fun MarkdownBlock(node: Node) {
    when (node) {
        is Heading -> Text(markdownInline(node, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant),
            style = when (node.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }, modifier = Modifier.semantics { heading() })
        is Paragraph -> Text(markdownInline(node, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant),
            style = MaterialTheme.typography.bodyMedium)
        is BulletList, is OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            node.children().forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (node is OrderedList) "${node.markerStartNumber + index}." else "•",
                        style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.weight(1f)) { MarkdownBlocks(item) }
                }
            }
        }
        is BlockQuote -> Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.small) {
            Box(Modifier.fillMaxWidth().padding(8.dp)) { MarkdownBlocks(node) }
        }
        is FencedCodeBlock -> MarkdownCode(node.literal)
        is IndentedCodeBlock -> MarkdownCode(node.literal)
        is ThematicBreak -> HorizontalDivider()
        is TableBlock -> Column(Modifier.horizontalScroll(rememberScrollState())) {
            node.children().flatMap { it.children() }.forEach { row ->
                Row {
                    row.children().forEach { cell ->
                        Text(markdownInline(cell, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if ((cell as? TableCell)?.isHeader == true) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.width(160.dp).padding(8.dp))
                    }
                }
                HorizontalDivider()
            }
        }
        is HtmlBlock -> Text(node.literal, style = MaterialTheme.typography.bodyMedium)
        else -> MarkdownBlocks(node)
    }
}

@Composable
private fun MarkdownCode(code: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(code.trimEnd(), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth().padding(8.dp))
    }
}
