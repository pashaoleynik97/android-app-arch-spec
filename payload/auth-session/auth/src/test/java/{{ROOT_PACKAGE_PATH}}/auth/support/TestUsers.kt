package {{ROOT_PACKAGE}}.auth.support

import {{ROOT_PACKAGE}}.auth.SessionUser

/** A [SessionUser] whose id is [id] — the session identity in tests. */
fun testUser(id: String) = SessionUser(id = id, email = "$id@example.test", displayName = "User $id")
