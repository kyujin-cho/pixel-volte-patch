package dev.bluehouse.enablevolte.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentifierMaskerTest {
    private val imsi = "450061234567890"
    private val iccid = "8982061234567890123F"
    private val imei = "352099001761481"
    private val msisdn = "+821012345678"

    private fun masked(
        text: String,
        known: Collection<String> = emptyList(),
    ) = IdentifierMasker.mask(text, known).text

    private fun assertNoDigitsOf(
        identifier: String,
        text: String,
    ) {
        val digits = identifier.filter { it.isDigit() }
        assertFalse("'$digits' leaked in: $text", text.filter { it.isDigit() || it == ' ' }.contains(digits))
        assertFalse("'$identifier' leaked in: $text", text.contains(identifier))
    }

    @Test
    fun `keyed identifiers are masked by kind`() {
        assertEquals("mImsi=[IMSI] subId=1", masked("mImsi=$imsi subId=1"))
        assertEquals("iccid: [ICCID]", masked("iccid: $iccid"))
        assertEquals("IMEI=[IMEI]", masked("IMEI=$imei"))
        assertEquals("msisdn=[PHONE]", masked("msisdn=$msisdn"))
        assertEquals("mIccId='[ICCID]'", masked("mIccId='$iccid'"))
        assertEquals("subscriberId=[IMSI],", masked("subscriberId=$imsi,"))
    }

    @Test
    fun `short keyed values are left alone`() {
        assertEquals("number=3 imsi=null phoneNumber=", masked("number=3 imsi=null phoneNumber="))
    }

    @Test
    fun `bare identifiers are masked`() {
        for (id in listOf(imsi, iccid, imei)) {
            val text = masked("value $id end")
            assertNoDigitsOf(id, text)
        }
        assertEquals("x [ICCID] y", masked("x $iccid y"))
        assertEquals("imei 35-209900-176148-1 here".replace("35-209900-176148-1", "[IMEI]"), masked("imei 35-209900-176148-1 here"))
    }

    @Test
    fun `phone numbers in common formats are masked`() {
        assertEquals("call [PHONE] now", masked("call $msisdn now"))
        assertEquals("call [PHONE] now", masked("call +82 10-1234-5678 now"))
        assertEquals("call [PHONE] now", masked("call 010-1234-5678 now"))
        assertEquals("call [PHONE] now", masked("call +1 (555) 123-4567 now"))
        assertEquals("call [PHONE] now", masked("call (555) 123-4567 now"))
        assertEquals("call [NUMBER] now", masked("call 01012345678 now"))
    }

    @Test
    fun `sip and tel uri user parts are masked but the domain is kept`() {
        assertEquals("To: <sip:[PHONE]@ims.example.org>", masked("To: <sip:$msisdn@ims.example.org>"))
        assertEquals("tel:[PHONE];phone-context=x", masked("tel:+821012345678;phone-context=x"))
        assertEquals(
            "From: sip:[ID]@ims.mnc006.mcc450.3gppnetwork.org",
            masked("From: sip:$imsi@ims.mnc006.mcc450.3gppnetwork.org"),
        )
    }

    @Test
    fun `logcat timestamps and small numbers survive`() {
        val line = "11-25 10:00:00.123  1234  5678 I ImsService: registered on subId 2, slot 0, mtu 1500"
        assertEquals(line, masked(line))
        val date = "2026-11-25 12:34:56 data_app_anr (text, 12345 bytes)"
        assertEquals(date, masked(date))
    }

    @Test
    fun `known identifiers are masked as literals in any form`() {
        val known = listOf(iccid, msisdn, imei)
        val text = masked("a ${iccid.dropLast(1)} b 821012345678 c ${imei}x", known)
        assertNoDigitsOf(iccid.dropLast(1), text)
        assertNoDigitsOf("821012345678", text)
        assertTrue(text, text.contains("[ID]x"))
    }

    @Test
    fun `short known identifiers are ignored`() {
        assertEquals("pid 1234", masked("pid 1234", listOf("1234", "")))
    }

    @Test
    fun `masked count reflects replacements`() {
        val result = IdentifierMasker.mask("imsi=$imsi and $msisdn and nothing")
        assertEquals(2, result.maskedCount)
        assertEquals(0, IdentifierMasker.mask("nothing to see").maskedCount)
    }

    @Test
    fun `radio log excerpt has no identifiers left`() {
        val log =
            """
            11-25 10:00:00.100  1000  1100 D RILJ    : [UNSL]< UNSOL_RESPONSE_SIM_STATUS iccid=$iccid [PHONE0]
            11-25 10:00:00.200  1000  1100 D SubscriptionManagerService: updateSubscription: mIccId=$iccid mNumber=$msisdn
            11-25 10:00:00.300  1000  1100 I ImsRegistration: REGISTER sip:$imsi@ims.mnc006.mcc450.3gppnetwork.org
            11-25 10:00:00.400  1000  1100 I ImsRegistration: P-Associated-URI: <sip:$msisdn@ims.mnc006.mcc450.3gppnetwork.org>, <tel:$msisdn>
            11-25 10:00:00.500  1000  1100 D PhoneSubInfoController: getImeiForSubscriber $imei
            """.trimIndent()
        val text = masked(log)
        for (id in listOf(imsi, iccid, imei, msisdn)) assertNoDigitsOf(id, text)
        assertTrue(text.contains("ims.mnc006.mcc450.3gppnetwork.org"))
        assertTrue(text.contains("11-25 10:00:00.500  1000  1100 D"))
    }

    @Test
    fun `literal variants include digits-only and unpadded forms, longest first`() {
        val variants = IdentifierMasker.literalVariants(listOf("+82 10-1234-5678", "8982061234567890123F"))
        assertTrue(variants.contains("821012345678"))
        assertTrue(variants.contains("8982061234567890123"))
        assertEquals(variants.sortedByDescending { it.length }, variants)
    }
}
