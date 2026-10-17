package com.example

import com.example.model.getShindoColorByText
import com.example.model.parseShindoScaleToText
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testShindoScaleConversion() {
        assertEquals("1", parseShindoScaleToText(10))
        assertEquals("2", parseShindoScaleToText(20))
        assertEquals("3", parseShindoScaleToText(30))
        assertEquals("4", parseShindoScaleToText(40))
        assertEquals("5弱", parseShindoScaleToText(45))
        assertEquals("5強", parseShindoScaleToText(50))
        assertEquals("6弱", parseShindoScaleToText(55))
        assertEquals("6強", parseShindoScaleToText(60))
        assertEquals("7", parseShindoScaleToText(70))
    }

    @Test
    fun testShindoColorResolves() {
        assertNotNull(getShindoColorByText("3"))
        assertNotNull(getShindoColorByText("5弱"))
        assertNotNull(getShindoColorByText("7"))
    }

    @Test
    fun testKmoniImageFetcherUrlBuilder() {
        val fetcher = com.example.network.KmoniImageFetcher()
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tokyo")).apply {
            set(2026, java.util.Calendar.OCTOBER, 17, 12, 34, 56)
        }
        val date = calendar.time

        // Surface Shindo image URL test
        val urlSurface = fetcher.buildImageUrl(date, com.example.network.KmoniImageType.SHINDO, com.example.model.DepthMode.SURFACE)
        assertEquals("http://www.kmoni.bosai.go.jp/data/map_img/RealtimeImg/jma_s/20261017/20261017123456.jma_s.gif", urlSurface)

        // Borehole PGA image URL test
        val urlBorehole = fetcher.buildImageUrl(date, com.example.network.KmoniImageType.PGA, com.example.model.DepthMode.BOREHOLE)
        assertEquals("http://www.kmoni.bosai.go.jp/data/map_img/RealtimeImg/acmap_b/20261017/20261017123456.acmap_b.gif", urlBorehole)

        // EEW JSON URL test
        val eewJsonUrl = fetcher.buildEEWJsonUrl(date)
        assertEquals("http://www.kmoni.bosai.go.jp/webservice/hypo/eew/20261017123456.json", eewJsonUrl)
    }

    @Test
    fun testWebSocketInitialStats() {
        val client = com.example.network.KyoshinWebSocketClient()
        val stats = client.stats.value
        assertEquals(com.example.network.WebSocketStatus.DISCONNECTED, stats.status)
        assertEquals("wss://api.p2pquake.net/v2/ws", stats.url)
        assertEquals(0L, stats.messagesReceivedCount)
    }
}
