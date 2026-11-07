package com.example.streaming

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object M3uParser {

    /**
     * Parse an M3U / M3U8 playlist input stream into a list of [StreamChannel]s.
     * Supports EXTINF tags with tvg-name, tvg-logo, and group-title attributes.
     */
    fun parse(inputStream: InputStream): List<StreamChannel> {
        val channels = mutableListOf<StreamChannel>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String?

        var currentName = ""
        var currentGroup = "عام"
        var currentLogo = ""

        val logoRegex = Regex("""tvg-logo="([^"]+)"""", RegexOption.IGNORE_CASE)
        val groupRegex = Regex("""group-title="([^"]+)"""", RegexOption.IGNORE_CASE)
        val nameRegex = Regex("""tvg-name="([^"]+)"""", RegexOption.IGNORE_CASE)

        while (reader.readLine().also { line = it } != null) {
            val trimmed = line!!.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                // Extract group-title
                currentGroup = groupRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: "عام"
                if (currentGroup.isBlank()) currentGroup = "عام"

                // Extract tvg-logo
                currentLogo = logoRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: ""

                // Extract tvg-name or fallback to text after last comma
                val tvgName = nameRegex.find(trimmed)?.groupValues?.get(1)?.trim()
                val commaIndex = trimmed.lastIndexOf(',')
                currentName = if (!tvgName.isNullOrBlank()) {
                    tvgName
                } else if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    trimmed.substring(commaIndex + 1).trim()
                } else {
                    "قناة بدون اسم"
                }
            } else if (!trimmed.startsWith("#")) {
                // This line is the stream URL
                val url = trimmed
                if (url.startsWith("http://", ignoreCase = true) ||
                    url.startsWith("https://", ignoreCase = true) ||
                    url.startsWith("rtsp://", ignoreCase = true)
                ) {
                    channels.add(
                        StreamChannel(
                            name = if (currentName.isNotBlank()) currentName else "بث مباشر #${channels.size + 1}",
                            url = url,
                            groupTitle = currentGroup,
                            logoUrl = currentLogo
                        )
                    )
                }
                // Reset for next channel
                currentName = ""
                currentGroup = "عام"
                currentLogo = ""
            }
        }

        return channels
    }

    /**
     * Default curated high-quality free legal streams (News, Science, Nature, Test streams)
     */
    fun getDefaultStreams(): List<StreamChannel> {
        return listOf(
            StreamChannel(
                name = "قناة الجزيرة الإخبارية (مباشر)",
                url = "https://live-hls-web-aje.getaj.net/AJE/03.m3u8",
                groupTitle = "أخبار",
                logoUrl = "",
                isFavorite = true
            ),
            StreamChannel(
                name = "BBC News Arabic (مباشر)",
                url = "https://vs-hls-push-uk-live.akamaized.net/x=4/i=urn:bbc:pips:service:bbc_arabic_tv/pc_hd_abr_v2.m3u8",
                groupTitle = "أخبار",
                logoUrl = "",
                isFavorite = true
            ),
            StreamChannel(
                name = "NASA TV (بث مباشر الفضاء)",
                url = "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8",
                groupTitle = "علوم ووثائقيات",
                logoUrl = ""
            ),
            StreamChannel(
                name = "Big Buck Bunny (HLS Test)",
                url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                groupTitle = "تجريبي",
                logoUrl = ""
            ),
            StreamChannel(
                name = "Tears of Steel (4K HLS Test)",
                url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                groupTitle = "تجريبي",
                logoUrl = ""
            )
        )
    }
}
