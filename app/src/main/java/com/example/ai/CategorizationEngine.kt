package com.example.ai

import com.example.data.model.AccountRuleEntity
import com.example.data.repository.FinanceRepository

data class CategorizationResult(
    val category: String,
    val source: CategorizationSource,
    val matchedRule: AccountRuleEntity? = null
)

enum class CategorizationSource {
    RECEIVER_RULE,
    ACCOUNT_RULE,
    USER_LEARNED,
    RECEIVER_DICTIONARY,
    AI_HEURISTIC,
    GEMINI_API,
    DEFAULT
}

class CategorizationEngine(private val repository: FinanceRepository) {

    // Exact receiver dictionary mapping matching the prompt requirements
    private val receiverCategoryDictionary = mapOf(
        // Food
        "zomato" to "Food",
        "swiggy" to "Food",
        "cafe" to "Food",
        "restaurant" to "Food",
        "hotel" to "Food",
        "tea shop" to "Food",
        "canteen" to "Food",
        "campus canteen" to "Food",
        "tuck shop" to "Food",
        "mess" to "Food",
        "dhaba" to "Food",
        "sharma dhaba" to "Food",
        "chai" to "Food",
        "chai point" to "Food",
        "tea" to "Food",
        "coffee" to "Food",
        "nescafe" to "Food",
        "bakery" to "Food",
        "starbucks" to "Food",
        "dominos" to "Food",
        "pizza" to "Food",
        "burger king" to "Food",
        "mcdonald" to "Food",
        "kfc" to "Food",
        "subway" to "Food",
        "biryani" to "Food",
        "rolls" to "Food",
        "roll corner" to "Food",
        "juice" to "Food",
        "fruit juice" to "Food",
        "snacks" to "Food",
        "eats" to "Food",
        "blinkit" to "Food",
        "instamart" to "Food",
        "zepto" to "Food",
        "blue tokai" to "Food",
        "third wave" to "Food",
        "chai sutta" to "Food",

        // Subscriptions
        "netflix" to "Subscriptions",
        "spotify" to "Subscriptions",
        "prime video" to "Subscriptions",
        "amazon prime" to "Subscriptions",
        "hotstar" to "Subscriptions",
        "youtube premium" to "Subscriptions",
        "cloudbox" to "Subscriptions",
        "icloud" to "Subscriptions",
        "google one" to "Subscriptions",
        "gymfit" to "Subscriptions",
        "chatgpt" to "Subscriptions",
        "sony liv" to "Subscriptions",
        "sonyliv" to "Subscriptions",

        // Entertainment
        "bookmyshow" to "Entertainment",
        "pvr" to "Entertainment",
        "inox" to "Entertainment",
        "youtube" to "Entertainment",

        // Transportation
        "uber" to "Transportation",
        "ola" to "Transportation",
        "metro" to "Transportation",
        "bus" to "Transportation",
        "rapido" to "Transportation",
        "irctc" to "Transportation",
        "railway" to "Transportation",
        "redbus" to "Transportation",
        "petrol" to "Transportation",
        "fuel" to "Transportation",
        "auto" to "Transportation",

        // Shopping
        "amazon" to "Shopping",
        "flipkart" to "Shopping",
        "myntra" to "Shopping",
        "meesho" to "Shopping",
        "ajio" to "Shopping",
        "zara" to "Shopping",
        "h&m" to "Shopping",
        "nykaa" to "Shopping",
        "decathlon" to "Shopping",
        "croma" to "Shopping",

        // Investments
        "groww" to "Investments",
        "zerodha" to "Investments",
        "sip" to "Investments",
        "mutual fund" to "Investments",
        "angelone" to "Investments",
        "upstox" to "Investments",
        "coin" to "Investments",
        "kuvera" to "Investments",

        // Education
        "college" to "Education",
        "exam fees" to "Education",
        "exam" to "Education",
        "books" to "Education",
        "university" to "Education",
        "tuition" to "Education",
        "coursera" to "Education",
        "udemy" to "Education",
        "xerox" to "Education",
        "stationery" to "Education",
        "library" to "Education",

        // People (Peer-to-peer transfers, friends, roommates, family, contacts)
        "people" to "People",
        "person" to "People",
        "friend" to "People",
        "friends" to "People",
        "roommate" to "People",
        "room rent split" to "People",
        "split" to "People",
        "hostel mate" to "People",
        "buddy" to "People",
        "peer" to "People",
        "brother" to "People",
        "sister" to "People",
        "cousin" to "People",
        "sent to" to "People",
        "transfer to" to "People",
        "lend" to "People",
        "lending" to "People",
        "borrow" to "People",
        "borrowed" to "People",
        "contribution" to "People",
        "rahul" to "People",
        "priya" to "People",
        "amit" to "People",
        "rohit" to "People",
        "sneha" to "People",
        "vikram" to "People",
        "anjali" to "People",
        "arjun" to "People",
        "neha" to "People",
        "karan" to "People",
        "pooja" to "People",
        "varun" to "People",
        "aditya" to "People",
        "simran" to "People",
        "rajesh" to "People",
        "suresh" to "People"
    )

