package com.classreel.player.captions

/**
 * Turns Devanagari Hindi text into Roman letters ("Hinglish"), e.g. "यह बहुत आसान है" -> "yeh bahut aasan hai".
 * It is a rule-based transliterator with a small dictionary for very common words.
 */
object Romanizer {

    private val dict = mapOf(
        "है" to "hai", "हैं" to "hain", "था" to "tha", "थी" to "thi", "थे" to "the", "हूँ" to "hoon", "हो" to "ho",
        "में" to "mein", "मैं" to "main", "नहीं" to "nahi", "और" to "aur", "यह" to "yeh", "ये" to "ye",
        "वह" to "woh", "वो" to "wo", "को" to "ko", "से" to "se", "का" to "ka", "की" to "ki", "के" to "ke",
        "तो" to "to", "भी" to "bhi", "कि" to "ki", "क्या" to "kya", "कौन" to "kaun", "कैसे" to "kaise",
        "कब" to "kab", "क्यों" to "kyun", "कहाँ" to "kahan", "यहाँ" to "yahan", "वहाँ" to "wahan",
        "जो" to "jo", "जब" to "jab", "तब" to "tab", "अब" to "ab", "एक" to "ek", "दो" to "do",
        "तीन" to "teen", "चार" to "chaar", "पाँच" to "paanch", "पर" to "par", "पे" to "pe", "ही" to "hi",
        "हम" to "hum", "आप" to "aap", "तुम" to "tum", "मेरा" to "mera", "मेरी" to "meri", "आपका" to "aapka",
        "लिए" to "liye", "साथ" to "saath", "बहुत" to "bahut", "बात" to "baat", "ठीक" to "theek",
        "अच्छा" to "accha", "सही" to "sahi", "गलत" to "galat", "चलो" to "chalo", "देखो" to "dekho",
        "देखिए" to "dekhiye", "समझो" to "samjho", "समझ" to "samajh", "कुछ" to "kuch", "सब" to "sab",
        "फिर" to "phir", "इस" to "is", "उस" to "us", "इसे" to "ise", "उसे" to "use", "अगर" to "agar",
        "तो" to "to", "लेकिन" to "lekin", "या" to "ya", "हाँ" to "haan", "जी" to "ji", "सर" to "sir",
        "आज" to "aaj", "कल" to "kal", "अभी" to "abhi", "सवाल" to "sawaal", "जवाब" to "jawab",
        "पहले" to "pehle", "बाद" to "baad", "ऊपर" to "upar", "नीचे" to "neeche", "करना" to "karna",
        "करते" to "karte", "करो" to "karo", "कर" to "kar", "करें" to "karein", "होता" to "hota",
        "होती" to "hoti", "होते" to "hote", "होगा" to "hoga", "होगी" to "hogi", "रहा" to "raha",
        "रही" to "rahi", "रहे" to "rahe", "गया" to "gaya", "गई" to "gayi", "गए" to "gaye",
        "लगा" to "laga", "दिया" to "diya", "लिया" to "liya", "बोला" to "bola", "कहा" to "kaha",
        "वाला" to "wala", "वाली" to "wali", "वाले" to "wale", "जैसे" to "jaise", "ऐसे" to "aise",
        "वैसे" to "waise", "इसलिए" to "isliye", "क्योंकि" to "kyunki", "मतलब" to "matlab"
    )

    private val cons = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        'ळ' to "l",
        'क़' to "q", 'ख़' to "kh", 'ग़' to "g", 'ज़' to "z",
        'ड़' to "r", 'ढ़' to "rh", 'फ़' to "f", 'य़' to "y"
    )

    private val nukta = mapOf(
        'क' to "q", 'ख' to "kh", 'ग' to "g", 'ज' to "z", 'ड' to "r", 'ढ' to "rh", 'फ' to "f"
    )

    private val indep = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o"
    )

    private val matra = mapOf(
        'ा' to "a", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e"
    )

    private const val VIRAMA = '्'
    private const val NUKTA = '़'

    private class U(val cons: String, val vowel: String?, val halant: Boolean) {
        var hasV = false
    }

    fun toRoman(text: String): String =
        text.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { word(it) }

    private fun isDevanagari(c: Char) = c in 'ऀ'..'ॿ'

    private fun word(raw: String): String {
        dict[raw]?.let { return it }
        if (raw.none { isDevanagari(it) }) return raw
        val w = raw.replace("ज्ञ", "ग्य")

        val units = mutableListOf<U>()
        var i = 0
        while (i < w.length) {
            val c = w[i]
            when {
                cons.containsKey(c) -> {
                    var base = cons.getValue(c)
                    i++
                    if (i < w.length && w[i] == NUKTA) {
                        base = nukta[c] ?: base
                        i++
                    }
                    if (i < w.length && w[i] == VIRAMA) {
                        units += U(base, null, true)
                        i++
                    } else if (i < w.length && matra.containsKey(w[i])) {
                        units += U(base, matra.getValue(w[i]), false)
                        i++
                    } else {
                        units += U(base, null, false)
                    }
                }
                indep.containsKey(c) -> {
                    units += U("", indep.getValue(c), false)
                    i++
                }
                c == 'ं' || c == 'ँ' -> {
                    units += U("n", null, true); i++
                }
                c == 'ः' -> {
                    units += U("h", null, true); i++
                }
                c == NUKTA || c == VIRAMA -> i++
                c in '०'..'९' -> {
                    units += U((c - '०').toString(), null, true); i++
                }
                c == '।' -> {
                    i++
                }
                else -> {
                    units += U(c.toString(), null, true); i++
                }
            }
        }

        // Decide which inherent "a" sounds are really pronounced (schwa deletion)
        val n = units.size
        for (idx in 0 until n) {
            val u = units[idx]
            if (u.vowel != null) {
                u.hasV = true
                continue
            }
            if (u.halant) {
                u.hasV = false
                continue
            }
            u.hasV = when {
                idx == n - 1 -> n == 1
                idx == 0 -> true
                else -> {
                    val prevHas = units[idx - 1].hasV
                    val next = units[idx + 1]
                    val nextHas = next.vowel != null || (!next.halant && idx + 1 != n - 1)
                    !(prevHas && nextHas)
                }
            }
        }

        val sb = StringBuilder()
        for (u in units) {
            sb.append(u.cons)
            when {
                u.vowel != null -> sb.append(u.vowel)
                !u.halant && u.hasV -> sb.append('a')
            }
        }
        return sb.toString()
    }
}
