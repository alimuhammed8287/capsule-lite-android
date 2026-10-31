package com.example.capsule

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ForegroundColorSpan

// Navigation details from Google Maps, only when the user has turned navigation info on.
// Two sources feed it: the text on the Maps screen (read by the accessibility service, only while Maps is open)
// and Maps' own navigation notification (works when Maps is in the background).
// The patterns match the text seen in Maps: "6 min", "16 km • 12:30 am", "50 m", and the street.
object MapsNav {
    @Volatile var active = false
    @Volatile var eta = ""            // e.g. "6 min"
    @Volatile var arrival = ""        // e.g. "12:07 am"
    @Volatile var distanceLeft = ""   // e.g. "400 m"
    @Volatile var turnDistance = ""   // e.g. "50 m"
    @Volatile var street = ""         // e.g. "Channasandra Main Rd"
    @Volatile var direction = ""      // left, right, slight_left, slight_right, straight, uturn, roundabout, arrive, or "" when unknown
    @Volatile var trafficLevel = ""    // clear, moderate, heavy; only set when Maps exposes a colored ETA
    @Volatile var maneuverSeq = 0     // goes up by one when a new turn starts (never for distance ticks)
    @Volatile var rawDump = ""        // the first texts read, for the debug line
    @Volatile var lastSeen = 0L
    @Volatile var notifAlive = false  // Maps' navigation notification is on the phone right now

    // The turn arrow picture taken from the Maps notification, when it has one
    @Volatile var arrow: Bitmap? = null
    @Volatile var arrowShape = false  // a shape on a clear background, so it can be drawn in white
    @Volatile var arrowVersion = 0

    private var accessAt = 0L
    private var lastTurnM = -1
    private var lastSource = ""

    private val spaceRe = Regex("""[\u00A0\u2007\u2009\u202F]""")
    private val sepRe = Regex("""\s*[•·|\n]\s*""")
    private val etaRe = Regex("""\d+\s?(hr|hrs|h|min|mins|minute|minutes)(\s\d+\s?min)?(\s+(left|remaining))?""", RegexOption.IGNORE_CASE)
    private val leftRe = Regex("""(\d+(\.\d+)?\s?(km|m|mi))\s*[•·]\s*(\d{1,2}:\d{2}\s?[ap]m)""", RegexOption.IGNORE_CASE)
    private val turnRe = Regex("""\d+(\.\d+)?\s?(km|m|mi|ft|yd)""", RegexOption.IGNORE_CASE)
    private val clockRe = Regex("""\d{1,2}:\d{2}\s?[ap]m""", RegexOption.IGNORE_CASE)
    private val durationInTextRe = Regex("""\b\d+\s*(?:hr|hrs|hour|hours|h|min|mins|minute|minutes)\b""", RegexOption.IGNORE_CASE)
    private val onToRe = Regex("""^.*?\b(onto|on|towards|toward|to)\s+""", RegexOption.IGNORE_CASE)
    private val metersRe = Regex("""(\d+(?:[.,]\d+)?)\s?(km|mi|yd|ft|m)\b""", RegexOption.IGNORE_CASE)
    private val ignore = setOf("Re-centre", "Report", "Search", "Start", "Done", "Overview")

    // Words that mark a text as a driving instruction, so a place called "Left Bank Cafe" is not read as a turn
    private val keywordRe = Regex("""\b(turn|keep|bear|slight|sharp|fork|ramp|merge|exit|continue|straight|head|u-turn|u turn|roundabout|rotary|arrived|destination)\b""", RegexOption.IGNORE_CASE)
    private val uturnRe = Regex("""u-?\s?turn""", RegexOption.IGNORE_CASE)
    private val roundRe = Regex("""roundabout|rotary|traffic circle""", RegexOption.IGNORE_CASE)
    private val arriveRe = Regex("""destination|you have arrived|you've arrived|arrived at""", RegexOption.IGNORE_CASE)
    private val leftWordRe = Regex("""\bleft\b""", RegexOption.IGNORE_CASE)
    private val rightWordRe = Regex("""\bright\b""", RegexOption.IGNORE_CASE)
    private val slightRe = Regex("""slight|bear|keep|fork|veer""", RegexOption.IGNORE_CASE)
    private val straightRe = Regex("""straight|continue (on|onto|along)|\bhead\b""", RegexOption.IGNORE_CASE)

    private fun norm(s: String) = s.replace(spaceRe, " ").trim()

