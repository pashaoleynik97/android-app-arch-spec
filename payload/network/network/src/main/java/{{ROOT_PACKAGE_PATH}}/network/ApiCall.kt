package {{ROOT_PACKAGE}}.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException

/**
 * The skeleton every `RetrofitXService` classifier shares (DATA-10 … DATA-13), so each service writes only its
 * ladder:
 *
 * ```
 * override suspend fun get(id: String, policy: RefreshPolicy): ItemResult = classifyCall(
 *     dispatcher = dispatchers.io,
 *     call = { api.get(id, policy) },
 *     onFailure = { ItemResult.Transient(it) },                    // IO error or undecodable 2xx body
 * ) { r ->
 *     when {
 *         r.isSuccessful -> r.body()?.data?.toDomain()?.let(ItemResult::Success) ?: ItemResult.Transient(null)
 *         r.code() == HttpStatus.UNAUTHORIZED -> ItemResult.SessionExpired
 *         r.code() == HttpStatus.NOT_FOUND -> ItemResult.Unavailable
 *         else -> ItemResult.Transient(null)
 *     }
 * }
 * ```
 *
 * - **Main-safe**: the call and the classification run on [dispatcher] (DATA-08).
 * - **Catches only** `IOException` (timeouts, resets, DNS) and `SerializationException` (a 2xx whose body does
 *   not decode) and hands them to [onFailure]. `CancellationException` propagates — leaving a screen cancels the
 *   HTTP call instead of turning into a bogus failure; anything else is a bug and should crash in development.
 * - A 2xx without a usable body is the classifier's job to map to its transient rung — never to `Success` with
 *   defaults (DATA-12).
 */
suspend fun <T, R> classifyCall(
    dispatcher: CoroutineDispatcher,
    call: suspend () -> Response<T>,
    onFailure: (Throwable) -> R,
    classify: (Response<T>) -> R,
): R = withContext(dispatcher) {
    val response = try {
        call()
    } catch (e: IOException) {
        return@withContext onFailure(e)
    } catch (e: SerializationException) {
        return@withContext onFailure(e)
    }
    classify(response)
}
