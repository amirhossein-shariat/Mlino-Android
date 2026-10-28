package app.malino.core

import kotlin.test.*

/**
 * Data-driven bank pack: every sample in test/resources/bank-samples.txt must keep parsing to the same result.
 * New samples (anonymised) are appended to that file and fixed in the parsers if they fail.
 */
class SamplesTest {
    private class Sample(val name: String, val expect: Map<String, String>, val text: String)

    private fun load(): List<Sample> {
        val raw = javaClass.getResourceAsStream("/bank-samples.txt")!!.readBytes().toString(Charsets.UTF_8)
        val out = ArrayList<Sample>()
        var head: List<String>? = null
        val body = StringBuilder()
        fun flush() {
            val h = head ?: return
            val kv = h.drop(1).associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            out.add(Sample(h[0].trim(), kv, body.toString().trimEnd('\n')))
            body.clear()
        }
        for (line in raw.lines()) {
            if (line.startsWith("###")) { flush(); head = line.removePrefix("###").split('|') }
            else if (!line.startsWith("#") && head != null) body.append(line).append('\n')
        }
        flush()
        return out
    }

    @Test fun everySampleParsesAsExpected() {
        val recv = Jalali.toMillis(1405, 8, 6, 13, 35) + 20_000
        val samples = load()
        assertTrue(samples.size >= 7, "samples loaded: ${samples.size}")
        for (s in samples) {
            val p = Parsers.parse(s.text, recv)
            assertNotNull(p, "${s.name}: not parsed")
            assertEquals(s.expect["bank"], p.bank, "${s.name}: bank")
            s.expect["key"]?.let { assertEquals(it, p.key, "${s.name}: key") }
            assertEquals(s.expect["out"] == "true", p.out, "${s.name}: direction")
            assertEquals(s.expect["amount"]!!.toLong(), p.amount, "${s.name}: amount")
            assertEquals(s.expect["balance"]!!.toLong(), p.balance, "${s.name}: balance")
            assertEquals(s.expect["extra"]?.toLong() ?: 0L, p.extraOut, "${s.name}: extra")
        }
    }
}