    fun isDurationText(text: CharSequence): Boolean = durationInTextRe.containsMatchIn(norm(text.toString()))

    /** Read Maps' existing ETA tint when its notification exposes it; neutral text stays unclassified. */
    fun trafficLevelFromColor(color: Int): String? {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]
        if (saturation < 0.30f || value < 0.28f) return null
        return when {
            hue in 76f..170f -> "clear"
            hue in 18f..75f -> "moderate"
            hue <= 17f || hue >= 345f -> "heavy"
            else -> null
        }
    }

    fun trafficLevelFromSpans(text: CharSequence): String? {
        if (!isDurationText(text) || text !is Spanned) return null
        val match = durationInTextRe.find(text) ?: return null
        val spans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
        return spans.firstNotNullOfOrNull { span ->
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (start < match.range.last + 1 && end > match.range.first) trafficLevelFromColor(span.foregroundColor) else null
        }
    }

    fun trafficTint(): Int? = when (trafficLevel) {
        "clear" -> 0xFF30D158.toInt()
        "moderate" -> 0xFFFFB547.toInt()
        "heavy" -> 0xFFFF5A52.toInt()
        else -> null
    }

    // "280 m" -> 280, "1.2 km" -> 1200, "0.5 mi" -> 804. -1 when there is no distance.
    fun metersOf(s: String): Int {
        val m = metersRe.find(norm(s)) ?: return -1
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return -1
        return when (m.groupValues[2].lowercase()) {
            "km" -> v * 1000.0
            "mi" -> v * 1609.34
            "yd" -> v * 0.9144
            "ft" -> v * 0.3048
            else -> v
        }.toInt()
    }

    // A new turn starts when the distance to the next turn jumps back up (the countdown starts over).
    // It never depends on street names or text layout, and a falling distance never counts.
    private fun noteTurn(m: Int, source: String) {
        if (source != lastSource) {
            lastSource = source
            lastTurnM = -1
        }
        if (m < 0) return
        if (lastTurnM >= 0 && m > lastTurnM + 40) maneuverSeq++
        lastTurnM = m
    }

    // Reads the direction of the next maneuver from instruction-like texts. Returns "" when none is found.
    fun parseDirection(items: List<String>): String {
        for (raw in items) {
            val y = norm(raw)
            if (y.isEmpty() || y.length > 160) continue
            if (!keywordRe.containsMatchIn(y)) continue
            val hasLeft = leftWordRe.containsMatchIn(y)
            val hasRight = rightWordRe.containsMatchIn(y)
            val dir = when {
                uturnRe.containsMatchIn(y) -> "uturn"
                roundRe.containsMatchIn(y) -> "roundabout"
                arriveRe.containsMatchIn(y) -> "arrive"
                hasLeft && !hasRight -> if (slightRe.containsMatchIn(y)) "slight_left" else "left"
                hasRight && !hasLeft -> if (slightRe.containsMatchIn(y)) "slight_right" else "right"
                straightRe.containsMatchIn(y) -> "straight"
                else -> ""
            }
            if (dir.isNotEmpty()) return dir
        }
        return ""
    }

    fun noteDirection(items: List<String>) {
        val d = parseDirection(items)
        if (d.isNotEmpty()) direction = d
    }

    // True when a picture has nothing visible in it
    fun isBlank(b: Bitmap): Boolean {
        val step = maxOf(1, b.width / 12)
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                if ((b.getPixel(x, y) ushr 24) >= 24) return false
                x += step
            }
            y += step
        }
        return true
    }

    private fun looksLikeShape(b: Bitmap): Boolean {
        var clear = 0
        var total = 0
        val step = maxOf(1, b.width / 12)
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                total++
                if ((b.getPixel(x, y) ushr 24) < 24) clear++
                x += step
            }
            y += step
        }
        return total > 0 && clear * 100 / total >= 15
    }

    private fun storeArrow(b: Bitmap) {
        val old = arrow
        if (old != null && old.width == b.width && old.height == b.height && old.sameAs(b)) return
        arrow = b
        arrowShape = looksLikeShape(b)
        arrowVersion++
    }

    // From the text on the Maps screen
    fun update(texts: List<String>) {
        accessAt = SystemClock.uptimeMillis()
        parse(texts.map { norm(it) }.filter { it.isNotEmpty() }, "screen")
    }

    // From Maps' navigation notification. The screen text, when it is fresh, is more up to date, so it wins.
    fun updateFromNotification(texts: List<String>, arrowBmp: Bitmap?, observedTrafficLevel: String? = null) {
        notifAlive = true
        if (arrowBmp != null) storeArrow(arrowBmp)
        if (observedTrafficLevel != null) trafficLevel = observedTrafficLevel
        val items = texts.flatMap { listOf(it) + it.split(sepRe) }.map { norm(it) }.filter { it.isNotEmpty() }.distinct()
        if (SystemClock.uptimeMillis() - accessAt < 3000) {
            noteDirection(items)
            // Maps only posts this ongoing/category-navigation notification while a route is
            // active. Keep navigation eligible for priority selection even when its current
            // custom notification exposes no readable ETA or distance text.
            active = true
            lastSeen = SystemClock.uptimeMillis()
            return
        }
        parse(items, "notification")
        // The notification lifecycle is a stronger signal of active navigation than whether
        // this Maps version exposed parsable text in its custom RemoteViews.
        active = true
        lastSeen = SystemClock.uptimeMillis()
    }

    private fun parse(items: List<String>, source: String) {
        var e = ""
        var a = ""
        var d = ""
        var t = ""
        var turnIndex = -1
        val parsedDistances = mutableListOf<String>()
        for ((i, y) in items.withIndex()) {
            val lm = leftRe.matchEntire(y)
            if (lm != null && d.isEmpty()) {
                d = lm.groupValues[1]
                a = lm.groupValues[4]
                continue
            }
            if (e.isEmpty() && etaRe.matchEntire(y) != null) {
                e = y
                continue
            }
            if (a.isEmpty() && clockRe.matchEntire(y) != null) {
                a = y
                continue
            }
            if (turnRe.matchEntire(y) != null) {
                parsedDistances.add(y)
                if (t.isEmpty()) {
                    t = y
                    turnIndex = i
                } else if (d.isEmpty()) {
                    // Maps may expose trip distance as a standalone value rather than "distance • arrival".
                    d = y
                }
                continue
            }
        }
        if (d.isEmpty() && parsedDistances.size > 1) d = parsedDistances[1]
        // The street is the first readable text after the turn distance
        var s = ""
        if (turnIndex >= 0) {
            for (j in turnIndex + 1 until items.size) {
                val y = items[j]
                val looksLikeText = y.length > 3 && y.any { it.isLetter() } && y !in ignore
                if (looksLikeText && leftRe.matchEntire(y) == null && etaRe.matchEntire(y) == null &&
                    turnRe.matchEntire(y) == null && clockRe.matchEntire(y) == null
                ) {
                    s = y
                    break
                }
            }
        }

        // From the notification, the instruction can come before the distance
        if (s.isEmpty() && source == "notification") {
            s = items.firstOrNull { y ->
                y.length > 3 && y.any { it.isLetter() } && y !in ignore &&
                    !y.contains('•') && !y.contains('·') && !y.contains('|') &&
                    leftRe.matchEntire(y) == null && etaRe.matchEntire(y) == null &&
                    turnRe.matchEntire(y) == null && clockRe.matchEntire(y) == null
            } ?: ""
        }
        // "Turn right onto NH 75" becomes "NH 75": the arrow already says which way
        if (s.isNotEmpty() && keywordRe.containsMatchIn(s)) {
            val rest = s.replaceFirst(onToRe, "").trim()
            if (rest.isNotEmpty() && rest != s) s = rest
        }

        // The Maps screen shows no navigation (a search, for example) but the notification does: keep what we have
        if (source == "screen" && e.isEmpty() && d.isEmpty() && t.isEmpty() && notifAlive) return

        val dir = parseDirection(items)
        if (dir.isNotEmpty()) direction = dir else if (s.isNotEmpty() && s != street) direction = ""
        if (t.isNotEmpty()) noteTurn(metersOf(t), source)

        eta = e
        arrival = a
        distanceLeft = d
        turnDistance = t
        street = s
        rawDump = "[$source] " + items.take(40).joinToString(" | ")
        // Distances alone also show up in Maps' search lists, so the screen needs the ETA or the trip distance.
        // The notification only exists during navigation, so a turn distance is enough there.
        active = e.isNotEmpty() || d.isNotEmpty() || (source == "notification" && t.isNotEmpty())
        lastSeen = SystemClock.uptimeMillis()
    }
}
