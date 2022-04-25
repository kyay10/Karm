package io.github.kyay10.karm

@JvmInline
value class IfScope(val elseLabel: ArmLabel)

context(IfScope, ArmBuilder) fun branchToElse() {
    branch(elseLabel)
}

class IfCall(val name: String, val endOfTruthBlock: ArmBuilder)
class ElseIfCall(val latestIfCall: IfCall, val builder: ArmBuilder)

context(ArmBuilder) fun If(
    condition: ArmValueOperand, name: String, block: context(IfScope) ArmBuilder.() -> Unit
): IfCall {
    val endOfTruthBlock: ArmBuilder
    +buildArm {
        val conditionalSubroutine = if (condition is ArmConditionalSubroutineBuilder) condition else condition `!=` 0.c
        conditionalSubroutine.armName = name
        call(conditionalSubroutine.also {
            withinBuilder(conditionalSubroutine.truthBlock) {
                block(IfScope(conditionalSubroutine.end), this)
                endOfTruthBlock = buildArm { }.also { +it }
            }
        })
    }
    return IfCall(name, endOfTruthBlock)
}

context(ArmBuilder) infix fun IfCall.Else(block: ArmBuilder.() -> Unit) = +buildArm {
    val endIf by label(name + "_end")
    endOfTruthBlock.instructions.add(BranchOpCode(endIf))
    block()
    label(endIf)
}

context(ArmBuilder) infix fun IfCall.Else(other: IfCall) = ElseIfCall(other, buildArm {
    val endIf by label(name + "_end")
    endOfTruthBlock.instructions.add(BranchOpCode(endIf))
    label(endIf)
}.also { +it })

context(ArmBuilder) infix fun ElseIfCall.Else(other: IfCall) = ElseIfCall(other, buildArm {
    val endIf by (builder.instructions.single() as LabelOpCode).label
    builder.instructions.clear()
    latestIfCall.endOfTruthBlock.instructions.add(BranchOpCode(endIf))
    +(endIf.position!!)
}.also { +it })

context(ArmBuilder) infix fun ElseIfCall.Else(block: ArmBuilder.() -> Unit) = +buildArm {
    val endIf by (builder.instructions.single() as LabelOpCode).label
    builder.instructions.clear()
    latestIfCall.endOfTruthBlock.instructions.add(BranchOpCode(endIf))
    block()
    +(endIf.position!!)
}