package com.sparkstock.scanner

import com.sparkstock.scanner.data.TagParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagParserTest {
    @Test
    fun `reads the tag from a label's address`() {
        assertEquals("VR-0001", TagParser.parse("http://192.168.1.20:3100/t/VR-0001"))
        assertEquals("PC-0003", TagParser.parse("https://stock.example.com/t/pc-0003"))
        assertEquals("CONT-0001", TagParser.parse("  http://10.0.0.5:3100/t/CONT%2D0001 "))
    }

    @Test
    fun `takes a plain tag`() {
        assertEquals("VR-0001", TagParser.parse("vr-0001"))
        assertEquals("000001", TagParser.parse("000001"))
    }

    @Test
    fun `ignores anything else`() {
        assertNull(TagParser.parse(""))
        assertNull(TagParser.parse("https://example.com/menu"))
        assertNull(TagParser.parse("http://10.0.0.5:3100/devices/VR-0001"))
        assertNull(TagParser.parse("http://10.0.0.5:3100/t/VR-0001/edit"))
        assertNull(TagParser.parse("WIFI:S:office;T:WPA;P:secret;;"))
        assertNull(TagParser.parse("hello world"))
    }
}
