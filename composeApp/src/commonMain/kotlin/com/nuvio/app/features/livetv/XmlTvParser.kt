package com.nuvio.app.features.livetv

internal data class XmlTvChannel(
    val id: String,
    val displayNames: List<String>,
)

internal data class XmlTvParseResult(
    val channels: Map<String, XmlTvChannel>,
    val programsByChannelId: Map<String, List<EpgProgram>>,
)

/**
 * Sequential scanner for XMLTV guides (guides run to tens of MB, so no DOM and no regex over the
 * whole text). Programmes outside [windowPastMs] before / [windowFutureMs] after [nowMs] are
 * dropped while parsing.
 */
internal object XmlTvParser {
    fun parse(
        xml: String,
        nowMs: Long,
        windowPastMs: Long = 24L * 3_600_000L,
        windowFutureMs: Long = 72L * 3_600_000L,
    ): XmlTvParseResult {
        val minEpoch = nowMs - windowPastMs
        val maxEpoch = nowMs + windowFutureMs
        val channels = mutableMapOf<String, XmlTvChannel>()
        val programs = mutableMapOf<String, MutableList<EpgProgram>>()

        forEachElement(xml, "channel") { header, body ->
            val id = attribute(header, "id")?.takeIf { it.isNotBlank() } ?: return@forEachElement
            val names = mutableListOf<String>()
            if (body != null) {
                var cursor = 0
                while (true) {
                    val text = childText(body, "display-name", cursor) ?: break
                    if (text.first.isNotBlank()) names += text.first
                    cursor = text.second
                }
            }
            channels[id] = XmlTvChannel(id, names)
        }

        forEachElement(xml, "programme") { header, body ->
            val channelId = attribute(header, "channel")?.takeIf { it.isNotBlank() } ?: return@forEachElement
            val start = attribute(header, "start")?.let(::parseXmlTvDate) ?: return@forEachElement
            val stop = attribute(header, "stop")?.let(::parseXmlTvDate) ?: return@forEachElement
            if (stop <= start || stop < minEpoch || start > maxEpoch || body == null) return@forEachElement
            val title = childText(body, "title", 0)?.first?.takeIf { it.isNotBlank() } ?: return@forEachElement
            programs.getOrPut(channelId) { mutableListOf() } += EpgProgram(
                channelId = channelId,
                title = title,
                description = childText(body, "desc", 0)?.first?.takeIf { it.isNotBlank() },
                startEpochMs = start,
                endEpochMs = stop,
                category = childText(body, "category", 0)?.first?.takeIf { it.isNotBlank() },
            )
        }

        return XmlTvParseResult(
            channels = channels,
            programsByChannelId = programs.mapValues { (_, list) -> list.sortedBy { it.startEpochMs } },
        )
    }

    private inline fun forEachElement(xml: String, tag: String, block: (header: String, body: String?) -> Unit) {
        val open = "<$tag"
        val close = "</$tag>"
        var cursor = 0
        while (cursor < xml.length) {
            val start = xml.indexOf(open, cursor)
            if (start == -1) return
            val afterName = start + open.length
            if (afterName < xml.length && !xml[afterName].isWhitespace() && xml[afterName] != '>' && xml[afterName] != '/') {
                cursor = afterName
                continue
            }
            val headerEnd = xml.indexOf('>', start)
            if (headerEnd == -1) return
            val selfClosing = xml[headerEnd - 1] == '/'
            if (selfClosing) {
                block(xml.substring(start, headerEnd), null)
                cursor = headerEnd + 1
                continue
            }
            val end = xml.indexOf(close, headerEnd)
            if (end == -1) return
            block(xml.substring(start, headerEnd), xml.substring(headerEnd + 1, end))
            cursor = end + close.length
        }
    }

    /** Text of the first <[tag]> at or after [from] in [body], and the index after its close tag. */
    private fun childText(body: String, tag: String, from: Int): Pair<String, Int>? {
        val start = body.indexOf("<$tag", from)
        if (start == -1) return null
        val headerEnd = body.indexOf('>', start)
        if (headerEnd == -1) return null
        if (body[headerEnd - 1] == '/') return "" to headerEnd + 1
        val close = "</$tag>"
        val end = body.indexOf(close, headerEnd)
        if (end == -1) return null
        return body.substring(headerEnd + 1, end).decodeXml().trim() to end + close.length
    }

    fun attribute(header: String, name: String): String? {
        for (quote in charArrayOf('"', '\'')) {
            val marker = " $name=$quote"
            val index = header.indexOf(marker)
            if (index == -1) continue
            val valueStart = index + marker.length
            val valueEnd = header.indexOf(quote, valueStart)
            if (valueEnd != -1) return header.substring(valueStart, valueEnd).decodeXml().trim()
        }
        return null
    }

    private fun String.decodeXml(): String {
        var text = this
        if (text.startsWith("<![CDATA[") && text.endsWith("]]>")) {
            return text.substring(9, text.length - 3)
        }
        if ('&' !in text) return text
        text = text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#34;", "\"")
            .replace("&#x27;", "'")
            .replace("&#x26;", "&")
        return text.replace("&amp;", "&")
    }

    /** XMLTV date: "YYYYMMDDhhmmss +hhmm" (seconds and offset optional; no offset means UTC). */
    fun parseXmlTvDate(raw: String): Long? {
        val text = raw.trim()
        if (text.length < 12) return null
        val digits = text.takeWhile { it.isDigit() }
        if (digits.length < 12) return null
        val year = digits.substring(0, 4).toIntOrNull() ?: return null
        val month = digits.substring(4, 6).toIntOrNull() ?: return null
        val day = digits.substring(6, 8).toIntOrNull() ?: return null
        val hour = digits.substring(8, 10).toIntOrNull() ?: return null
        val minute = digits.substring(10, 12).toIntOrNull() ?: return null
        val second = if (digits.length >= 14) digits.substring(12, 14).toIntOrNull() ?: 0 else 0
        if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null

        val rest = text.substring(digits.length).trim()
        var offsetMinutes = 0
        if (rest.length >= 5 && (rest[0] == '+' || rest[0] == '-')) {
            val hh = rest.substring(1, 3).toIntOrNull()
            val mm = rest.substring(3, 5).toIntOrNull()
            if (hh != null && mm != null) {
                offsetMinutes = (hh * 60 + mm) * if (rest[0] == '-') -1 else 1
            }
        }
        val epochSeconds = daysFromCivil(year, month, day) * 86_400L +
            hour * 3_600L + minute * 60L + second - offsetMinutes * 60L
        return epochSeconds * 1_000L
    }

    // Howard Hinnant's days_from_civil: days since 1970-01-01 for a proleptic Gregorian date.
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }
}
