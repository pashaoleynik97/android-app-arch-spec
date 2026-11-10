package {{ROOT_PACKAGE}}.input.settings

import {{ROOT_PACKAGE}}.input.InputPreference

/**
 * Persistence seam for the input preference, so the repository is unit-tested against an in-memory fake.
 * Implementations are **main-safe** (they switch to their own dispatcher) and never throw on corrupt data —
 * they read it as [InputPreference.Auto] and log.
 */
interface InputSettingsStore {

    suspend fun read(): InputPreference

    suspend fun write(preference: InputPreference)
}
