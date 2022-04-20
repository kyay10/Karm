package io.github.kyay10.karm

import AqAsm.*

@JvmInline
value class IfScope(val elseLabel: ArmLabel)

context(IfScope, ArmBuilder) fun branchToElse(): BranchOpCode {
    return branch(elseLabel)
}

class IfCall(val name: String, val endOfTruthBlock: ArmBuilder)
class ElseIfCall(val latestIfCall: IfCall, val builder: ArmBuilder)

context(ArmBuilder) fun If(
    condition: ArmValueOperand,
    name: String,
    shouldNegate: Boolean = false,
    block: context(IfScope) ArmBuilder.() -> Unit
): IfCall {
    val endOfTruthBlock: ArmBuilder
    +buildArm {
        val conditionalSubroutine = if (condition is ArmConditionalSubroutineBuilder) condition else condition `!=` 0.c
        conditionalSubroutine.baseName = name
        conditionalSubroutine.negated.baseName = name
        call(io.github.kyay10.karm.replaceIf(shouldNegate, replacement = conditionalSubroutine::negated).also {
            withinBuilder(it.truthBlock) {
                block(IfScope(it.end), this)
                endOfTruthBlock = +buildArm { }
            }
        })
        /*} else {
            val notTrue by label(name + "_false")
            compare(condition, 0.c)
            if (shouldNegate) branchNotEqual(notTrue) else branchEqual(notTrue)
            block()
            endOfTruthBlock = +buildArm { }
            label(notTrue)
        }*/
    }
    return IfCall(name, endOfTruthBlock)
}

context(ArmBuilder) infix fun IfCall.Else(block: ArmBuilder.() -> Unit) = +buildArm {
    val endIf by label(name + "_end")
    endOfTruthBlock.instructions.add(BranchOpCode(endIf))
    block()
    label(endIf)
}

context(ArmBuilder) infix fun IfCall.Else(other: IfCall) = ElseIfCall(other, +buildArm {
    val endIf by label(name + "_end")
    endOfTruthBlock.instructions.add(BranchOpCode(endIf))
    label(endIf)
})

context(ArmBuilder) infix fun ElseIfCall.Else(other: IfCall) = ElseIfCall(other, +buildArm {
    val endIf by (builder.instructions.single() as LabelOpCode).label
    builder.instructions.clear()
    latestIfCall.endOfTruthBlock.instructions.add(BranchOpCode(endIf))
    +(endIf.position!!)
})

context(ArmBuilder) infix fun ElseIfCall.Else(block: ArmBuilder.() -> Unit) = +buildArm {
    val endIf by (builder.instructions.single() as LabelOpCode).label
    builder.instructions.clear()
    latestIfCall.endOfTruthBlock.instructions.add(BranchOpCode(endIf))
    block()
    +(endIf.position!!)
}