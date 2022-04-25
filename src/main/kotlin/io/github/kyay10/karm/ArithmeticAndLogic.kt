package io.github.kyay10.karm

//TODO: Change subroutines to not expose return value and instead just allow usage of the storage register if and only
// if it isn't a parameter
context(ArmBuilder) operator fun ArmValueOperand.plus(other: ArmValueOperand): ArmValueOperand = when (this) {
    is ArmRegister -> when (other) {
        is ArmRegisterOrConstant -> AddCalculation(this, other)
        is ArmMemoryAddress, is ArmCalculationOperand -> buildSubroutine(
            "registerPlusCalc", this, other
        ) { (first, second) ->
            // For memory, this could be R1 = R1 + memory(x)
            // And so, we have to store the memory value somewhere other than the final register
            // For calculations, a calculation can include the return value in a variety of places
            // And so it isn't safe to just use the return value as a temporary register
            // since the first operand can also be the return value
            // e.g. this could be R1 = R1 + (R1 + R1)
            // In that case, storing (R1 + R1) into R1 will cause the wrong result, and storing R1 into R1 doesn't
            // change much
            result = first + second
        }
    }
    is ArmConstant -> when (other) {
        is ArmConstant -> constant(this.value + other.value)
        is ArmRegister -> other + this
        is ArmMemoryAddress, is ArmCalculationOperand -> buildSubroutine("constantPlusCalc") {
            result = other
            result += this@plus
        }
    }
    is ArmMemoryAddress -> when (other) {
        is ArmRegisterOrConstant -> other + this
        is ArmMemoryAddress -> buildSubroutine("memoryPlusMemory") {
            result = this@plus
            result += other
        }
        is ArmCalculationOperand -> buildSubroutine("memoryPlusCalc", other) { (second) ->
            // Calculation can depend on returnValue AND the temporary register
            // so evaluate it first before you evaluate the memory call
            result = this@plus
            result += second
        }
    }
    is ArmCalculationOperand -> when (other) {
        is ArmRegisterOrConstant, is ArmMemoryAddress -> other + this
        is ArmCalculationOperand -> buildSubroutine("calcPlusCalc", this) { (first) ->
            // R1 = (R1 + R2) + (R1 + R2)
            // R2 could be chosen as temporary for the first (R1 + R2), which then messes up the result for the
            // second one.
            // We need to ensure a completely independent storage from the second calculation so that we can store
            // the first calculation inside of it, or vice versa
            // Also, respect order of operation
            result = other
            result += first
        }
    }
}

context(ArmBuilder) operator fun ArmValueOperand.minus(other: ArmValueOperand): ArmValueOperand = when (this) {
    is ArmRegister -> when (other) {
        is ArmRegisterOrConstant -> SubtractCalculation(this, other)
        is ArmMemoryAddress, is ArmCalculationOperand -> buildSubroutine(
            "registerMinusCalc", this, other
        ) { (first, second) ->
            // For memory, this could be R1 = R1 - memory(x)
            // And so, we have to store the memory value somewhere other than the final register
            // For calculations, a calculation can include the return value in a variety of places
            // And so it isn't safe to just use the return value as a temporary register
            // since the first operand can also be the return value
            // e.g. this could be R1 = R1 - (R1 + R1)
            // In that case, storing (R1 + R1) into R1 will cause the wrong result, and storing R1 into R1 doesn't
            // change much
            result = first - second
        }
    }
    is ArmConstant -> when (other) {
        is ArmConstant -> constant(this.value - other.value)
        is ArmRegister -> -(other - this)
        is ArmMemoryAddress, is ArmCalculationOperand -> -buildSubroutine("constantMinusCalc") {
            result = other
            result -= this@minus
        }
    }
    is ArmMemoryAddress -> when (other) {
        is ArmConstant -> -(other - this)
        is ArmRegister -> buildSubroutine("memoryMinusRegister", this, other) { (first, second) ->
            // This could be R1 = memory(y) - R1
            result = first - second
        }
        is ArmMemoryAddress -> buildSubroutine("memoryMinusMemory") {
            result = this@minus
            result -= other
        }
        is ArmCalculationOperand -> buildSubroutine("memoryMinusCalc", other) { (second) ->
            // Calculation can depend on returnValue AND the temporary register
            // so evaluate it first before you evaluate the memory call
            result = this@minus
            result -= second
        }
    }
    is ArmCalculationOperand -> when (other) {
        is ArmConstant -> -(other - this)
        is ArmRegister -> buildSubroutine("calcMinusRegister", this, other) { (first, second) ->
            // a calculation can include the return value in a variety of places
            // And so it isn't safe to just use the return value as a temporary register
            // since the first operand can also be the return value
            // e.g. this could be R1 = (R1 + R1) - R1
            // In that case, storing (R1 + R1) into R1 will cause the wrong result, and storing R1 into R1 doesn't
            // change much
            result = first - second
        }
        is ArmMemoryAddress -> buildSubroutine("calcMinusMemory", this) { (first) ->
            // Calculation can depend on returnValue AND the temporary register
            // so evaluate it first before you evaluate the memory call
            result = other
            result = first - result
        }
        is ArmCalculationOperand -> buildSubroutine("calcMinusCalc", this) { (first) ->
            // R1 = (R1 + R2) - (R1 - R2)
            // R2 could be chosen as temporary for the first (R1 + R2), which then messes up the result for the
            // second one.
            // We need to ensure a completely independent storage from the second calculation so that we can store
            // the first calculation inside of it, or vice versa
            // Also, respect order of operation
            result = other
            result = first - result
        }
    }
}

