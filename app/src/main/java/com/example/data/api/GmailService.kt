package com.example.data.api

import android.util.Base64
import android.util.Log
import com.example.data.model.ItineraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ParsedBooking(
    val id: String,
    val type: String, // "Flight", "Hotel", "Train", "Tour"
    val title: String,
    val destination: String,
    val provider: String,
    val bookingReference: String,
    val dates: String,
    val amount: String,
    val summary: String,
    val snippet: String
)

sealed class GmailScanResult {
    data class Success(val bookings: List<ParsedBooking>, val message: String) : GmailScanResult()
    data class Error(val message: String) : GmailScanResult()
    object NoToken : GmailScanResult()
}

class GmailService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Scans Gmail messages matching travel keywords via Gmail REST API:
     * https://gmail.googleapis.com/gmail/v1/users/me/messages?q=...
     */
    suspend fun scanTravelConfirmations(
        accessToken: String,
        userEmail: String
    ): GmailScanResult = withContext(Dispatchers.IO) {
        val cleanToken = accessToken.trim()
        if (cleanToken.isBlank()) {
            return@withContext GmailScanResult.NoToken
        }

        try {
            // Search query for confirmation emails: flight, hotel, booking, PNR, reservation
            val query = "subject:(flight OR hotel OR booking OR reservation OR PNR OR airline OR stay OR ticket)"
            val url = "https://gmail.googleapis.com/gmail/v1/users/me/messages?q=${java.net.URLEncoder.encode(query, "UTF-8")}&maxResults=10"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $cleanToken")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(bodyString).optJSONObject("error")?.optString("message")
                        ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: $bodyString"
                }
                Log.e("GmailService", "Failed to query Gmail: $errorMsg")
                return@withContext GmailScanResult.Error("Gmail API returned: $errorMsg")
            }

            val jsonResponse = JSONObject(bodyString)
            val messagesArray = jsonResponse.optJSONArray("messages") ?: JSONArray()

            if (messagesArray.length() == 0) {
                return@withContext GmailScanResult.Success(
                    bookings = emptyList(),
                    message = "No travel confirmation emails found in the scanned query."
                )
            }

            val bookings = mutableListOf<ParsedBooking>()

            // Fetch details for up to 6 recent messages
            val limit = minOf(messagesArray.length(), 6)
            for (i in 0 until limit) {
                val msgObj = messagesArray.getJSONObject(i)
                val msgId = msgObj.getString("id")

                val detailUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/$msgId?format=full"
                val detailRequest = Request.Builder()
                    .url(detailUrl)
                    .addHeader("Authorization", "Bearer $cleanToken")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()

                val detailResponse = client.newCall(detailRequest).execute()
                if (detailResponse.isSuccessful) {
                    val detailBody = detailResponse.body?.string() ?: ""
                    val parsed = parseMessageJson(detailBody)
                    if (parsed != null) {
                        bookings.add(parsed)
                    }
                }
            }

            return@withContext GmailScanResult.Success(
                bookings = bookings,
                message = "Successfully scanned ${bookings.size} travel confirmations from your Gmail."
            )

        } catch (e: Exception) {
            Log.e("GmailService", "Exception scanning Gmail", e)
            return@withContext GmailScanResult.Error(e.localizedMessage ?: "Network or parsing error")
        }
    }

    /**
     * Parses Gmail message JSON representation into a ParsedBooking model.
     */
    fun parseMessageJson(jsonStr: String): ParsedBooking? {
        try {
            val root = JSONObject(jsonStr)
            val id = root.optString("id")
            val snippet = root.optString("snippet")
            val payload = root.optJSONObject("payload") ?: return null
            val headers = payload.optJSONArray("headers") ?: JSONArray()

            var subject = ""
            var from = ""
            var date = ""

            for (i in 0 until headers.length()) {
                val h = headers.getJSONObject(i)
                when (h.optString("name").lowercase()) {
                    "subject" -> subject = h.optString("value")
                    "from" -> from = h.optString("value")
                    "date" -> date = h.optString("value")
                }
            }

            // Extract body text if available
            val bodyText = extractBodyText(payload) ?: snippet

            return parseBookingDetails(id, subject, from, date, snippet, bodyText)
        } catch (e: Exception) {
            Log.e("GmailService", "Error parsing message", e)
            return null
        }
    }

    private fun extractBodyText(payload: JSONObject): String? {
        // Direct body data
        val body = payload.optJSONObject("body")
        val directData = body?.optString("data")
        if (!directData.isNullOrBlank()) {
            return decodeBase64Safe(directData)
        }

        // Search multipart
        val parts = payload.optJSONArray("parts") ?: return null
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val mimeType = part.optString("mimeType")
            if (mimeType == "text/plain" || mimeType == "text/html") {
                val pData = part.optJSONObject("body")?.optString("data")
                if (!pData.isNullOrBlank()) {
                    return decodeBase64Safe(pData)
                }
            }
        }
        return null
    }

    private fun decodeBase64Safe(encoded: String): String {
        return try {
            val clean = encoded.replace("-", "+").replace("_", "/")
            String(Base64.decode(clean, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            encoded
        }
    }

    /**
     * Extracts destination, provider, booking reference, and budget from email metadata and content.
     */
    fun parseBookingDetails(
        id: String,
        subject: String,
        from: String,
        date: String,
        snippet: String,
        fullContent: String
    ): ParsedBooking {
        val lowerSub = subject.lowercase()
        val lowerContent = (subject + " " + fullContent).lowercase()

        // 1. Determine Type
        val type = when {
            lowerSub.contains("flight") || lowerSub.contains("airline") || lowerSub.contains("indigo") ||
                    lowerSub.contains("air india") || lowerSub.contains("vistara") || lowerSub.contains("spicejet") ||
                    lowerSub.contains("boarding pass") -> "Flight"
            lowerSub.contains("hotel") || lowerSub.contains("resort") || lowerSub.contains("stay") ||
                    lowerSub.contains("check-in") || lowerSub.contains("airbnb") || lowerSub.contains("oyo") ||
                    lowerSub.contains("booking.com") || lowerSub.contains("makemytrip") -> "Hotel"
            lowerSub.contains("train") || lowerSub.contains("irctc") || lowerSub.contains("railway") ||
                    lowerSub.contains("vande bharat") -> "Train"
            else -> "Booking"
        }

        // 2. Determine Provider
        val provider = when {
            from.contains("IndiGo", ignoreCase = true) || lowerContent.contains("indigo") -> "IndiGo"
            from.contains("Air India", ignoreCase = true) || lowerContent.contains("air india") -> "Air India"
            from.contains("Vistara", ignoreCase = true) || lowerContent.contains("vistara") -> "Vistara"
            from.contains("SpiceJet", ignoreCase = true) || lowerContent.contains("spicejet") -> "SpiceJet"
            from.contains("MakeMyTrip", ignoreCase = true) || lowerContent.contains("makemytrip") -> "MakeMyTrip"
            from.contains("Booking.com", ignoreCase = true) || lowerContent.contains("booking.com") -> "Booking.com"
            from.contains("Airbnb", ignoreCase = true) || lowerContent.contains("airbnb") -> "Airbnb"
            from.contains("Agoda", ignoreCase = true) || lowerContent.contains("agoda") -> "Agoda"
            from.contains("IRCTC", ignoreCase = true) || lowerContent.contains("irctc") -> "IRCTC"
            else -> {
                val cleanFrom = from.replace(Regex("<.*?>"), "").trim()
                if (cleanFrom.isNotBlank()) cleanFrom.take(25) else "Travel Provider"
            }
        }

        // 3. Extract Destination / Indian City
        val indianCities = listOf(
            "Jaipur", "Agra", "Varanasi", "Goa", "Delhi", "Mumbai", "Udaipur", "Kerala", "Manali",
            "Shimla", "Kolkata", "Bengaluru", "Bangalore", "Hyderabad", "Chennai", "Amritsar",
            "Jodhpur", "Rishikesh", "Darjeeling", "Mysore", "Madurai", "Kochi", "Srinagar", "Leh"
        )
        val destination = indianCities.firstOrNull { lowerContent.contains(it.lowercase()) }
            ?: extractCityFromSubject(subject)
            ?: "India Travel"

        // 4. Extract Booking Reference / PNR
        val pnrPattern = Pattern.compile("(?i)(?:pnr|booking(?:\\s+id|\\s+ref|\\s+reference)?|reservation(?:\\s+code)?)\\s*[:#-]?\\s*([A-Z0-9]{5,10})")
        val matcher = pnrPattern.matcher(fullContent)
        val bookingRef = if (matcher.find()) {
            matcher.group(1) ?: "CONF-${id.takeLast(6).uppercase()}"
        } else {
            "CONF-${id.takeLast(6).uppercase()}"
        }

        // 5. Extract Amount / Budget
        val amountPattern = Pattern.compile("(?i)(?:₹|inr|rs\\.?)\\s*([0-9,]{3,8})")
        val amtMatcher = amountPattern.matcher(fullContent)
        val amount = if (amtMatcher.find()) {
            "₹" + (amtMatcher.group(1) ?: "4,500")
        } else {
            if (type == "Hotel") "₹6,500" else if (type == "Flight") "₹5,200" else "₹3,500"
        }

        // 6. Dates
        val displayDate = if (date.length > 16) date.substring(0, 16) else (date.ifBlank { "Upcoming Trip" })

        val title = when (type) {
            "Flight" -> "$provider Flight to $destination"
            "Hotel" -> "$destination Stay ($provider)"
            "Train" -> "Train Journey to $destination"
            else -> "$destination Reservation via $provider"
        }

        val summary = "$type confirmed via $provider. Ref: $bookingRef. Estimated Cost: $amount."

        return ParsedBooking(
            id = id,
            type = type,
            title = title,
            destination = destination,
            provider = provider,
            bookingReference = bookingRef,
            dates = displayDate,
            amount = amount,
            summary = summary,
            snippet = snippet
        )
    }

    private fun extractCityFromSubject(subject: String): String? {
        val parts = subject.split(" ", "-", "to", "at", "in")
        for (part in parts) {
            val clean = part.trim().replace(Regex("[^A-Za-z]"), "")
            if (clean.length in 4..12 && clean.first().isUpperCase()) {
                return clean
            }
        }
        return null
    }

    /**
     * Converts a ParsedBooking into an ItineraryItem for the Trip Planning module.
     */
    fun convertToItinerary(booking: ParsedBooking, userEmail: String): ItineraryItem {
        val durationDays = when (booking.type) {
            "Flight" -> 4
            "Hotel" -> 3
            "Train" -> 5
            else -> 3
        }

        val planText = when (booking.type) {
            "Flight" -> """
                • Day 1: Arrive via ${booking.provider} (${booking.bookingReference}). Check-in and relax. Evening local market walk.
                • Day 2: Full-day guided sightseeing of major heritage landmarks and cultural monuments in ${booking.destination}.
                • Day 3: Culinary trail exploring authentic local food & handicrafts.
                • Day 4: Souvenir shopping, leisure morning, and transfer back to airport.
            """.trimIndent()

            "Hotel" -> """
                • Day 1: Check-in at ${booking.destination} hotel (${booking.provider} Ref: ${booking.bookingReference}). Leisure evening.
                • Day 2: Morning cultural tour, temple visit, and photography walk.
                • Day 3: Sunset viewpoint tour, check-out, and forward journey.
            """.trimIndent()

            else -> """
                • Day 1: Arrival in ${booking.destination} (${booking.bookingReference}). Check-in and acclimatization.
                • Day 2: Top attractions and historical sites exploration.
                • Day 3: Traditional arts, cuisine, and local experiences.
            """.trimIndent()
        }

        return ItineraryItem(
            title = "${booking.type}: ${booking.destination} (${booking.provider})",
            destination = booking.destination,
            durationDays = durationDays,
            dayWisePlan = planText,
            estimatedBudgetInr = booking.amount,
            bestTimeToVisit = "October to March",
            userEmail = userEmail
        )
    }

    /**
     * Curated sample bookings to test the auto-parsing module immediately
     * without needing live confirmation emails in the test inbox.
     */
    fun getSampleTravelConfirmations(): List<ParsedBooking> {
        return listOf(
            ParsedBooking(
                id = "sample_fl_101",
                type = "Flight",
                title = "IndiGo Flight 6E-204 to Jaipur",
                destination = "Jaipur",
                provider = "IndiGo Airlines",
                bookingReference = "6E-JPR789",
                dates = "Upcoming Weekend",
                amount = "₹4,890",
                summary = "Flight confirmed: Delhi (DEL) → Jaipur (JAI). Terminal 3, Seat 14F.",
                snippet = "Your IndiGo flight booking confirmation. PNR: 6E-JPR789. Passenger: Avishu Parikar. Flight 6E-204 departing on time."
            ),
            ParsedBooking(
                id = "sample_ht_202",
                type = "Hotel",
                title = "Taj Lake Palace Stay in Udaipur",
                destination = "Udaipur",
                provider = "MakeMyTrip",
                bookingReference = "MMT-UDPR452",
                dates = "Next Month (3 Nights)",
                amount = "₹18,500",
                summary = "Hotel reservation confirmed: Deluxe Lake View Suite with complimentary royal breakfast.",
                snippet = "MakeMyTrip Booking Confirmation: Taj Lake Palace Udaipur, 3 Nights reserved for Avishu Parikar. Booking ID: MMT-UDPR452."
            ),
            ParsedBooking(
                id = "sample_fl_303",
                type = "Flight",
                title = "Air India Flight AI-682 to Goa",
                destination = "Goa",
                provider = "Air India",
                bookingReference = "AI-GOA912",
                dates = "Next Friday",
                amount = "₹5,400",
                summary = "Flight confirmed: Mumbai (BOM) → Goa Dabolim (GOI). Baggage: 25kg included.",
                snippet = "Air India e-Ticket Confirmation. PNR: AI-GOA912. Route: BOM to GOI. Passenger: Avishu Parikar."
            ),
            ParsedBooking(
                id = "sample_ht_404",
                type = "Hotel",
                title = "Heritage Ganga View Hotel in Varanasi",
                destination = "Varanasi",
                provider = "Booking.com",
                bookingReference = "BK-VNS331",
                dates = "Upcoming Trip (2 Nights)",
                amount = "₹4,200",
                summary = "Hotel reservation confirmed: Ghat-facing Heritage Room near Dashashwamedh Ghat.",
                snippet = "Booking.com confirmation for Varanasi stay. Reference BK-VNS331. Check-in 12:00 PM. Near Ganga Aarti."
            )
        )
    }
}
