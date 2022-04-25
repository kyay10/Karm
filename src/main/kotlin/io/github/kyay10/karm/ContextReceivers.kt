@file:OptIn(ExperimentalContracts::class)

package io.github.kyay10.karm

import org.intellij.lang.annotations.Language
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

inline fun <A, R> withContexts(a: A, block: context(A) (TypeWrapper<A>) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, TypeWrapper.IMPL)
}

inline fun <A, B, R> withContexts(a: A, b: B, block: context(A, B) (TypeWrapper<B>) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, b, TypeWrapper.IMPL)
}

inline fun <A, B, C, R> withContexts(a: A, b: B, c: C, block: context(A, B, C) (TypeWrapper<C>) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, b, c, TypeWrapper.IMPL)
}

inline fun <A, B, C, D, R> withContexts(a: A, b: B, c: C, d: D, block: context(A, B, C, D) (TypeWrapper<D>) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, b, c, d, TypeWrapper.IMPL)
}

inline fun <A, B, C, D, E, R> withContexts(
    a: A, b: B, c: C, d: D, e: E, block: context(A, B, C, D, E) (TypeWrapper<E>) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, b, c, d, e, TypeWrapper.IMPL)
}

inline fun <A, B, C, D, E, F, R> withContexts(
    a: A, b: B, c: C, d: D, e: E, f: F, block: context(A, B, C, D, E, F) (TypeWrapper<F>) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, b, c, d, e, f, TypeWrapper.IMPL)
}

inline fun <A, B, C, D, E, F, G, R> withContexts(
    a: A, b: B, c: C, d: D, e: E, f: F, g: G, block: context(A, B, C, D, E, F, G) (TypeWrapper<G>) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, b, c, d, e, f, g, TypeWrapper.IMPL)
}

inline fun <A, B, C, D, E, F, G, H, R> withContexts(
    a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, block: context(A, B, C, D, E, F, G, H) (TypeWrapper<H>) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, b, c, d, e, f, g, h, TypeWrapper.IMPL)
}

inline fun <A, B, C, D, E, F, G, H, I, R> withContexts(
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    block: context(A, B, C, D, E, F, G, H, I) (TypeWrapper<I>) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, b, c, d, e, f, g, h, i, TypeWrapper.IMPL)
}

inline fun <A, B, C, D, E, F, G, H, I, J, R> withContexts(
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    block: context(A, B, C, D, E, F, G, H, I, J) (TypeWrapper<J>) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, b, c, d, e, f, g, h, i, j, TypeWrapper.IMPL)
}

inline fun <A, B, C, D, E, F, G, H, I, J, K, R> withContexts(
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    block: context(A, B, C, D, E, F, G, H, I, J, K) (TypeWrapper<K>) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, b, c, d, e, f, g, h, i, j, k, TypeWrapper.IMPL)
}

inline fun <A, B, C, D, E, F, G, H, I, J, K, L, R> withContexts(
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    block: context(A, B, C, D, E, F, G, H, I, J, K, L) (TypeWrapper<L>) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, b, c, d, e, f, g, h, i, j, k, l, TypeWrapper.IMPL)
}

inline fun <A, B, C, D, E, F, G, H, I, J, K, L, M, R> withContexts(
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    block: context(A, B, C, D, E, F, G, H, I, J, K, L, M) (TypeWrapper<M>) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, b, c, d, e, f, g, h, i, j, k, l, m, TypeWrapper.IMPL)
}

inline fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, R> withContexts(
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    block: context(A, B, C, D, E, F, G, H, I, J, K, L, M, N) (TypeWrapper<N>) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, b, c, d, e, f, g, h, i, j, k, l, m, n, TypeWrapper.IMPL)
}

sealed interface TypeWrapper<out A> {
    object IMPL : TypeWrapper<Nothing>
}

context(A) fun <A> given(): A = id()
fun <A> A.id(): A = this

fun generateDeclarations(index: Int): String {
    val alphabet = "BCDEFGHIJKLMNOPQSTUVWXYZ"
    val letters = ("A" + alphabet.take(index)).toList()
    val lastType = letters.last()
    val types = letters.joinToString(", ")
    val receivers = letters.joinToString(", ") { it.lowercase() }
    val parameters = letters.joinToString(", ") { "${it.lowercase()}: $it" }

    @Language("kotlin") val codeTemplate = """
        inline fun <$types, R> withContexts($parameters, block: context($types) (TypeWrapper<$lastType>) -> R): R {
            contract {
                callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            }
            return block($receivers, TypeWrapper.IMPL)
        }
    """.trimIndent()
    return codeTemplate
}
