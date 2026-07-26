package com.happwner.desktop

import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputValidatorTest {
    @Test
    fun acceptsHttpAndWrappedSubscriptionLinks() {
        assertNull(InputValidator.sourceIssue("https://example.com/sub"))
        val wrapped = "happ://add/" + URLEncoder.encode("https://example.com/sub", Charsets.UTF_8)
        assertNull(InputValidator.sourceIssue(wrapped))
    }

    @Test
    fun rejectsMissingHostAndUnsupportedText() {
        assertEquals(SourceValidationIssue.INVALID, InputValidator.sourceIssue("https://"))
        assertEquals(SourceValidationIssue.INVALID, InputValidator.sourceIssue("not a subscription"))
        assertEquals(SourceValidationIssue.EMPTY, InputValidator.sourceIssue("  "))
    }

    @Test
    fun acceptsOnlyUserPorts() {
        assertEquals(8166, InputValidator.validPort("8166"))
        assertNull(InputValidator.validPort(""))
        assertNull(InputValidator.validPort("80"))
        assertNull(InputValidator.validPort("65536"))
    }
}
