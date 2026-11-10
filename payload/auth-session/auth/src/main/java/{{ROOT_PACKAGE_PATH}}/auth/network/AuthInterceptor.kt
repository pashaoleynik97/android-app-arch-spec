package {{ROOT_PACKAGE}}.auth.network

import okhttp3.Interceptor
import okhttp3.Response
import {{ROOT_PACKAGE}}.auth.AuthManager
import {{ROOT_PACKAGE}}.network.ApiInterceptor
import javax.inject.Inject

/**
 * Attaches `Authorization: Bearer <access>` to every **business** request. Contributed to the network module's
 * client through the `ApiInterceptor` set — never installed on the session client. No token → the request goes
 * out unmodified, and its 401 is the authenticator's business.
 */
internal class AuthInterceptor @Inject constructor(
    private val authManager: AuthManager,
) : ApiInterceptor {

    override val order: Int = ApiInterceptor.ORDER_CREDENTIALS

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authManager.currentAccessToken() ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().newBuilder().header(HEADER_AUTHORIZATION, BEARER_PREFIX + token).build())
    }

    companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
