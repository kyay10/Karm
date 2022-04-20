package io.github.kyay10.karm

fun <A : Appendable> List<ArmElement>.joinToAsm(
    buffer: A,
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    limit: Int = -1,
    truncated: CharSequence = "...",
): A = joinAppending(buffer, separator, prefix, postfix, limit, truncated) { element, _ -> element.appendAsm() }

context(A) fun <A : Appendable> List<ArmElement>.appendAllAsm(
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    limit: Int = -1,
    truncated: CharSequence = "..."
): A = joinToAsm(given<A>(), separator, prefix, postfix, limit, truncated)

context(Appendable)
fun ArmElement.appendAsm() {
    when (this) {
        is ArmBlockOpCode -> {
            instructions.appendAllAsm("\n")
        }
        is LabelOpCode -> {
            label.appendAsm()
            append(':')
        }
        is BranchOpCode -> {
            condition.decomposed.appendAll("\n") { condition, _ ->
                append('B')
                condition.appendAsm()
                append(' ')
                label.appendAsm()
            }
        }
        is ArmOpCode -> {
            append(armName)
            if (operands.isNotEmpty()) append(" ")
            operands.appendAllAsm(", ")
        }

        else -> append(armName)
    }
}

context(Appendable)
fun ArmBuilder.appendFlattenedAsm() {
    instructions.appendAllAsm("\n")
}

fun ArmBuilder.toAsmString(): String = buildString { appendFlattenedAsm() }