package com.greenhands.app.auth

data class FieldErrors(
    val name: String? = null,
    val email: String? = null,
    val password: String? = null,
    val confirmPassword: String? = null,
    val terms: String? = null
) {
    val hasErrors: Boolean
        get() = name != null || email != null || password != null ||
            confirmPassword != null || terms != null
}

object DemoAuth {
    private val emailRegex = Regex(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    fun isValidEmail(email: String): Boolean = emailRegex.matches(email.trim())

    fun isValidPassword(password: String): Boolean = password.length >= 6

    fun validateLogin(email: String, password: String): FieldErrors {
        return FieldErrors(
            email = emailError(email),
            password = passwordError(password)
        )
    }

    fun validateRegistration(
        name: String,
        email: String,
        password: String,
        confirmPassword: String,
        termsAccepted: Boolean
    ): FieldErrors {
        val confirmError = when {
            confirmPassword.isBlank() -> "Confirm your password"
            password != confirmPassword -> "Passwords do not match"
            else -> null
        }
        return FieldErrors(
            name = if (name.trim().length < 2) "Enter your full name" else null,
            email = emailError(email),
            password = passwordError(password),
            confirmPassword = confirmError,
            terms = if (!termsAccepted) "Please accept the demo terms" else null
        )
    }

    fun validateEmailOnly(email: String): FieldErrors {
        return FieldErrors(email = emailError(email))
    }

    fun validateProfile(name: String, email: String): FieldErrors {
        return FieldErrors(
            name = if (name.trim().length < 2) "Enter your full name" else null,
            email = emailError(email)
        )
    }

    private fun emailError(email: String): String? = when {
        email.isBlank() -> "Enter your email"
        !isValidEmail(email) -> "Enter a valid email address"
        else -> null
    }

    private fun passwordError(password: String): String? = when {
        password.isBlank() -> "Enter your password"
        !isValidPassword(password) -> "Password must be at least 6 characters"
        else -> null
    }
}
