package io.github.kyay10.karm

import io.github.kyay10.prettifykotlin.Pretty

// step: given the value of the current index, calculate the next index
data class ArmProgression(
    val start: ArmValueOperand,
    val endInclusive: ArmValueOperand,
    val isIncreasingStep: Boolean = true,
    val step: context(ArmBuilder) (ArmValueOperand) -> ArmValueOperand
) {
    context(ArmBuilder)
            operator fun iterator(): ArmProgressionIterator {
        val indexRegister = register()
        return ArmProgressionIterator(
            start, endInclusive, step(given(), indexRegister), indexRegister, isIncreasingStep
        )
    }
}

data class ArmProgressionIterator(
    val start: ArmValueOperand,
    val endInclusive: ArmValueOperand,
    val step: ArmValueOperand,
    val indexRegister: ArmRegister,
    val isIncreasingStep: Boolean = true
) {
    private val isFirstRun get() = !this::initializationAndConditionCheckBuilder.isInitialized
    lateinit var initializationAndConditionCheckBuilder: ArmBuilder

    val loopStart by labeled
    val loopEnd by labeled

    context(ArmBuilder) operator fun hasNext(): Boolean {
        return if (isFirstRun) {
            initializationAndConditionCheckBuilder = buildArm {
                addInitializationAndConditionCheck(loopStart, loopEnd)
            }.also { +it }
            true
        } else {
            val lastInstruction = instructions.last()
            val loopEnd = if (lastInstruction is ArmRepeatableBlockBuilder) {
                initializationAndConditionCheckBuilder.instructions.clear()
                withinBuilder(initializationAndConditionCheckBuilder) {
                    loopStart.unlabel()
                    addInitializationAndConditionCheck(loopStart, lastInstruction.blockEnd)
                }
                loopStart.armName = lastInstruction.blockStart.armName
                lastInstruction.blockStart.armName += "_incrementer"
                lastInstruction.blockEnd
            } else loopEnd
            +buildArm {
                if (lastInstruction is ArmRepeatableBlockBuilder) label(lastInstruction.blockStart)
                indexRegister arrowAssign step
                branch(loopStart)
                label(loopEnd)
            }
            false
        }
    }

    private fun ArmBuilder.addInitializationAndConditionCheck(
        loopStart: ArmLabel, loopEnd: ArmLabel
    ) {
        indexRegister arrowAssign start
        label(loopStart)
        compare(indexRegister, endInclusive)
        branch(loopEnd, BranchCondition.LessThan.replaceIf(isIncreasingStep, BranchCondition::flipped))
    }

    context(ArmBuilder) operator fun next(): ArmValueOperand = indexRegister

}

context(ArmBuilder)
fun ArmProgression.forEach(
    name: String, action: context(BreakScope, ContinueScope) ArmBuilder.(ArmValueOperand) -> Unit
) {
    for (element in this) loop(name) { action(given<BreakScope>(), given<ContinueScope>(), this, element) }
}

context(ArmBuilder)
        operator fun ArmValueOperand.rangeTo(other: ArmValueOperand): ArmProgression {
    return ArmProgression(this, other, isIncreasingStep = true) { it + 1.c }
}

context(ArmBuilder)
        operator fun ArmValueOperand.rangeUntil(other: ArmValueOperand): ArmProgression {
    return ArmProgression(this, other - 1.c, isIncreasingStep = true) { it + 1.c }
}

context(ArmBuilder)
        infix fun ArmValueOperand.downTo(other: ArmValueOperand): ArmProgression {
    return ArmProgression(this, other, isIncreasingStep = false) { it - 1.c }
}

context(ArmBuilder)
        infix fun ArmValueOperand.downUntil(other: ArmValueOperand): ArmProgression {
    return ArmProgression(this, other + 1.c, isIncreasingStep = false) { it - 1.c }
}

infix fun ArmProgression.step(block: context(ArmBuilder) (ArmValueOperand) -> ArmValueOperand) =
    copy(step = block, isIncreasingStep = true)

infix fun ArmProgression.stepDown(block: context(ArmBuilder) (ArmValueOperand) -> ArmValueOperand) =
    copy(step = block, isIncreasingStep = false)

@Pretty(">>")
infix fun ArmProgression.shr(amount: ArmValueOperand) = this stepDown { it shr amount }
@Pretty("<<")
infix fun ArmProgression.shl(amount: ArmValueOperand) = this step { it shl amount }
