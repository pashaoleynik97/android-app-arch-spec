package {{ROOT_PACKAGE}}.auth.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import {{ROOT_PACKAGE}}.auth.network.dto.LoginRequestDto
import {{ROOT_PACKAGE}}.auth.network.dto.LoginResultDto
import {{ROOT_PACKAGE}}.auth.network.dto.RefreshRequestDto
import {{ROOT_PACKAGE}}.auth.network.dto.RefreshResultDto
import {{ROOT_PACKAGE}}.network.dto.ApiResponse
import {{ROOT_PACKAGE}}.network.dto.SuccessDto

/**
 * The session endpoints (example contract), on the session's own client. Returns [Response] rather than bare
 * bodies so the classifier can tell a dead session (401 + code) from a flaky network (5xx, IO).
 */
interface AuthApi {

    @POST("v1/auth/login")
    suspend fun login(@Body body: LoginRequestDto): Response<ApiResponse<LoginResultDto>>

    @POST("v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): Response<ApiResponse<RefreshResultDto>>

    /** Revokes the session the access token belongs to. The bearer is explicit: this client has no auth interceptor. */
    @POST("v1/auth/logout")
    suspend fun logout(@Header("Authorization") authorization: String): Response<ApiResponse<SuccessDto>>
}
