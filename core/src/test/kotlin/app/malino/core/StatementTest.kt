package app.malino.core

import kotlin.test.*

class StatementTest {
    @Test fun parsesCsvFromAnAssistant() {
        val text = """
            ```csv
            date,time,type,amount,balance,description
            1405/07/29,13:17,out,1500000,98500000,"خرید, فروشگاه نمونه"
            ۱۴۰۵/۰۷/۲۹,۱۸:۲۵,واریز,۲۰۰۰۰۰۰,100500000,حقوق
            2026-10-21,,in,50000,,بدون ساعت و مانده
            ```
        """.trimIndent()
        val r = Statement.parse(text)
        assertEquals(emptyList(), r.errors)
        assertEquals(3, r.rows.size)
        assertEquals(Statement.Row(Jalali.toMillis(1405, 7, 29, 13, 17), true, 1_500_000, 98_500_000, "خرید, فروشگاه نمونه"), r.rows[0])
        assertEquals(false, r.rows[1].out); assertEquals(2_000_000L, r.rows[1].amount)
        assertNull(r.rows[2].balance); assertEquals(Jalali.toMillis(1405, 7, 29, 12, 0), r.rows[2].at)
    }

    @Test fun reportsBadLinesButKeepsGoodOnes() {
        val r = Statement.parse("date,time,type,amount,balance,description\n1405/13/01,10:00,out,100,,x\n1405/07/01,10:00,maybe,100,,x\n1405/07/01,10:00,out,0,,x\n1405/07/01,10:00,out,100,,ok")
        assertEquals(1, r.rows.size); assertEquals(3, r.errors.size)
    }
}