    suspend fun categorize(
        receiver: String,
        account: String = "",
        upiId: String = "",
        type: String = "DEBIT",
        activeRules: List<AccountRuleEntity> = emptyList()
    ): CategorizationResult {
        val cleanReceiver = receiver.trim()

        // Income / Credit handling
        if (type.equals("CREDIT", ignoreCase = true)) {
            val lowerReceiver = cleanReceiver.lowercase()
            return if (lowerReceiver.contains("papa") || lowerReceiver.contains("mom") ||
                lowerReceiver.contains("parent") || lowerReceiver.contains("allowance") ||
                lowerReceiver.contains("pocket") || lowerReceiver.contains("salary") || lowerReceiver.contains("stipend")
            ) {
                CategorizationResult("Income", CategorizationSource.AI_HEURISTIC)
            } else {
                CategorizationResult("Income", CategorizationSource.DEFAULT)
            }
        }

        // 1. Check Permanent Receiver Rules first
        // e.g. "Rahul Kumar" -> "Food", "ABC Stores" -> "Shopping", "Groww" -> "Investments"
        for (rule in activeRules) {
            if (!rule.isEnabled) continue
            val target = rule.accountIdentifier.trim()

            // Check if rule matches the receiver name
            if (cleanReceiver.isNotBlank() && target.isNotBlank()) {
                if (cleanReceiver.equals(target, ignoreCase = true) ||
                    cleanReceiver.contains(target, ignoreCase = true) ||
                    target.contains(cleanReceiver, ignoreCase = true)
                ) {
                    return CategorizationResult(
                        category = rule.categoryName,
                        source = CategorizationSource.RECEIVER_RULE,
                        matchedRule = rule
                    )
                }
            }
        }

        // 2. Check Permanent Account Rules (e.g. account "X1234" -> "Food")
        val cleanAcct = account.replace(Regex("[^0-9a-zA-Z]"), "").uppercase()
        val cleanUpi = upiId.trim().lowercase()

        for (rule in activeRules) {
            if (!rule.isEnabled) continue
            val target = rule.accountIdentifier.trim()
            val ruleDigits = target.filter { it.isDigit() }
            val acctDigits = cleanAcct.filter { it.isDigit() }

            val matchesDigits = ruleDigits.isNotEmpty() && acctDigits.isNotEmpty() &&
                    (acctDigits.endsWith(ruleDigits) || ruleDigits.endsWith(acctDigits))

            val matchesUpi = cleanUpi.isNotEmpty() &&
                    (cleanUpi.equals(target, ignoreCase = true) || cleanUpi.contains(target, ignoreCase = true))

            if (matchesDigits || matchesUpi || (cleanAcct.isNotEmpty() && cleanAcct.equals(target, ignoreCase = true))) {
                return CategorizationResult(
                    category = rule.categoryName,
                    source = CategorizationSource.ACCOUNT_RULE,
                    matchedRule = rule
                )
            }
        }

        // 3. User Learned Corrections (historical user adjustments)
        val learned = repository.getLearnedCategory(cleanReceiver)
        if (!learned.isNullOrBlank()) {
            return CategorizationResult(learned, CategorizationSource.USER_LEARNED)
        }

        // 4. Receiver-Based Categorization Dictionary
        val lowerReceiver = cleanReceiver.lowercase()
        for ((keyword, cat) in receiverCategoryDictionary) {
            if (lowerReceiver.contains(keyword)) {
                return CategorizationResult(cat, CategorizationSource.RECEIVER_DICTIONARY)
            }
        }

        // 5. Intelligent detection for Person / Peer-to-Peer UPI transfers
        val words = cleanReceiver.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val nonPersonKeywords = setOf("ltd", "pvt", "limited", "store", "shop", "canteen", "services", "mart", "bazaar", "corp", "agency", "solutions", "bank", "pay", "point", "centre", "center", "hub", "station")
        val hasNonPersonWord = words.any { it.lowercase() in nonPersonKeywords }
        if (words.size in 1..3 && !hasNonPersonWord && words.all { it.firstOrNull()?.isLetter() == true } && cleanReceiver.length >= 3) {
            return CategorizationResult("People", CategorizationSource.AI_HEURISTIC)
        }

        // 6. Default fallback to "Miscellaneous" if no category is found
        return CategorizationResult("Miscellaneous", CategorizationSource.DEFAULT)
    }
}
