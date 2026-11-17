package app.allfun.data.net

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * One readable sentence for the user, plus the raw text for the "technical details"
 * disclosure. Nothing here hides information: it moves the stack-trace language behind a
 * secondary control and keeps the primary message actionable.
 */
data class UserError(
    val message: String,
    val technical: String?
)

/**
 * Turns a thrown exception into a message a person can act on. The mapping is deliberately
 * conservative: only well-understood failures get a tailored sentence, everything else
 * keeps the exception's own message so a developer can still diagnose it.
 */
fun Throwable.toUserError(subject: String = "this"): UserError {
    val raw = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
    val text = raw.lowercase()
    val message = when (this) {
        is UnknownHostException -> "Can't reach that server. Check the address and your connection."
        is ConnectException -> "The server refused the connection or is offline."
        is SocketTimeoutException -> "The server took too long to answer. Try again in a moment."
        is SSLException -> "The secure connection failed. The server's certificate may be invalid."
        is IOException -> when {
            text.contains("http 401") || text.contains("http 403") ->
                "This source rejected the request. Sign in again or check your credentials."
            text.contains("http 404") ->
                "This source no longer has that item. It may have been removed or renamed."
            text.contains("http 429") ->
                "This source is rate-limiting AllFun. Wait a minute, then try again."
            text.contains("http 5") ->
                "The source is having problems. Try again later."
            text.contains("http 4") ->
                "The source could not complete that request."
            text.contains("timeout") || text.contains("timed out") ->
                "The request timed out. Check your connection and try again."
            else -> "Could not load $subject. Check your connection or try again."
        }
        is SecurityException -> "AllFun is not allowed to open that file."
        is IllegalArgumentException -> raw
        else -> raw
    }
    val technical = buildString {
        append(javaClass.simpleName)
        if (raw != javaClass.simpleName) append(": ").append(raw)
        val origin = stackTrace.firstOrNull { it.className.startsWith("app.allfun") }
        if (origin != null) append("\n  at ").append(origin)
    }
    return UserError(message, technical)
}

/** Useful for snackbars where only the sentence fits. */
fun Throwable.userMessage(subject: String = "this"): String = toUserError(subject).message
