package com.example

import com.example.data.model.ArticleEntity
import com.example.data.pdf.ArticleContentBlock
import com.example.data.pdf.ArticleRichContentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleBookPdfParserTest {

    @Test
    fun parseBlocks_extractsHeadingsListsQuotesImagesAndParagraphs() {
        val raw = """
            ## Economic Outlook
            
            Bangladesh saw steady export growth.
            
            • Textile sector resilience
            - Remittance inflows
            1. Infrastructure completion
            
            > Sustainable growth requires diversification.
            
            ![Port operations](https://example.com/port.jpg)
            
            ### Closing Remarks
            Read more at [Daily Star](https://thedailystar.net/business).
        """.trimIndent()

        val blocks = ArticleRichContentParser.parseBlocks(raw)

        assertTrue(blocks[0] is ArticleContentBlock.Heading)
        assertEquals(2, (blocks[0] as ArticleContentBlock.Heading).level)
        assertEquals("Economic Outlook", (blocks[0] as ArticleContentBlock.Heading).text)

        assertTrue(blocks[1] is ArticleContentBlock.Paragraph)
        assertEquals("Bangladesh saw steady export growth.", (blocks[1] as ArticleContentBlock.Paragraph).text)

        assertTrue(blocks[2] is ArticleContentBlock.BulletItem)
        assertFalse((blocks[2] as ArticleContentBlock.BulletItem).isOrdered)
        assertTrue(blocks[3] is ArticleContentBlock.BulletItem)
        assertTrue(blocks[4] is ArticleContentBlock.BulletItem)
        assertTrue((blocks[4] as ArticleContentBlock.BulletItem).isOrdered)
        assertEquals("1.", (blocks[4] as ArticleContentBlock.BulletItem).marker)

        assertTrue(blocks[5] is ArticleContentBlock.Blockquote)
        assertEquals("Sustainable growth requires diversification.", (blocks[5] as ArticleContentBlock.Blockquote).text)

        assertTrue(blocks[6] is ArticleContentBlock.ImageBlock)
        assertEquals("Port operations", (blocks[6] as ArticleContentBlock.ImageBlock).caption)
        assertEquals("https://example.com/port.jpg", (blocks[6] as ArticleContentBlock.ImageBlock).url)

        assertTrue(blocks[7] is ArticleContentBlock.Heading)
        assertEquals(3, (blocks[7] as ArticleContentBlock.Heading).level)
    }

    @Test
    fun parseInlineSegments_splitsMarkdownLinksAndRawUrls() {
        val text = "Visit [Prothom Alo](https://prothomalo.com/news) or https://bbc.com/world today."
        val segments = ArticleRichContentParser.parseInlineSegments(text)

        assertEquals(5, segments.size)
        assertEquals("Visit ", segments[0].text)
        assertNull(segments[0].url)

        assertEquals("Prothom Alo", segments[1].text)
        assertEquals("https://prothomalo.com/news", segments[1].url)

        assertEquals(" or ", segments[2].text)
        assertNull(segments[2].url)

        assertEquals("https://bbc.com/world", segments[3].text)
        assertEquals("https://bbc.com/world", segments[3].url)

        assertEquals(" today.", segments[4].text)
        assertNull(segments[4].url)
    }

    @Test
    fun splitScriptRuns_separatesBengaliAndEnglishCleanly() {
        val bilingual = "বাংলাদেশ এবং Global Economy একসাথে এগিয়ে চলছে।"
        val runs = ArticleRichContentParser.splitScriptRuns(bilingual)

        assertEquals(3, runs.size)
        assertTrue(runs[0].isBengali)
        assertTrue(runs[0].text.contains("বাংলাদেশ"))

        assertFalse(runs[1].isBengali)
        assertTrue(runs[1].text.contains("Global Economy"))

        assertTrue(runs[2].isBengali)
        assertTrue(runs[2].text.contains("একসাথে"))
    }

    @Test
    fun toCleanReaderText_stripsMarkdownBracketsWhileKeepingReadableContent() {
        val raw = """
            ## Headline Here
            Read [Full Report](https://example.com/report) for details.
            ![Chart](https://example.com/chart.png)
        """.trimIndent()

        val cleaned = ArticleRichContentParser.toCleanReaderText(raw)
        assertFalse(cleaned.contains("##"))
        assertFalse(cleaned.contains("https://example.com/report"))
        assertTrue(cleaned.contains("Headline Here"))
        assertTrue(cleaned.contains("Read Full Report for details."))
        assertTrue(cleaned.contains("[Chart]"))
    }

    @Test
    fun deriveWebsiteNameAndPublishedDate_fallsBackFromUrlAndCreatedAt() {
        val art = ArticleEntity(
            id = "a1",
            title = "Test",
            content = "Body",
            author = "Reporter",
            sourceUrl = "https://www.thedailystar.net/opinion/article-1",
            websiteName = "",
            publishedDate = "",
            createdAt = 1_700_000_000_000L
        )

        assertEquals("thedailystar.net", ArticleRichContentParser.deriveWebsiteName(art))
        assertTrue(ArticleRichContentParser.derivePublishedDate(art).isNotBlank())
    }

    @Test
    fun parseBlocks_filtersNewspaperShareTagsAndEmails_whilePreservingRealContent() {
        val raw = """
            She announced plans to examine whether officials had violated ethics.

            NPR does not offer or accept money for coverage or interviews. Anyone who makes such an offer or request is not an NPR employee and does not represent NPR.

            • [trump](https://www.npr.org/tags/123/donald-trump)
            • Facebook
            • Flipboard
            • Email

            Tags: Politics, Courts, Justice Department

            Questions? Email desk@newspaper.com

            She sent an official email to the committee regarding Facebook advertising policy.

            • First key policy recommendation from the panel
            • Second budget reform item
        """.trimIndent()

        val blocks = ArticleRichContentParser.parseBlocks(raw)

        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is ArticleContentBlock.Paragraph)
        assertTrue((blocks[0] as ArticleContentBlock.Paragraph).text.contains("violated ethics"))

        assertTrue(blocks[1] is ArticleContentBlock.Paragraph)
        assertTrue((blocks[1] as ArticleContentBlock.Paragraph).text.contains("official email to the committee"))

        assertTrue(blocks[2] is ArticleContentBlock.BulletItem)
        assertEquals("First key policy recommendation from the panel", (blocks[2] as ArticleContentBlock.BulletItem).text)

        assertTrue(blocks[3] is ArticleContentBlock.BulletItem)
        assertEquals("Second budget reform item", (blocks[3] as ArticleContentBlock.BulletItem).text)
    }
}
