package de.vocabscan.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.commonmark.node.*
import org.commonmark.ext.gfm.tables.TableBlock
import org.junit.Assert.*
import org.junit.Test

class MarkdownContentTest {
    @Test fun preservesMarkdownBlocksAndLegacyListEntries() {
        val source = "## Neu\n\n- Eins\n  - Zwei\n\n```kotlin\nval x = 1\n```"
        assertEquals(source, releaseNotesMarkdown(listOf(source)))
        assertEquals("- Eins\n- Zwei", releaseNotesMarkdown(listOf("Eins", "Zwei")))
        assertEquals("", releaseNotesMarkdown(listOf("  ")))
        val document = parseReleaseMarkdown(source)
        assertTrue(document.firstChild is Heading)
        val list = document.firstChild.next as BulletList
        assertTrue(list.firstChild.firstChild.next is BulletList)
        assertEquals("val x = 1\n", (list.next as FencedCodeBlock).literal)
    }

    @Test fun rendersInlineStylesLinksAndEscapedSyntax() {
        val node = parseReleaseMarkdown("**Fett** und *kursiv*, `code`, ~~alt~~ und [Details](https://example.org). \\*Literal\\*").firstChild
        val result = markdownInline(node, Color.Blue, Color.Gray)
        assertEquals("Fett und kursiv, code, alt und Details. *Literal*", result.text)
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertEquals(1, result.getLinkAnnotations(0, result.length).size)
    }

    @Test fun blocksExecutableLinksAndKeepsTheirLabels() {
        listOf("javascript:alert(1)", "intent://app", "file:///private", "data:text/html,test").forEach {
            assertFalse(allowedMarkdownLink(it))
        }
        assertTrue(allowedMarkdownLink("https://example.org"))
        val node = parseReleaseMarkdown("[Text](javascript:alert)").firstChild
        val result = markdownInline(node, Color.Blue, Color.Gray)
        assertEquals("Text", result.text)
        assertTrue(result.getLinkAnnotations(0, result.length).isEmpty())
    }

    @Test fun handlesOrderedListsQuotesAndTables() {
        val node = parseReleaseMarkdown("3. Drei\n4. Vier\n\n> Hinweis\n\n| A | B |\n| --- | --- |\n| 1 | 2 |")
        assertEquals(3, (node.firstChild as OrderedList).markerStartNumber)
        assertTrue(node.firstChild.next is BlockQuote)
        assertTrue(node.lastChild is TableBlock)
    }
}
