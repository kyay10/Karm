package io.github.kyay10.karm

fun ArmBuilder.compare(first: ArmValueOperand, second: ArmValueOperand): ArmOpCode = when (first) {
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

class ComparisonScope(name: String, parent: ArmBuilder) {
    var lessThan: ArmLabel? = null
    var greaterThan: ArmLabel? = null
    var equal: ArmLabel? = null
    var notEqual: ArmLabel? = null
    val subroutineBuilder = ArmSubroutineBuilder(
        name, parent, parent.usedRegisters.toSet()
    ) //TODO maybe we can allow use of used registers here

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

/*    fun Else(name: String, block: ArmBuilder.() -> Unit) {
        val label = label(name)
        lessThan = lessThan ?: label
        greaterThan = greaterThan ?: label
        equal = equal ?: label
        notEqual = notEqual ?: label
        subroutineBuilder.apply {
            +buildArm {
                label(label)
                block()
                Return()
            }
        }
    }*/
}

context(ArmBuilder) fun ArmValueOperand.compare(
    other: ArmValueOperand, name: String, block: ComparisonScope.() -> Unit
): ArmBlockOpCode = with(ComparisonScope(name, this@ArmBuilder)) {
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
                this@buildArm.Return()
            }
        }
        call(subroutineBuilder)
    }
}

context(ArmBuilder) fun ArmValueOperand.compareSingle(other: ArmValueOperand, condition: BranchCondition) =
    buildNegateableConditionalSubroutine("comparison", dependencies = listOf(this, other)) { shouldNegate, _ ->
        result = 0.c
        labelsDependentOnBaseName[end] = "%s_false"
        refreshLabels()
        compare(this@compareSingle, other)
        branch(end, condition.negateIf(!shouldNegate))
        result = 1.c
        markTruthful()
    }

context(ArmBuilder) infix fun ArmValueOperand.lessThan(other: ArmValueOperand) =
    compareSingle(other, BranchCondition.LessThan)
        .also {
            it.baseName = "lessThan"
            it.negated.baseName = "greaterThanOrEqual"
        }
/*buildNegateableConditionalSubroutine("lessThan") { shouldNegate ->
    result = 0.c
    this@lessThan.compare(other, "lessThan") {
        val label = makeLabelForComparison(if (shouldNegate) "greaterThanOrEqual" else "lessThan") {
            result = 1.c
            markTruthful()
        }
        if (shouldNegate) {
            greaterThan = label
            equal = label
        } else {
            lessThan = label
        }
        labelsDependentOnBaseName[label] = "%s"
        subroutinesDependentOnBaseName[subroutineBuilder] = "%s"
    }
}*/

context(ArmBuilder) infix fun ArmValueOperand.lessThanOrEqual(other: ArmValueOperand) =
    compareSingle(other, BranchCondition.LessThanOrEqual)
        .also {
            it.baseName = "lessThanOrEqual"
            it.negated.baseName = "greaterThan"
        }
/*buildNegateableConditionalSubroutine("lessThanOrEqual") { shouldNegate ->
    result = 0.c
    this@lessThanOrEqual.compare(other, "lessThanOrEqual") {
        val label = makeLabelForComparison(if (shouldNegate) "greaterThan" else "lessThanOrEqual") {
            result = 1.c
            markTruthful()
        }
        if (shouldNegate) {
            greaterThan = label
        } else {
            lessThan = label
            equal = label
        }
        labelsDependentOnBaseName[label] = "%s"
        subroutinesDependentOnBaseName[subroutineBuilder] = "%s"
    }
}*/

context(ArmBuilder) infix fun ArmValueOperand.greaterThan(other: ArmValueOperand) = !lessThanOrEqual(other)
/*buildNegateableConditionalSubroutine("greaterThan") { shouldNegate ->
    result = 0.c
    this@greaterThan.compare(other, "greaterThan") {
        val label = makeLabelForComparison(if (shouldNegate) "lessThanOrEqual" else "greaterThan") {
            result = 1.c
            markTruthful()
        }
        if (shouldNegate) {
            lessThan = label
            equal = label
        } else {
            greaterThan = label
        }
        labelsDependentOnBaseName[label] = "%s"
        subroutinesDependentOnBaseName[subroutineBuilder] = "%s"
    }
}*/

context(ArmBuilder) infix fun ArmValueOperand.greaterThanOrEqual(other: ArmValueOperand) = !lessThan(other)
/*buildNegateableConditionalSubroutine("greaterThanOrEqual") { shouldNegate ->
    result = 0.c
    this@greaterThanOrEqual.compare(other, "greaterThanOrEqual") {
        val label = makeLabelForComparison(if (shouldNegate) "lessThan" else "greaterThanOrEqual") {
            result = 1.c
            markTruthful()
        }
        if (shouldNegate) {
            lessThan = label
        } else {
            greaterThan = label
            equal = label
        }
        labelsDependentOnBaseName[label] = "%s"
        subroutinesDependentOnBaseName[subroutineBuilder] = "%s"
    }
}*/

context(ArmBuilder) infix fun ArmValueOperand.equal(other: ArmValueOperand) =
    compareSingle(other, BranchCondition.Equal)
        .also {
            it.baseName = "areEqual"
            it.negated.baseName = "areNotEqual"
        }
/*buildNegateableConditionalSubroutine("equal") { shouldNegate ->
    result = 0.c
    this@equal.compare(other, "equal") {
        val label = makeLabelForComparison(if (shouldNegate) "areNotEqual" else "areEqual") {
            result = 1.c
            markTruthful()
        }
        if (shouldNegate) {
            notEqual = label
        } else {
            equal = label
        }
        labelsDependentOnBaseName[label] = "%s"
        subroutinesDependentOnBaseName[subroutineBuilder] = "%s"
    }
}*/

context(ArmBuilder) infix fun ArmValueOperand.notEqual(other: ArmValueOperand) = !equal(other)
/*buildNegateableConditionalSubroutine("equal") { shouldNegate ->
    result = 0.c
    this@notEqual.compare(other, "equal") {
        val label = makeLabelForComparison(if (shouldNegate) "areEqual" else "areNotEqual") {
            result = 1.c
            markTruthful()
        }
        if (shouldNegate) {
            equal = label
        } else {
            notEqual = label
        }
        labelsDependentOnBaseName[label] = "%s"
        subroutinesDependentOnBaseName[subroutineBuilder] = "%s"
    }
}*/

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