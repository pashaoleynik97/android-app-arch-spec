package {{ROOT_PACKAGE}}.login.domain

/** A typed field error (DATA-30). The composable maps each to its own text. */
enum class LoginFieldError { Required, InvalidEmail }

/** Result of [LoginValidator.validate]; `null` fields are valid. */
data class LoginValidation(val email: LoginFieldError?, val password: LoginFieldError?) {
    val isValid: Boolean get() = email == null && password == null
}

/**
 * Advisory client-side checks before a sign-in is sent (DATA-32: the server stays the authority). Deliberately
 * loose on the email shape — "something@something" — because a stricter client rule rejects valid addresses
 * the server would accept.
 */
object LoginValidator {

    fun validate(email: String, password: String): LoginValidation = LoginValidation(
        email = when {
            email.isBlank() -> LoginFieldError.Required
            !EMAIL_SHAPE.matches(email.trim()) -> LoginFieldError.InvalidEmail
            else -> null
        },
        password = if (password.isEmpty()) LoginFieldError.Required else null,
    )

    private val EMAIL_SHAPE = Regex("^[^@\\s]+@[^@\\s]+$")
}
