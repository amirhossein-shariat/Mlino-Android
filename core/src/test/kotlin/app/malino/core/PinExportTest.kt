package app.malino.core

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.*

class PinExportTest {
    @Test fun pinRoundTrip() {
        val salt = Pin.newSalt()
        val h = Pin.hash("1234", salt, 1000)
        assertTrue(Pin.verify("1234", salt, h, 1000)); assertFalse(Pin.verify("1235", salt, h, 1000))
        assertFalse(Pin.verify("1234", Pin.newSalt(), h, 1000), "another salt gives another hash")
        assertTrue(Pin.isValid("1234") && Pin.isValid("12345678")); assertFalse(Pin.isValid("123")); assertFalse(Pin.isValid("123456789")); assertFalse(Pin.isValid("12a4"))
    }

    @Test fun wrongPinBlocksAfterFiveTries() {
        var f = Pin.Fail(0, 0)
        repeat(4) { f = Pin.afterWrong(f.count, 1000); assertEquals(0, f.blockedUntil) }
        f = Pin.afterWrong(f.count, 1000)
        assertEquals(1000 + Pin.BLOCK_MS, f.blockedUntil); assertEquals(0, f.count)
    }

    @Test fun exportRows() {
        val open = Jalali.toMillis(1405, 7, 1)
        val cards = listOf(Card("c1", "روزمره", openingBalance = 1_000, openingAt = open))
        val txs = listOf(
            Tx("a", "c1", Jalali.toMillis(1405, 7, 2, 10, 5), TxType.OUT, 300, categoryId = "food", note = "ناهار & نوشیدنی"),
            Tx("b", "c1", Jalali.toMillis(1405, 7, 3, 9, 0), TxType.IN, 5_000, categoryId = "invoice", business = true),
            Tx("c", "c1", Jalali.toMillis(1405, 8, 3, 9, 0), TxType.OUT, 9, categoryId = "food"),
        )
        val res = Ledger.compute(cards, txs)
        val bytes = Export.transactions(cards, txs, res.after, mapOf("food" to "خوراک", "invoice" to "درآمد فاکتور"), Jalali.toMillis(1405, 7, 1), Jalali.toMillis(1405, 8, 1))
        val parts = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> generateSequence { z.nextEntry }.forEach { parts[it.name] = z.readBytes().toString(Charsets.UTF_8) } }
        val s1 = parts.getValue("xl/worksheets/sheet1.xml")
        assertTrue("1405/07/02" in s1 && "10:05" in s1 && "ناهار &amp; نوشیدنی" in s1 && "<v>-300</v>" in s1 && "<v>700</v>" in s1 && "کسب‌وکار" in s1)
        assertFalse("1405/08/03" in s1, "outside the range")
        val s2 = parts.getValue("xl/worksheets/sheet2.xml")
        assertTrue("خوراک" in s2 && "درآمد فاکتور" in s2 && "<v>5000</v>" in s2)
    }
}
