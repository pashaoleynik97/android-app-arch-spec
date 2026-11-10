package {{ROOT_PACKAGE}}.auth.network

import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.Response
import {{ROOT_PACKAGE}}.auth.SessionUser
import {{ROOT_PACKAGE}}.auth.network.dto.AuthUserDto
import {{ROOT_PACKAGE}}.auth.network.dto.LoginRequestDto
import {{ROOT_PACKAGE}}.auth.network.dto.RefreshRequestDto
import {{ROOT_PACKAGE}}.libcommon.coroutines.DispatcherProvider
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import {{ROOT_PACKAGE}}.network.HttpStatus
import {{ROOT_PACKAGE}}.network.classifyCall
import java.io.IOException
import javax.inject.Inject

/**
 * [AuthService] over Retrofit — the session layer's **failure classifier**. Ladder (example contract):
 * - 2xx with a well-formed token pair → [AuthNetworkResult.Success]
 * - 2xx without a body, or with a blank token → [AuthNetworkResult.Transient] (a server bug, not a refusal)
 * - 401 → [AuthNetworkResult.AuthRejected] with the envelope's code (per-endpoint default when the body is not
 *   the envelope) and, on refresh, `details.reason`
 * - 400 / 403 with a code in [DEFINITIVE_LOGIN_CODES] → [AuthNetworkResult.AuthRejected]
 * - anything else, IO and undecodable bodies → [AuthNetworkResult.Transient]
 *
 * Runs on the session's own client, which has no authenticator — so a 401 here never triggers a refresh.
 */
internal class RetrofitAuthService @Inject constructor(
    private val api: AuthApi,
    private val dispatchers: DispatcherProvider,
    private val json: Json,
) : AuthService {

    private val logTag = logTag()

    override suspend fun login(email: String, password: String): AuthNetworkResult = classify(
        call = { api.login(LoginRequestDto(email = email, password = password)) },
        defaultRejectCode = AuthErrorCode.INVALID_CREDENTIALS,
    ) { body -> body.data.let { tokens(it.accessToken, it.refreshToken, it.expiresIn, it.user.toDomain()) } }

    override suspend fun refresh(refreshToken: String): AuthNetworkResult = classify(
        call = { api.refresh(RefreshRequestDto(refreshToken = refreshToken)) },
        defaultRejectCode = AuthErrorCode.REFRESH_TOKEN_INVALID,
    ) { body -> body.data.let { tokens(it.accessToken, it.refreshToken, it.expiresIn, user = null) } }

    override suspend fun logout(accessToken: String): Boolean = withContext(dispatchers.io) {
        try {
            val response = api.logout("Bearer $accessToken")
            response.isSuccessful || response.code() < HttpStatus.SERVER_ERROR
        } catch (e: IOException) {
            Reporter.debug("remote logout failed transiently: ${e.message}", where = logTag)
            false
        }
    }

    private suspend fun <B> classify(
        call: suspend () -> Response<B>,
        defaultRejectCode: AuthErrorCode,
        toSuccess: (B) -> AuthNetworkResult,
    ): AuthNetworkResult = classifyCall(
        dispatcher = dispatchers.io,
        call = call,
        onFailure = { AuthNetworkResult.Transient(it) },
    ) { response ->
        when {
            response.isSuccessful -> response.body()?.let(toSuccess) ?: AuthNetworkResult.Transient(null)

            response.code() == HttpStatus.UNAUTHORIZED -> {
                // Single-read body: parse once, use both fields.
                val parsed = parseAuthError(response, json)
                AuthNetworkResult.AuthRejected(parsed?.code ?: defaultRejectCode, parsed?.sessionEndReason)
            }

            response.code() == HttpStatus.BAD_REQUEST || response.code() == HttpStatus.FORBIDDEN -> {
                val code = parseAuthError(response, json)?.code
                if (code != null && code in DEFINITIVE_LOGIN_CODES) {
                    AuthNetworkResult.AuthRejected(code)
                } else {
                    Reporter.warn("auth call → ${response.code()} ($code): treated as transient", where = logTag)
                    AuthNetworkResult.Transient(null)
                }
            }

            else -> {
                Reporter.warn("auth call → ${response.code()}: transient", where = logTag)
                AuthNetworkResult.Transient(null)
            }
        }
    }

    private fun tokens(access: String, refresh: String, expiresIn: Long, user: SessionUser?): AuthNetworkResult =
        if (access.isBlank() || refresh.isBlank()) {
            Reporter.warn("auth 2xx with a blank token: transient", where = logTag)
            AuthNetworkResult.Transient(null)
        } else {
            AuthNetworkResult.Success(access, refresh, expiresIn, user)
        }

    private companion object {
        /** 400 / 403 codes that are a definitive "no" to a login (anything else on those statuses is ambiguous). */
        val DEFINITIVE_LOGIN_CODES = setOf(AuthErrorCode.VALIDATION_ERROR, AuthErrorCode.ACCOUNT_DISABLED)
    }
}

private fun AuthUserDto.toDomain() = SessionUser(id = id, email = email, displayName = displayName)
