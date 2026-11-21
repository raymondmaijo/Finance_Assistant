package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sms.SmsTransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CampusFin AI", appName)
    }

    @Test
    fun `test sms parser extracts zomato expense`() {
        val sms = "Rs. 350.00 debited from A/C XX1234 for UPI transaction to Zomato on 18-Sep-26. Ref no 3298472910."
        val parsed = SmsTransactionParser.parse(sms, "VM-HDFCBK")
        assertNotNull(parsed)
        assertEquals(350.0, parsed!!.amount, 0.01)
        assertEquals("Debit", parsed.type)
        assertEquals("XX1234", parsed.account)
        assertEquals("Zomato", parsed.receiver)
    }

    @Test
    fun `test sms parser filters out otp messages`() {
        val otpSms = "482910 is your secret OTP for login to netbanking. Do not share with anyone."
        val parsed = SmsTransactionParser.parse(otpSms, "VM-HDFCBK")
        assertNull(parsed)
    }

    @Test
    fun `test sms parser detects credit income`() {
        val creditSms = "Dear Customer, your Account XX5678 has been credited with INR 5,000.00 on 18-Sep-26 by transfer from Papa."
        val parsed = SmsTransactionParser.parse(creditSms, "VK-ICICIB")
        assertNotNull(parsed)
        assertEquals(5000.0, parsed!!.amount, 0.01)
        assertEquals("Credit", parsed.type)
    }
}
