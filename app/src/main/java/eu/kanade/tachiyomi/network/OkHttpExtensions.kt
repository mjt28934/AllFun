@file:Suppress("Unused", "UnusedReceiverParameter")

package eu.kanade.tachiyomi.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import rx.Observable
import rx.subscriptions.Subscriptions
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Deprecated("Use suspend APIs instead")
fun Call.asObservable(): Observable<Response> = Observable.create { subscriber ->
    val call = clone()
    subscriber.add(Subscriptions.create { call.cancel() })
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!subscriber.isUnsubscribed) subscriber.onError(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (subscriber.isUnsubscribed) {
                response.close()
                return
            }
            subscriber.onNext(response)
            if (!subscriber.isUnsubscribed) subscriber.onCompleted()
        }
    })
}

@Deprecated("Use suspend APIs instead")
fun Call.asObservableSuccess(): Observable<Response> = asObservable().map { response ->
    if (!response.isSuccessful) {
        val code = response.code
        response.close()
        throw HttpException(code)
    }
    response
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    val call = clone()
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}

/**
 * Similar to [await] but throws [HttpException] if [Response.isSuccessful] returns false
 */
suspend fun Call.awaitSuccess(): Response {
    val response = await()
    if (!response.isSuccessful) {
        val code = response.code
        response.close()
        throw HttpException(code)
    }
    return response
}
