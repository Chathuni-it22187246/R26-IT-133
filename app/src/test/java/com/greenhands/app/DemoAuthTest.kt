package com.greenhands.app

import com.greenhands.app.auth.DemoAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAuthTest {

    @Test
    fun validEmailPasses() {
        assertTrue(DemoAuth.isValidEmail("grower@greenhands.app"))
        assertTrue(DemoAuth.isValidEmail("  user.name+tag@example.co.uk  "))
    }

    @Test
    fun invalidEmailFails() {
        assertFalse(DemoAuth.isValidEmail(""))
        assertFalse(DemoAuth.isValidEmail("not-an-email"))
        assertFalse(DemoAuth.isValidEmail("missing@domain"))
        assertFalse(DemoAuth.isValidEmail("spaces @greenhands.app"))
    }

    @Test
    fun loginRejectsEmptyAndInvalidInput() {
        val empty = DemoAuth.validateLogin("", "")
        assertEquals("Enter your email", empty.email)
        assertEquals("Enter your password", empty.password)
        assertTrue(empty.hasErrors)

        val invalid = DemoAuth.validateLogin("bad", "123")
        assertEquals("Enter a valid email address", invalid.email)
        assertEquals("Password must be at least 6 characters", invalid.password)
    }

    @Test
    fun loginAcceptsSyntacticallyValidCredentials() {
        val result = DemoAuth.validateLogin("demo@greenhands.app", "secret1")
        assertFalse(result.hasErrors)
        assertNull(result.email)
        assertNull(result.password)
    }

    @Test
    fun registrationRequiresMatchingPasswordsAndTerms() {
        val mismatch = DemoAuth.validateRegistration(
            name = "Ada Grower",
            email = "ada@greenhands.app",
            password = "secret1",
            confirmPassword = "secret2",
            termsAccepted = true
        )
        assertEquals("Passwords do not match", mismatch.confirmPassword)
        assertTrue(mismatch.hasErrors)

        val noTerms = DemoAuth.validateRegistration(
            name = "Ada Grower",
            email = "ada@greenhands.app",
            password = "secret1",
            confirmPassword = "secret1",
            termsAccepted = false
        )
        assertEquals("Please accept the demo terms", noTerms.terms)

        val ok = DemoAuth.validateRegistration(
            name = "Ada Grower",
            email = "ada@greenhands.app",
            password = "secret1",
            confirmPassword = "secret1",
            termsAccepted = true
        )
        assertFalse(ok.hasErrors)
    }

    @Test
    fun forgotPasswordValidatesEmailOnly() {
        assertEquals("Enter your email", DemoAuth.validateEmailOnly("").email)
        assertNull(DemoAuth.validateEmailOnly("reset@greenhands.app").email)
    }
}
