package io.github.kyay10.karm

@JvmInline
value class BreakScope(val breakLabel: ArmLabel)

context(BreakScope, ArmBuilder) fun Break() = branch(breakLabel)

@JvmInline
value class ContinueScope(val continueLabel: ArmLabel)

context(ContinueScope, ArmBuilder) fun Continue() = branch(continueLabel)

context(ArmBuilder) fun While(
    condition: ArmValueOperand, name: String, block: context(BreakScope, ContinueScope) ArmBuilder.() -> Unit
): ArmOpCode = +buildArm {
    val loopStart by label(name)
    label(loopStart)
    If(condition, name) {
        block(BreakScope(elseLabel), ContinueScope(loopStart), this)
        branch(loopStart)
    }
}

context(ArmBuilder) fun For(
    initializer: ArmValueOperand? = null,
    condition: ArmBuilder.(ArmValueOperand) -> ArmValueOperand,
    incrementer: ArmBuilder.(ArmRegister) -> Unit,
    name: String,
    block: context(BreakScope, ContinueScope) ArmBuilder.(ArmValueOperand) -> Unit
): ArmOpCode = +buildArm {
    val index = register(initializer)
    val incrementerLabel by label(name + "_incrementer")
    While(condition(this, index), name) {
        block(given<BreakScope>(), ContinueScope(incrementerLabel), this, index)
        label(incrementerLabel)
        incrementer(this, index)
    }
}

class ArmRepeatableBlockBuilder(
    name: String,
    parent: ArmBuilder,
    instructions: MutableList<ArmOpCode> = mutableListOf()
) : ArmBuilder(instructions, parent) {
    val blockStart by label(name)
    val blockEnd by label(name + "_end")
}

context(ArmBuilder) fun loop(name: String, block: context(BreakScope, ContinueScope) ArmBuilder.() -> Unit): ArmOpCode {
    val repeatableBlock = ArmRepeatableBlockBuilder(name, given())
    withinBuilder(repeatableBlock) {
        block(BreakScope(blockEnd), ContinueScope(blockStart), this)
    }
    return +repeatableBlock
}