package io.github.kyay10.karm

open class ArmConditionalSubroutineBuilder(
    name: String,
    parent: ArmBuilder,
    parameters: Set<ArmRegister>,
    instructions: MutableList<ArmOpCode> = mutableListOf()
) : ArmSubroutineBuilder(name, parent, parameters, instructions) {
    val truthBlock: ArmBuilder = ArmBuilder(parent = this)

    lateinit var negated: ArmConditionalSubroutineBuilder

    fun ArmBuilder.markTruthful() = +truthBlock
}

inline fun ArmBuilder.buildConditionalSubroutine(
    name: String,
    vararg parameters: ArmValueOperand,
    dependencies: Iterable<ArmValueOperand> = emptyList(),
    block: ArmConditionalSubroutineBuilder.(LocalParameterList) -> Unit
): ArmConditionalSubroutineBuilder {
    val parameterList = parameters.asList()
    val builder = ArmConditionalSubroutineBuilder(name, this, parameterList.collectRegisterParameters())
    dependencies.forEach { if (it is ArmRegister) builder.addParameter(it) }
    withinBuilder(builder) { block(parameterList.localized()) }
    builder.negated = ArmConditionalSubroutineBuilder(name, this, builder.parameters)
    withinBuilder(builder.negated) {
        val negated by labelWithPostfix("_negated")
        val copyForNegation = ArmConditionalSubroutineBuilder(name, this, builder.parameters)
        subroutinesDependentOnBaseName[copyForNegation] = "%s"
        withinBuilder(copyForNegation) {
            block(parameterList.localized())
        }
        withinBuilder(copyForNegation.truthBlock) {
            result = 0.c
            branch(negated)
        }
        call(copyForNegation)
        result = 1.c
        markTruthful()
        label(negated)
    }
    builder.negated.negated = builder
    return builder
}

inline fun ArmBuilder.buildNegateableConditionalSubroutine(
    name: String,
    vararg parameters: ArmValueOperand,
    dependencies: Iterable<ArmValueOperand> = emptyList(),
    block: ArmConditionalSubroutineBuilder.(shouldNegate: Boolean, LocalParameterList) -> Unit
): ArmConditionalSubroutineBuilder {
    val parameterList = parameters.asList()
    val builder = ArmConditionalSubroutineBuilder(name, this, parameterList.collectRegisterParameters())
    dependencies.forEach { if (it is ArmRegister) builder.addParameter(it) }
    withinBuilder(builder) {
        block(false, parameterList.localized())
    }
    builder.negated = ArmConditionalSubroutineBuilder(name, this, builder.parameters)
    withinBuilder(builder.negated) {
        block(true, parameterList.localized())
    }
    println(builder.parameters)
    builder.negated.negated = builder
    return builder
}

context(ArmBuilder) operator fun ArmConstant.not(): ArmConstant = if (value == 0) 1.c else 0.c
context(ArmBuilder) operator fun ArmConditionalSubroutineBuilder.not(): ArmConditionalSubroutineBuilder = negated
context(ArmBuilder) operator fun ArmValueOperand.not(): ArmValueOperand = when (this) {
    is ArmConstant -> !this
    is ArmConditionalSubroutineBuilder -> !this
    else -> !this.bool
}

context(ArmBuilder) val ArmValueOperand.bool: ArmValueOperand
    get() = when (this) {
        is ArmConstant -> constant(if (value == 0) 0 else 1)
        is ArmConditionalSubroutineBuilder -> this
        else -> this `!=` 0.c
    }

context(ArmBuilder) fun bool(operand: ArmValueOperand) = operand.bool