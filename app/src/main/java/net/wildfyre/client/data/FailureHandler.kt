package net.wildfyre.client.data

import android.util.Log
import androidx.annotation.CallSuper
import net.wildfyre.client.R
import net.wildfyre.client.WildFyreApplication

/**
 * Interface implemented by classes that can receive a [Failure] and propagate it.
 */
interface FailureHandler {
    /**
     * Called whenever an operation failed.
     *
     * @param failure The object containing information on what went wrong
     */
    @CallSuper
    fun onFailure(failure: Failure) {
        Log.e(WildFyreApplication.context.getString(R.string.app_name), failure.throwable.message)
    }
}
