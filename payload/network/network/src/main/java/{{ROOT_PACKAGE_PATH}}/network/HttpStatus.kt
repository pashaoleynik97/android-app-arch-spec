package {{ROOT_PACKAGE}}.network

/** The status codes classifiers branch on, named so a ladder reads as a contract rather than as magic numbers. */
object HttpStatus {
    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    const val CONFLICT = 409
    const val GONE = 410
    const val UNPROCESSABLE = 422
    const val TOO_MANY_REQUESTS = 429
    const val SERVER_ERROR = 500
}
