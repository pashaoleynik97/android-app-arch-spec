package {{ROOT_PACKAGE}}.files.internal

import okhttp3.Interceptor
import okhttp3.Response
import {{ROOT_PACKAGE}}.files.FilesAuthorizer

/** Applies the bound [FilesAuthorizer] (if any) to every files request. */
internal class FilesAuthInterceptor(private val authorizer: FilesAuthorizer?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return chain.proceed(authorizer?.authorize(request) ?: request)
    }
}
