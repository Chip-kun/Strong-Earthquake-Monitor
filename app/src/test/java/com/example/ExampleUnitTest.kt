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
}
