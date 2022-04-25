package io.github.kyay10.karm

fun ArmBuilder.compare(first: ArmValueOperand, second: ArmValueOperand) {
    when (first) {
        is ArmRegister -> when (second) {
            is ArmRegisterOrConstant -> compare(first, second)
            is ArmMemoryAddress, is ArmCalculationOperand -> call(buildSubroutine(
                "compareRegisterWithCalc", first, second
            ) { (first, second) ->
                compare(first, second)
            })
        }
        else -> call(buildSubroutine("compareNonRegisterWithOther") {
            // Put anything that ain't a register into one, even constants
            val temp by register(first)
            compare(temp, second)
        })
    }
}

class ComparisonScope(name: String, parent: ArmBuilder) {
    var lessThan: ArmLabel? = null
    var greaterThan: ArmLabel? = null
    var equal: ArmLabel? = null
    var notEqual: ArmLabel? = null
    val subroutineBuilder = ArmSubroutineBuilder(name, parent, setOf())

    fun makeLabelForComparison(name: String, block: ArmBuilder.() -> Unit): ArmLabel {
        val label = label(name)
        subroutineBuilder.apply {
            +buildArm {
                label(label)
                block()
                Return()
            }
        }
        return label
    }

    fun lessThan(name: String, block: ArmBuilder.() -> Unit) {
        lessThan = makeLabelForComparison(name, block)
    }

    fun lessThanOrEqual(name: String, block: ArmBuilder.() -> Unit) {
        lessThan(name, block)
        equal = lessThan
    }

    fun greaterThan(name: String, block: ArmBuilder.() -> Unit) {
        greaterThan = makeLabelForComparison(name, block)
    }

    fun greaterThanOrEqual(name: String, block: ArmBuilder.() -> Unit) {
        greaterThan(name, block)
        equal = greaterThan
    }

    fun equal(name: String, block: ArmBuilder.() -> Unit) {
        equal = makeLabelForComparison(name, block)
    }

    fun notEqual(name: String, block: ArmBuilder.() -> Unit) {
        notEqual = makeLabelForComparison(name, block)
    }
}

context(ArmBuilder) fun ArmValueOperand.compare(
    other: ArmValueOperand, name: String, block: ComparisonScope.() -> Unit
) = with(ComparisonScope(name, given<ArmBuilder>())) {
    withinBuilder(subroutineBuilder) {
        block()
    }
    +buildArm {
        if (this@compare is ArmConstant) {
            // swap order of arguments as an optimization
            compare(other, this@compare)
            lessThan?.let { branchGreaterThan(it) }
            greaterThan?.let { branchLessThan(it) }
        } else {
            compare(this@compare, other)
            lessThan?.let { branchLessThan(it) }
            greaterThan?.let { branchGreaterThan(it) }
        }
        equal?.let { branchEqual(it) }
        notEqual?.let { branchNotEqual(it) }
        if (equal == null || (notEqual == null && (lessThan == null || greaterThan == null))) {
            with(subroutineBuilder) {
                Return()
            }
        }
        call(subroutineBuilder)
    }
}

context(ArmBuilder) fun ArmValueOperand.compareSingle(other: ArmValueOperand, condition: BranchCondition) =
    buildNegateableConditionalSubroutine("comparison") { shouldNegate, _ ->
        result = 0.c
        labelsDependentOnBaseName[end] = "%s_false"
        refreshLabels()
        if (this@compareSingle is ArmConstant) {
            compare(other, this@compareSingle)
            branch(end, condition.negateIf(!shouldNegate).flipped)
        } else {
            compare(this@compareSingle, other)
            branch(end, condition.negateIf(!shouldNegate))
        }
        result = 1.c
        markTruthful()
    }

context(ArmBuilder) infix fun ArmValueOperand.lessThan(other: ArmValueOperand) =
    compareSingle(other, BranchCondition.LessThan).withUpdatedNames("lessThan", "greaterThanOrEqual")

context(ArmBuilder) infix fun ArmValueOperand.lessThanOrEqual(other: ArmValueOperand) =
    compareSingle(other, BranchCondition.LessThanOrEqual).withUpdatedNames("lessThanOrEqual", "greaterThan")

context(ArmBuilder) infix fun ArmValueOperand.greaterThan(other: ArmValueOperand) = !lessThanOrEqual(other)

context(ArmBuilder) infix fun ArmValueOperand.greaterThanOrEqual(other: ArmValueOperand) = !lessThan(other)

context(ArmBuilder) infix fun ArmValueOperand.equal(other: ArmValueOperand) =
    compareSingle(other, BranchCondition.Equal).withUpdatedNames("areEqual", "areNotEqual")

context(ArmBuilder) infix fun ArmValueOperand.notEqual(other: ArmValueOperand) = !equal(other)

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("_lessThan")
        infix fun ArmValueOperand.`<`(other: ArmValueOperand) = this lessThan other

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("_lessThanOrEqual")
        infix fun ArmValueOperand.`<=`(other: ArmValueOperand) = this lessThanOrEqual other

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("_greaterThan")
        infix fun ArmValueOperand.`>`(other: ArmValueOperand) = this greaterThan other

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("_greaterThanOrEqual")
        infix fun ArmValueOperand.`>=`(other: ArmValueOperand) = this greaterThanOrEqual other

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("_equal")
        infix fun ArmValueOperand.`==`(other: ArmValueOperand) = this equal other

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("_notEqual")
        infix fun ArmValueOperand.`!=`(other: ArmValueOperand) = this notEqual other