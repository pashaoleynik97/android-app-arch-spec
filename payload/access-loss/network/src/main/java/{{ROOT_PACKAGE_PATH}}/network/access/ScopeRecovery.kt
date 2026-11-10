package {{ROOT_PACKAGE}}.network.access

/**
 * Optional port: how the app re-establishes a lost scope (e.g. send the user to pick another organisation and
 * mint tokens for it) instead of ending the session. Declared `@Multibinds` and left **unbound** by this
 * component — bind at most one, in `:app` or in the feature that owns the scope picker (MOD-19).
 *
 * Return `true` when the recovery took over (navigation started, tokens being re-minted). It is called inside the
 * [RecoveryBudget]; once the budget is spent the host ends the session instead.
 */
fun interface ScopeRecovery {
    suspend fun recover(loss: AccessLoss.ScopeLost): Boolean
}
