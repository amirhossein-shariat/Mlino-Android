package app.malino.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Dependency-free .xlsx writer: a zip of the few XML parts Excel needs. Cells: Number, String or null. The first row is bold; sheets are RTL. */
object Xlsx {
    class Sheet(val name: String, val rows: List<List<Any?>>, val widths: List<Int> = emptyList())

    private fun esc(s: String) = buildString {
        for (ch in s) when {
            ch == '&' -> append("&amp;"); ch == '<' -> append("&lt;"); ch == '>' -> append("&gt;"); ch == '"' -> append("&quot;")
            ch.code < 32 && ch != '\n' && ch != '\r' && ch != '\t' -> {}
            else -> append(ch)
        }
    }

    private fun col(index: Int): String {
        var i = index + 1; val sb = StringBuilder()
        while (i > 0) { sb.insert(0, ('A' + (i - 1) % 26)); i = (i - 1) / 26 }
        return sb.toString()
    }

    private fun sheetXml(s: Sheet): String {
        val cols = if (s.widths.isEmpty()) "" else "<cols>" + s.widths.mapIndexed { i, w -> """<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""" }.joinToString("") + "</cols>"
        val body = s.rows.mapIndexed { r, row ->
            "<row r=\"${r + 1}\">" + row.mapIndexed { c, v ->
                val ref = col(c) + (r + 1)
                val st = if (r == 0) " s=\"1\"" else ""
                when {
                    v == null || (v is String && v.isEmpty()) -> ""
                    v is Number -> "<c r=\"$ref\"$st><v>$v</v></c>"
                    else -> "<c r=\"$ref\"$st t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v.toString())}</t></is></c>"
                }
            }.joinToString("") + "</row>"
        }.joinToString("")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<sheetViews><sheetView workbookViewId=\"0\" rightToLeft=\"1\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>" +
            "$cols<sheetData>$body</sheetData></worksheet>"
    }

    fun build(sheets: List<Sheet>): ByteArray {
        val safe = { n: String -> n.replace(Regex("[\\[\\]:*?/\\\\]"), " ").take(31).ifBlank { "Sheet" } }
        val head = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        val files = listOf(
            "[Content_Types].xml" to head + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } + "</Types>",
            "_rels/.rels" to head + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>",
            "xl/workbook.xml" to head + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" +
                sheets.mapIndexed { i, s -> "<sheet name=\"${esc(safe(s.name))}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") + "</sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to head + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                sheets.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" } +
                "<Relationship Id=\"rId${sheets.size + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>",
            "xl/styles.xml" to head + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs></styleSheet>",
        ) + sheets.mapIndexed { i, s -> "xl/worksheets/sheet${i + 1}.xml" to sheetXml(s) }
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> for ((name, data) in files) { z.putNextEntry(ZipEntry(name)); z.write(data.toByteArray(Charsets.UTF_8)); z.closeEntry() } }
        return bos.toByteArray()
    }
}
