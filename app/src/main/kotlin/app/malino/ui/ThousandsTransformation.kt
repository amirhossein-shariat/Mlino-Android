package app.malino.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import app.malino.core.Grouping
import app.malino.core.Jalali

/** Shows typed digits grouped by thousands in Persian digits (۱٬۰۶۹٬۷۰۳) while the stored text stays plain digits. */
object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text
        val n = d.length
        return TransformedText(AnnotatedString(Jalali.toFa(Grouping.group(d, '٬'))), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = Grouping.toTransformed(n, offset)
            override fun transformedToOriginal(offset: Int) = Grouping.toOriginal(n, offset)
        })
    }
}
