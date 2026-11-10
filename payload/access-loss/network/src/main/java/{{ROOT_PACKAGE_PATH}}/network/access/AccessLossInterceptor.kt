package {{ROOT_PACKAGE}}.network.access

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import {{ROOT_PACKAGE}}.network.ApiInterceptor
import {{ROOT_PACKAGE}}.network.HttpStatus
import {{ROOT_PACKAGE}}.network.peekApiErrorCode
import javax.inject.Inject

/**
 * Turns every 403 on the business client into an [AccessLoss] report — detection in one place, so a new caller
 * cannot forget it. Contributed to the client through the `ApiInterceptor` set (observer band).
 *
 * An application interceptor, so it sees the **final** response (after any authenticator retry). The code is read
 * with `peekBody` — the service that classifies the same response still reads the single-read body (DATA-14) —
 * and the response is returned unchanged: the feature's own ladder still maps the 403 to its `Refused` /
 * `ScopeLost` rung, and simply does nothing for the host-owned one (DATA-18).
 *
 * The session's own client does not get it: refusals there are part of signing in, not a loss of access.
 */
internal class AccessLossInterceptor @Inject constructor(
    private val bus: AccessLossBus,
    private val json: Json,
    private val config: AccessLossConfig,
) : ApiInterceptor {

    override val order: Int = ApiInterceptor.ORDER_OBSERVERS

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != HttpStatus.FORBIDDEN) return response
        val where = response.request.url.encodedPath
        val code = peekApiErrorCode(response, json)
        bus.report(if (code in config.scopeLevelCodes) AccessLoss.ScopeLost(where, code) else AccessLoss.Forbidden(where, code))
        return response
    }
}
