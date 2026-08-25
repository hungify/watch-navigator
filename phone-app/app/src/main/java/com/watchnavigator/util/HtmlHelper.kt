package com.watchnavigator.util

import java.util.regex.Pattern

object HtmlHelper {
    private val HTML_TAG_PATTERN = Pattern.compile("<[^>]*>")
    private val ONTO_PATTERN = Pattern.compile("(?:onto|vào)\\s+<b>(.*?)</b>", Pattern.CASE_INSENSITIVE)
    private val ON_PATTERN = Pattern.compile("(?:on|trên)\\s+<b>(.*?)</b>", Pattern.CASE_INSENSITIVE)
    private val TOWARD_PATTERN = Pattern.compile("(?:toward|hướng|đến)\\s+<b>(.*?)</b>", Pattern.CASE_INSENSITIVE)
    private val LAST_BOLD_PATTERN = Pattern.compile("<b>(.*?)</b>", Pattern.CASE_INSENSITIVE)

    /**
     * Strips all HTML tags and decodes common HTML entities from instruction strings.
     */
    fun stripHtml(html: String?): String {
        if (html.isNullOrBlank()) return ""

        var text =
            html
                .replace("<div[^>]*>".toRegex(RegexOption.IGNORE_CASE), " ")
                .replace("</div>".toRegex(RegexOption.IGNORE_CASE), " ")
                .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), " ")
                .replace(HTML_TAG_PATTERN.toRegex(), "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")

        return text.replace("\\s+".toRegex(), " ").trim()
    }

    /**
     * Extracts the target street name from HTML instruction, e.g. "onto <b>Nguyen Trai</b>" or "on <b>Hang Khay</b>".
     */
    fun extractStreetName(
        html: String?,
        fallbackInstruction: String = ""
    ): String {
        if (!html.isNullOrBlank()) {
            // 1. Check "onto ..."
            val ontoMatcher = ONTO_PATTERN.matcher(html)
            if (ontoMatcher.find()) {
                val match = ontoMatcher.group(1)
                if (!match.isNullOrBlank()) return stripHtml(match)
            }

            // 2. Check "on ..."
            val onMatcher = ON_PATTERN.matcher(html)
            if (onMatcher.find()) {
                val match = onMatcher.group(1)
                if (!match.isNullOrBlank()) return stripHtml(match)
            }

            // 3. Check "toward ..."
            val towardMatcher = TOWARD_PATTERN.matcher(html)
            if (towardMatcher.find()) {
                val match = towardMatcher.group(1)
                if (!match.isNullOrBlank()) return stripHtml(match)
            }

            // 4. Fallback to bold tags
            val boldMatcher = LAST_BOLD_PATTERN.matcher(html)
            var lastBoldMatch: String? = null
            while (boldMatcher.find()) {
                val candidate = boldMatcher.group(1)
                val cleaned = stripHtml(candidate)
                if (cleaned.length > 1 && !isCardinalOrTurnDirection(cleaned)) {
                    lastBoldMatch = cleaned
                }
            }
            if (!lastBoldMatch.isNullOrBlank()) {
                return lastBoldMatch
            }
        }

        val plain = stripHtml(html).ifBlank { fallbackInstruction }
        return plain
    }

    private fun isCardinalOrTurnDirection(text: String): Boolean {
        val lower = text.lowercase()
        return lower in
            setOf(
                "north",
                "south",
                "east",
                "west",
                "northeast",
                "northwest",
                "southeast",
                "southwest",
                "left",
                "right",
                "straight",
                "trái",
                "phải",
                "thẳng",
                "bắc",
                "nam",
                "đông",
                "tây"
            )
    }
}
