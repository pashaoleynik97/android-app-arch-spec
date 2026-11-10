package {{ROOT_PACKAGE}}.network.log

/**
 * Persistence seam for the [HttpLogPolicy], so [HttpLogSettingsRepository] is tested against an in-memory fake.
 * Implementations are main-safe (they move disk access to their own dispatcher) and never throw on corrupt data.
 */
interface HttpLogSettingsStore {
    suspend fun read(): HttpLogPolicy
    suspend fun write(policy: HttpLogPolicy)
}
