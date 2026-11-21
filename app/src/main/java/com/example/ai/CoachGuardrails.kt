package com.example.ai

import java.util.Locale

data class GuardrailCheckResult(
    val isRestrictedSecurityRequest: Boolean,
    val educationalResponse: String? = null
)

/**
 * Clear, transparent guardrails ensuring the AI Coach educates and models scenarios,
 * and NEVER recommends specific stocks, ETFs, mutual funds, crypto tokens, or buy/sell timing.
 */
object CoachGuardrails {

    const val GUARDRAIL_SYSTEM_PROMPT = """
STRICT EDUCATIONAL GUARDRAILS (NON-NEGOTIABLE):
1. You are an educational personal-finance coach and scenario modeler — NOT a registered investment advisor (RIA/SEBI/SEC advisor).
2. NEVER recommend, pick, rank, or tell the user to buy, sell, hold, or short any specific security, stock, ticker symbol, mutual fund scheme, ETF, bond, option, or cryptocurrency (e.g., never name specific tickers or funds to invest in).
3. If the user asks which stock, fund, ETF, or crypto to buy or whether a specific security is a good investment, clearly and politely explain this boundary, teach the underlying general financial principle (e.g., diversification, expense ratios, risk tolerance, time horizon, building an emergency fund first), and offer to model a hypothetical savings or budget scenario using their real numbers.
4. Always present scenario math with clear assumptions and remind users that hypothetical return modeling is educational, not a guarantee of returns.
"""

    private val BUY_PICK_INTENT_PATTERNS = listOf(
        Regex("""\b(which|what|best|top|good|recommend|suggest|pick)\b.*\b(stock|stocks|share|shares|etf|etfs|mutual\s*fund|mutual\s*funds|crypto|bitcoin|ethereum|coin|coins|security|securities|bond|bonds|scheme|fund|ticker|option|calls|puts)\b"""),
        Regex("""\bshould\s+i\s+(buy|sell|invest\s+in|hold|short|trade)\b"""),
        Regex("""\b(buy|sell|invest\s+in)\s+(nvda|nvidia|aapl|apple|tsla|tesla|msft|google|googl|amzn|meta|tcs|infosys|infy|reliance|hdfc|icici|sbi|zomato|bitcoin|btc|eth|solana|doge|nifty|sensex|spy|voo|qqq|vti)\b"""),
        Regex("""\b(stock\s+pick|stock\s+tip|multibagger|guaranteed\s+return|price\s+target|market\s+timing|beat\s+the\s+market)\b""")
    )

    private val SPECIFIC_SECURITY_POST_FILTER = Regex(
        """(?i)\b(you should buy|i recommend buying|invest directly in|put your money into)\s+[A-Z0-9][A-Za-z0-9\s.&-]{1,30}\b"""
    )

    fun evaluateUserPrompt(question: String, monthlySurplus: Double): GuardrailCheckResult {
        val q = question.lowercase(Locale.ENGLISH).trim()
        val matched = BUY_PICK_INTENT_PATTERNS.any { it.containsMatchIn(q) }
        if (!matched) {
            return GuardrailCheckResult(isRestrictedSecurityRequest = false)
        }

        val surplusStr = "₹${monthlySurplus.coerceAtLeast(0.0).toLong()}"
        val response = buildString {
            append("🛡️ **Educational Guardrail Notice**\n")
            append("I'm your budgeting coach and scenario modeler — I can't recommend specific stocks, mutual funds, ETFs, crypto assets, or tell you what to buy or sell.\n\n")
            append("**What I CAN help you with right now:**\n")
            append("• **Core concepts explained:** How diversification, asset allocation, expense ratios, emergency fund priority, and time horizons work in general.\n")
            append("• **Cash-flow readiness check:** Before investing, most personal finance frameworks recommend holding 3–6 months of essential expenses in a liquid emergency fund.\n")
            append("• **Scenario modeling with your numbers:** Based on your current estimated surplus (~**$surplusStr/mo**), I can model how changing dining out, rent, or subscriptions shifts your goal target dates.\n\n")
            append("Try asking: *\"What if I cut dining out by 30%?\"*, *\"How does an emergency fund work?\"*, or *\"Explain diversification vs concentration risk.\"*")
        }
        return GuardrailCheckResult(
            isRestrictedSecurityRequest = true,
            educationalResponse = response
        )
    }

    fun sanitizeModelOutput(text: String): String {
        if (SPECIFIC_SECURITY_POST_FILTER.containsMatchIn(text)) {
            return text + "\n\n🛡️ *Reminder: Finance Coach is for financial education and scenario modeling only and does not recommend specific securities.*"
        }
        return text
    }
}
