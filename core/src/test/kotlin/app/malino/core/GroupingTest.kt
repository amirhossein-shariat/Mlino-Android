package app.malino.core

import kotlin.test.*

class GroupingTest {
    @Test fun groupsFromTheRight() {
        assertEquals("", Grouping.group("", ','))
        assertEquals("7", Grouping.group("7", ','))
        assertEquals("123", Grouping.group("123", ','))
        assertEquals("1,234", Grouping.group("1234", ','))
        assertEquals("1,069,703", Grouping.group("1069703", ','))
        assertEquals("612,480,000", Grouping.group("612480000", ','))
        assertEquals("1٬000", Grouping.group("1000", '٬'))
    }

    @Test fun cursorMappingRoundTripsAtEveryPosition() {
        for (n in 0..13) {
            val shown = Grouping.group("1".repeat(n), ',')
            assertEquals(shown.length, Grouping.toTransformed(n, n), "end of $n digits")
            assertEquals(n, Grouping.toOriginal(n, shown.length))
            for (i in 0..n) {
                val t = Grouping.toTransformed(n, i)
                assertTrue(t in 0..shown.length)
                assertEquals(i, Grouping.toOriginal(n, t), "n=$n i=$i")
            }
        }
    }
}
