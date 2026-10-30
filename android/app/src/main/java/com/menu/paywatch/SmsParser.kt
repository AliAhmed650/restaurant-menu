package com.menu.paywatch

/**
 * Extracts provider, amount and transaction id from a transfer SMS.
 * Same rules as parseSms() in the website's index.html.
 *
 * IMPORTANT: the exact wording of Vodafone Cash / InstaPay / bank SMS was not
 * verified against real messages. The rules are deliberately flexible. The
 * server also matches on the raw text, so an unparsed id can still match.
 */
data class ParsedSms(
    val provider: String,        // vodafone_cash | instapay | other
    val amount: Double?,
    val txn: String?,            // digits only
    val incoming: Boolean
)

object SmsParser {
    private val I = RegexOption.IGNORE_CASE

    private val amountPatterns = listOf(
        Regex("""(\d{1,7}(?:\.\d{1,2})?)\s*(?:جنيه|ج\.?\s?م|egp|l\.?e\b)""", I),
        Regex("""(?:egp|\bl\.?e\b|جنيه)\s*:?\s*(\d{1,7}(?:\.\d{1,2})?)""", I),
        Regex("""(?:مبلغ|amount|قيمة)\D{0,14}?(\d{1,7}(?:\.\d{1,2})?)(?!\d)""", I)
    )

    private val txnKeyword = Regex(
        """(?:رقم\s*(?:ال)?(?:عملي[ةه]|معامل[ةه]|مرجع(?:ي)?)|(?:ال)?مرجع|عملي[ةه]\s*رقم|transaction\s*(?:id|no\.?|number|#)?|reference(?:\s*(?:no\.?|number|id))?|\bref(?:\.|\s*no\.?|\s*#)?|\btrx\s*(?:id|no)?|\brrn\b|\bid\b)\s*[:#\-]?\s*([A-Za-z0-9]{6,24})""",
        I
    )

    /** Arabic-Indic and Persian digits to ASCII. */
    fun ascii(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    fun digits(s: String): String = ascii(s).filter { it in '0'..'9' }

    fun parse(raw: String): ParsedSms {
        val t = ascii(raw).replace(Regex("""(\d)[,٬](?=\d{3}(?!\d))"""), "$1")
        val low = t.lowercase()

        val provider = when {
            Regex("""vodafone|فودافون|vf[\s-]?cash""").containsMatchIn(low) -> "vodafone_cash"
            Regex("""insta\s*pay|انستا|إنستا|\bipn\b""").containsMatchIn(low) -> "instapay"
            else -> "other"
        }

        val outgoing = Regex("""(تم خصم|سحب|you have sent|you sent|debited|withdraw)""", I).containsMatchIn(t)

        var amount: Double? = null
        for (p in amountPatterns) {
            val v = p.find(t)?.groupValues?.get(1)?.toDoubleOrNull()
            if (v != null && v > 0) { amount = v; break }
        }

        var txn: String? = null
        val kw = txnKeyword.find(t)?.groupValues?.get(1)
        if (kw != null && digits(kw).length >= 6) {
            txn = digits(kw)
        } else {
            txn = Regex("""\d{9,20}""").findAll(t)
                .map { it.value }
                .filter { !(it.length == 11 && it.startsWith("01")) }
                .sortedByDescending { it.length }
                .firstOrNull()
        }
        return ParsedSms(provider, amount, txn, !outgoing)
    }
}
