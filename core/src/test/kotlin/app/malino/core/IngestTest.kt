package app.malino.core

import kotlin.test.*

class IngestTest {
    private val recv = Jalali.toMillis(1405, 8, 4, 7, 10)
    private val parsianCard = Card("p1", "daily", smsKey = "12345678901234", openingBalance = 0, openingAt = 0)
    private val bluCard = Card("b1", "blu", smsKey = "blu", openingBalance = 0, openingAt = 0)
    private var n = 0
    private fun id() = "id${n++}"
    private fun sms(body: String) = RawMsg("sms", "PARSIANBANK", "", body, recv)
    private val out = "12345678901234\nمبلغ:500,000-\nمانده:69,521,224\n08/04\n07:06"

    @Test fun createsInboxTx() {
        val r = Ingest.process(sms(out), listOf(parsianCard), emptyList(), ::id) as IngestResult.Created
        val t = r.txs.single()
        assertEquals(TxType.OUT, t.type); assertEquals(500_000, t.amount); assertEquals(69_521_224, t.balanceAfter)
        assertEquals(TxStatus.INBOX, t.status); assertEquals("p1", t.cardId)
    }

    @Test fun duplicateIsDropped() {
        val t = (Ingest.process(sms(out), listOf(parsianCard), emptyList(), ::id) as IngestResult.Created).txs.single()
        assertEquals(IngestResult.Duplicate, Ingest.process(sms(out), listOf(parsianCard), listOf(t), ::id))
    }

    @Test fun otpPromoUnparsedUnmapped() {
        assertEquals(IngestResult.Ignored("otp"), Ingest.process(sms("رمز یکبار مصرف 1234"), listOf(parsianCard), emptyList(), ::id))
        assertEquals(IngestResult.Unparsed, Ingest.process(sms("سلام"), listOf(parsianCard), emptyList(), ::id))
        assertIs<IngestResult.Unmapped>(Ingest.process(sms(out), emptyList(), emptyList(), ::id))
    }

    @Test fun recognisesTransferBetweenOwnCards() {
        val blu = "بلو\nواریز پول\nعلی عزیز، 495,000ریال به حساب شما نشست.\nموجودی: 1,000,000 ریال\n7:07\n۱۴۰۵.۰۸.۰۴"
        val cards = listOf(parsianCard, bluCard)
        val first = (Ingest.process(sms(out), cards, emptyList(), ::id) as IngestResult.Created).txs.single()
        val r = Ingest.process(RawMsg("notif", "blu", "", blu, recv), cards, listOf(first), ::id) as IngestResult.Created
        assertEquals(3, r.txs.size)
        assertEquals(setOf(TxType.TRANSFER, TxType.FEE), r.txs.map { it.type }.toSet())
        assertEquals(5_000, r.txs.first { it.type == TxType.FEE }.amount)
        // balances still line up with the bank's figures: 69,521,224 after the out side
        val c = parsianCard.copy(openingBalance = 70_021_224)
        val res = Ledger.compute(listOf(c), r.txs.filter { it.cardId == "p1" })
        assertTrue(res.gaps.isEmpty(), "unexpected gap ${res.gaps}")
    }
}
