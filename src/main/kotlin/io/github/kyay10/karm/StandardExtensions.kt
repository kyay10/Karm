package io.github.kyay10.karm

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun <T> T.replaceIf(condition: Boolean, replacement: (T) -> T): T {
    contract {
        callsInPlace(replacement, InvocationKind.AT_MOST_ONCE)
    }
    return if (condition) replacement(this) else this
}

@OptIn(ExperimentalContracts::class)
fun <T> T.replaceIf(condition: Boolean, replacement: () -> T): T {
    contract {
        callsInPlace(replacement, InvocationKind.AT_MOST_ONCE)
    }
    return if (condition) replacement() else this
}

// Reimplementation of joinTo from stdlib but in a way that supports custom appending
fun <T, A : Appendable> Iterable<T>.joinAppending(
    buffer: A,
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    limit: Int = -1,
    truncated: CharSequence = "...",
    appendAction: context(A) (T, TypeWrapper<A>) -> Unit
): A = withContexts(buffer) {
    append(prefix)
    var count = 0
    for (element in this) {
        if (++count > 1) append(separator)
        if (limit < 0 || count <= limit) {
            appendAction(given(), element, TypeWrapper.IMPL)
        } else break
    }
    if (limit in 0 until count) append(truncated)
    append(postfix)
    return buffer
}

context(A) fun <T, A : Appendable> Iterable<T>.appendAll(
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    limit: Int = -1,
    truncated: CharSequence = "...",
    appendAction: context(A) (T, TypeWrapper<A>) -> Unit
): A = joinAppending(given<A>(), separator, prefix, postfix, limit, truncated, appendAction)

fun <T> Appendable.appendElement(element: T, transform: ((T) -> CharSequence)?) {
    when {
        transform != null -> append(transform(element))
        element is CharSequence? -> append(element)
        element is Char -> append(element)
        else -> append(element.toString())
    }
}

fun <T> Iterator<T>.nextOrNull() = if (hasNext()) next() else null
fun <T> ListIterator<T>.previousOrNull() = if (hasPrevious()) previous() else null

inline fun <T> Iterator<T>.nextUntilOrNull(predicate: (T) -> Boolean): T? {
    for (element in this) {
        if (predicate(element)) {
            return element
        }
    }
    return null
}

inline fun <T> ListIterator<T>.previousUntilOrNull(predicate: (T) -> Boolean): T? {
    while (hasPrevious()) {
        val element = previous()
        if (predicate(element)) {
            return element
        }
    }
    return null
}

