package app.malino.core

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.*

class XlsxTest {
    @Test fun producesAReadableWorkbook() {
        val bytes = Xlsx.build(listOf(
            Xlsx.Sheet("تراکنش‌ها", listOf(listOf("تاریخ", "مبلغ"), listOf("1405/07/02", 400_000L), listOf("a & <b> \"q\"", null)), listOf(14, 12)),
            Xlsx.Sheet("خلاصه", listOf(listOf("x"), listOf(1.5))),
        ))
        val parts = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> generateSequence { z.nextEntry }.forEach { parts[it.name] = z.readBytes().toString(Charsets.UTF_8) } }
        assertEquals(7, parts.size)
        assertTrue(parts.containsKey("xl/worksheets/sheet2.xml") && parts.containsKey("[Content_Types].xml"))
        val s1 = parts.getValue("xl/worksheets/sheet1.xml")
        assertTrue("<v>400000</v>" in s1 && "a &amp; &lt;b&gt; &quot;q&quot;" in s1 && "rightToLeft=\"1\"" in s1)
        assertTrue("<v>1.5</v>" in parts.getValue("xl/worksheets/sheet2.xml"))
        assertTrue("تراکنش‌ها" in parts.getValue("xl/workbook.xml"))
    }
}
