package {{ROOT_PACKAGE}}.libandroid.base

import androidx.lifecycle.ViewModel
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag

/**
 * Base class for every ViewModel: a ready [logTag] for `Reporter` calls and a log line on teardown.
 *
 * Deliberately thin — no state or event machinery lives here. Each screen states its own surface in an
 * `XViewModelContract` (uiState + uiCallbacks + intents), which keeps the pattern visible at every call site
 * instead of hidden in a base class.
 */
open class BaseViewModel : ViewModel() {

    protected val logTag: String = logTag()

    override fun onCleared() {
        super.onCleared()
        Reporter.debug("onCleared", where = logTag)
    }
}
