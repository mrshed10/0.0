package app.safar.browser.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** تحويل GeckoResult إلى دالة suspend. يجب الاستدعاء من خيط له Looper (Main). */
suspend fun <T> GeckoResult<T>.await(): T? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { cont ->
        accept(
            { value -> if (cont.isActive) cont.resume(value) },
            { error -> if (cont.isActive) cont.resumeWithException(error ?: IllegalStateException("GeckoResult failed")) },
        )
    }
}

/** إنشاء GeckoResult يُكمَل من Coroutine (للرد على رسائل الإضافات). يُستدعى على Main. */
fun <T> CoroutineScope.geckoResult(block: suspend () -> T): GeckoResult<T> {
    val result = GeckoResult<T>()
    launch(Dispatchers.Main) {
        try {
            result.complete(block())
        } catch (t: Throwable) {
            result.completeExceptionally(t)
        }
    }
    return result
}
