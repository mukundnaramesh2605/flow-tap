package com.flowtap.system

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellQuoteTest {
    @Test
    fun `wraps in single quotes`() {
        assertEquals("'intent:#Intent;end'", "intent:#Intent;end".shellQuoted())
    }

    @Test
    fun `escapes embedded single quotes`() {
        assertEquals("'it'\\''s'", "it's".shellQuoted())
    }
}
