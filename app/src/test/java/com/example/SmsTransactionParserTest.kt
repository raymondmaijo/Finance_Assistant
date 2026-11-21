package com.example

import com.example.sms.SmsTransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsTransactionParserTest {

    @Test
    fun testStructuredDebitSms_RahulKumar() {
        val sms = "Debited Rs 350 from a/c X1234 on 19Sep26 08:15 PM via UPI to Rahul Kumar"
        val parsed = SmsTransactionParser.parse(sms, "VM-HDFCBK")

        assertNotNull(parsed)
        parsed?.let {
            assertEquals("Debit", it.type)
            assertEquals(350.0, it.amount, 0.01)
            assertEquals("X1234", it.account)
            assertEquals("19Sep26", it.date)
            assertEquals("08:15 PM", it.time)
            assertEquals("UPI", it.mode)
            assertEquals("Rahul Kumar", it.receiver)
        }
    }

    @Test
    fun testStructuredDebitSms_Zomato() {
        val sms = "Debited Rs 1200 from a/c X5678 on 21Sep26 10:45 AM via CARD to Zomato"
        val parsed = SmsTransactionParser.parse(sms, "AX-SBIINB")

        assertNotNull(parsed)
        parsed?.let {
            assertEquals("Debit", it.type)
            assertEquals(1200.0, it.amount, 0.01)
            assertEquals("X5678", it.account)
            assertEquals("21Sep26", it.date)
            assertEquals("10:45 AM", it.time)
            assertEquals("CARD", it.mode)
            assertEquals("Zomato", it.receiver)
        }
    }

    @Test
    fun testShortDebitSms_Swiggy() {
        val sms = "Debited Rs 250 via UPI to Swiggy"
        val parsed = SmsTransactionParser.parse(sms, "VM-HDFCBK")

        assertNotNull(parsed)
        parsed?.let {
            assertEquals("Debit", it.type)
            assertEquals(250.0, it.amount, 0.01)
            assertEquals("UPI", it.mode)
            assertEquals("Swiggy", it.receiver)
        }
    }

    @Test
    fun testDirectUpiFormats() {
        // Paid Rs X to Y via UPI
        val zeptoSms = "Paid Rs 210 to Zepto via UPI"
        val zeptoParsed = SmsTransactionParser.parse(zeptoSms, "VK-AXISBK")
        assertNotNull(zeptoParsed)
        assertEquals(210.0, zeptoParsed!!.amount, 0.01)
        assertEquals("Zepto", zeptoParsed.receiver)
        assertEquals("UPI", zeptoParsed.mode)

        // UPI txn of Rs X to Y
        val dhabaSms = "UPI txn of Rs 380 to Sharma Dhaba"
        val dhabaParsed = SmsTransactionParser.parse(dhabaSms, "VM-HDFCBK")
        assertNotNull(dhabaParsed)
        assertEquals(380.0, dhabaParsed!!.amount, 0.01)
        assertEquals("Sharma Dhaba", dhabaParsed.receiver)
        assertEquals("UPI", dhabaParsed.mode)

        // Campus Canteen
        val canteenSms = "Debited Rs 140 from a/c X1234 on 19Sep26 01:30 PM via UPI to Campus Canteen"
        val canteenParsed = SmsTransactionParser.parse(canteenSms, "VM-HDFCBK")
        assertNotNull(canteenParsed)
        assertEquals(140.0, canteenParsed!!.amount, 0.01)
        assertEquals("Campus Canteen", canteenParsed.receiver)
        assertEquals("X1234", canteenParsed.account)
        assertEquals("UPI", canteenParsed.mode)
    }

    @Test
    fun testStructuredDebitWithRefBalAndEmergencyCall() {
        val sms = "Debited Rs 350 from a/c2405 on 19Sep26 08:15 PM via UPI to Rahul Kumar Ref 429810392810.Bal Rs 5,420.00. Not you?Call1800120120- HDFC Bank"
        val parsed = SmsTransactionParser.parse(sms, "VM-HDFCBK")

        assertNotNull(parsed)
        parsed?.let {
            assertEquals("Debit", it.type)
            assertEquals(350.0, it.amount, 0.01)
            assertEquals("2405", it.account)
            assertEquals("19Sep26", it.date)
            assertEquals("08:15 PM", it.time)
            assertEquals("UPI", it.mode)
            assertEquals("Rahul Kumar", it.receiver)
            assertEquals("429810392810", it.txnRef)
            assertEquals(5420.0, it.balance ?: 0.0, 0.01)
            assertEquals("1800120120", it.emergencyNumber)
            assertEquals("HDFC Bank", it.bankName)
        }

        // Full month name variation
        val fullMonthSms = "Debited Rs 450 from a/c2405 on 19September26 08:15 PM via UPI to Canteen Manager Ref 123456789012.Bal Rs 12450. Not you?Call1800200-SBI"
        val fullMonthParsed = SmsTransactionParser.parse(fullMonthSms, "AX-SBIINB")
        assertNotNull(fullMonthParsed)
        fullMonthParsed?.let {
            assertEquals("Debit", it.type)
            assertEquals(450.0, it.amount, 0.01)
            assertEquals("2405", it.account)
            assertEquals("19September26", it.date)
            assertEquals("08:15 PM", it.time)
            assertEquals("UPI", it.mode)
            assertEquals("Canteen Manager", it.receiver)
            assertEquals("123456789012", it.txnRef)
            assertEquals(12450.0, it.balance ?: 0.0, 0.01)
        }

        // Card & decimals with spaced emergency number
        val cardSms = "Debited Rs 1200.50 from a/c2405 on 21Sep26 10:45 AM via CARD to Zomato Ref 987654321012.Bal Rs 3,450.75. Not you?Call 1800-123-456 - Axis Bank"
        val cardParsed = SmsTransactionParser.parse(cardSms, "VK-AXISBK")
        assertNotNull(cardParsed)
        cardParsed?.let {
            assertEquals("Debit", it.type)
            assertEquals(1200.50, it.amount, 0.01)
            assertEquals("2405", it.account)
            assertEquals("21Sep26", it.date)
            assertEquals("10:45 AM", it.time)
            assertEquals("CARD", it.mode)
            assertEquals("Zomato", it.receiver)
            assertEquals("987654321012", it.txnRef)
            assertEquals(3450.75, it.balance ?: 0.0, 0.01)
            assertEquals("Axis Bank", it.bankName)
        }
    }

    @Test
    fun testIgnoredMessages() {
        // OTP messages
        assertNull(SmsTransactionParser.parse("482910 is your secret OTP for login to netbanking. Do not share OTP.", "VM-HDFCBK"))
        assertNull(SmsTransactionParser.parse("Your One Time Password is 829103. Valid for 5 mins.", "VM-SBI"))

        // Advertisements & Offers & Cashback
        assertNull(SmsTransactionParser.parse("Special Cashback Offer: Get Rs 100 cashback on your next recharge! Visit app now.", "AD-RECHRG"))
        assertNull(SmsTransactionParser.parse("Exclusive advertisement: Get pre-approved loan of Rs 50,000 today.", "AD-LOANS"))

        // Failed / Reversed transactions
        assertNull(SmsTransactionParser.parse("Transaction of Rs 500 on a/c X1234 has failed due to network timeout.", "VM-HDFCBK"))
        assertNull(SmsTransactionParser.parse("Transaction of Rs 350 on card X5678 has been reversed.", "VM-ICICIB"))
        assertNull(SmsTransactionParser.parse("Transaction cancelled: Rs 1500 was declined.", "VM-HDFCBK"))
    }
}