context(ArmBuilder) fun ArmValueOperand.logical(other: ArmValueOperand, type: LogicalOperation): ArmValueOperand =
    when (this) {
        is ArmRegister -> when (other) {
            is ArmRegisterOrConstant -> LogicalCalculation(type, this, other)
            is ArmMemoryAddress, is ArmCalculationOperand -> buildSubroutine(
                "registerLogicalCalc", this, other
            ) { (first, second) ->
                result = first.logical(second, type)
            }
        }
        is ArmConstant -> when (other) {
            is ArmConstant -> constant(
                when (type) {
                    LogicalOperation.And -> this.value and other.value
                    LogicalOperation.Or -> this.value or other.value
                    LogicalOperation.ExclusiveOr -> this.value xor other.value
                    LogicalOperation.ShiftRight -> this.value shr other.value
                    LogicalOperation.ShiftLeft -> this.value shl other.value
                }
            )
            is ArmRegister -> when {
                type.isCommutative -> other.logical(this, type)
                else -> buildSubroutine("constantLogicalRegister", other) {
                    val temp by register(this@logical)
                    result = temp.logical(other, type)
                }
            }
            is ArmMemoryAddress, is ArmCalculationOperand -> when {
                type.isCommutative -> buildSubroutine("constantLogicalCalcFlipped") {
                    result = other
                    result = result.logical(this@logical, type)
                }
                else -> buildSubroutine("constantLogicalCalc", other) { (second) ->
                    result = this@logical
                    result = result.logical(second, type)
                }
            }
        }
        is ArmMemoryAddress -> when (other) {
            is ArmRegisterOrConstant -> when {
                type.isCommutative -> other.logical(this, type)
                else -> when (other) {
                    is ArmConstant -> buildSubroutine("memoryLogicalConstant") {
                        result = this@logical
                        result = result.logical(other, type)
                    }
                    is ArmRegister -> buildSubroutine("memoryLogicalRegister", this, other) { (first) ->
                        result = first.logical(other, type)
                    }
                }
            }
            is ArmMemoryAddress -> buildSubroutine("memoryLogicalMemory") {
                result = this@logical
                result = result.logical(other, type)
            }
            is ArmCalculationOperand -> buildSubroutine("memoryLogicalCalc", other) { (second) ->
                // Calculation can depend on returnValue AND the temporary register
                // so evaluate it first before you evaluate the memory call
                result = this@logical
                result = result.logical(second, type)
            }
        }
        is ArmCalculationOperand -> when (other) {
            is ArmConstant -> if (type.isCommutative) other.logical(
                this, type
            ) else buildSubroutine("calcLogicalConstant") {
                result = this@logical
                result = result.logical(other, type)
            }
            is ArmRegister -> if (type.isCommutative) other.logical(
                this, type
            ) else buildSubroutine("calcLogicalRegister", this, other) { (first, second) ->
                result = first.logical(second, type)
            }
            is ArmMemoryAddress -> if (type.isCommutative) other.logical(
                this, type
            ) else buildSubroutine("calcLogicalMemory", this) { (first) ->
                result = other
                result = first.logical(result, type)
            }
            is ArmCalculationOperand -> buildSubroutine("calcLogicalCalc", this) { (first) ->
                // We need to ensure a completely independent storage from the second calculation so that we can store
                // the first calculation inside of it, or vice versa
                // Also, respect order of operation
                result = other
                result = first.logical(result, type)
            }
        }
    }

context(ArmBuilder) infix fun ArmValueOperand.and(other: ArmValueOperand) = logical(other, LogicalOperation.And)
context(ArmBuilder) infix fun ArmValueOperand.or(other: ArmValueOperand) = logical(other, LogicalOperation.Or)
context(ArmBuilder) infix fun ArmValueOperand.xor(other: ArmValueOperand) = logical(other, LogicalOperation.ExclusiveOr)
context(ArmBuilder) infix fun ArmValueOperand.shl(other: ArmValueOperand) = logical(other, LogicalOperation.ShiftLeft)
context(ArmBuilder) infix fun ArmValueOperand.shr(other: ArmValueOperand) = logical(other, LogicalOperation.ShiftRight)

context(ArmBuilder) infix fun ArmValueOperand.`&`(other: ArmValueOperand) = this and other

context(ArmBuilder)
        @JvmName("_or")
        infix fun ArmValueOperand.`|`(other: ArmValueOperand) = this or other

context(ArmBuilder) infix fun ArmValueOperand.`^`(other: ArmValueOperand) = this xor other

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("shiftLeft")
        infix fun ArmValueOperand.`<<`(other: ArmValueOperand) = this shl other

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("shiftRight")
        infix fun ArmValueOperand.`>>`(other: ArmValueOperand) = this shr other

context(ArmBuilder) fun ArmValueOperand.inv(): ArmValueOperand = when (this) {
    is ArmConstant -> constant(this.value.inv())
    is ArmRegister -> NotCalculation(this)
    is ArmMemoryAddress, is ArmCalculationOperand -> buildSubroutine("notCalc") {
        result = this@inv
        result = result.inv()
    }
}

context(ArmBuilder) val ArmValueOperand.`~` get() = inv()
context(ArmBuilder) fun `~`(value: ArmValueOperand) = value.`~`

context(ArmBuilder) operator fun ArmValueOperand.unaryMinus(): ArmValueOperand = when (this) {
    is ArmConstant -> constant(-value)
    is ArmRegister, is ArmMemoryAddress, is ArmCalculationOperand -> buildSubroutine("minusNonConstant") {
        result = `~`(this@unaryMinus)
        result += 1.c
    }
}