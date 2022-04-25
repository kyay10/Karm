package io.github.kyay10.karm

import java.util.*


const val AVAILABLE_MEMORY = 200

sealed interface ArmElement {
    val armName: String

}

sealed interface ArmOpCode : ArmElement {
    val operands: List<ArmOperand>
}

sealed interface ArmBlockOpCode : ArmOpCode {
    val instructions: List<ArmOpCode>

    override val operands get() = emptyList<ArmOperand>()
}

sealed interface ArmOperand : ArmElement
sealed interface ArmValueOperand : ArmOperand
sealed interface ArmTransferableValueOperand : ArmValueOperand
sealed interface ArmStorageOperand : ArmTransferableValueOperand
sealed interface ArmAccessibleValueOperand : ArmValueOperand
sealed interface ArmRegisterOrConstant : ArmAccessibleValueOperand, ArmTransferableValueOperand
sealed interface ArmCalculationOperand : ArmAccessibleValueOperand {
    val parameters: Set<ArmRegister>
    fun storeInto(register: ArmRegister, parent: ArmBuilder): ArmOpCode
    fun callIn(parent: ArmBuilder): ArmOpCode = storeInto(RNull, parent)
}

sealed interface ArmSubroutine : ArmCalculationOperand {
    val instructions: List<ArmOpCode>
    val internalRegisters: Set<RTemp>

    override fun storeInto(register: ArmRegister, parent: ArmBuilder): ArmBlockOpCode
    override fun callIn(parent: ArmBuilder): ArmBlockOpCode = storeInto(RNull, parent)
}

sealed class ArmRegister(open val index: Int) : ArmRegisterOrConstant, ArmStorageOperand {
    override val armName get() = "R$index"
}

object R0 : ArmRegister(0)
object R1 : ArmRegister(1)
object R2 : ArmRegister(2)
object R3 : ArmRegister(3)
object R4 : ArmRegister(4)
object R5 : ArmRegister(5)
object R6 : ArmRegister(6)
object R7 : ArmRegister(7)
object R8 : ArmRegister(8)
object R9 : ArmRegister(9)
object R10 : ArmRegister(10)
object R11 : ArmRegister(11)
object R12 : ArmRegister(12)
object RNull : ArmRegister(Int.MIN_VALUE)
data class RCustom(override val index: Int) : ArmRegister(index)

// The identity of this class matters, so it shouldn't be data
class RTemp(var underlying: ArmRegister) : ArmRegister(underlying.index) {
    override val index: Int
        get() = underlying.index
}

tailrec fun RTemp.computeTrueUnderlying(): ArmRegister = when (val underlying = underlying) {
    is RTemp -> underlying.computeTrueUnderlying()
    else -> underlying
}

val builtInRegisters = listOf(R0, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12)

data class ArmConstant(val value: Int) : ArmRegisterOrConstant {
    override val armName = "#$value"
}

// The identity of this class matters, so it shouldn't be data
class ArmLabel(override var armName: String) : ArmOperand {
    constructor(asmName: String, position: LabelOpCode) : this(asmName) {
        this.position = position
    }

    private var _position: LabelOpCode? = null
    var position: LabelOpCode? = _position
        set(value) {
            _position = field?.let {
                throw IllegalStateException("Labels shall be defined only in one place")
            } ?: value
        }
        get() = _position

    fun unlabel() {
        _position = null
    }
}

class LabelOpCode(val label: ArmLabel) : ArmOpCode {
    init {
        label.position = this
    }

    override val operands = listOf(label)
    override val armName = "LABEL $label"
}

sealed class ArmMemoryAddress(open val address: Int) : ArmStorageOperand {
    override val armName get() = "$address"
}

data class MemoryAddress(override val address: Int) : ArmMemoryAddress(address)

// The identity of this class matters, so it shouldn't be data
class TemporaryAddress(override var address: Int) : ArmMemoryAddress(address)

data class LoadRegisterOpCode(val into: ArmRegister, val fromMemory: ArmMemoryAddress) : ArmOpCode {
    override val armName = "LDR"
    override val operands = listOf(into, fromMemory)
}

data class StoreRegisterOpCode(val from: ArmRegister, val intoMemory: ArmMemoryAddress) : ArmOpCode {
    override val armName = "STR"
    override val operands = listOf(from, intoMemory)
}

data class AddOpCode(val into: ArmRegister, val first: ArmRegister, val second: ArmRegisterOrConstant) : ArmOpCode {
    override val armName = "ADD"
    override val operands = listOf(into, first, second)
}

data class SubtractOpCode(val into: ArmRegister, val first: ArmRegister, val second: ArmRegisterOrConstant) :
    ArmOpCode {
    override val armName = "SUB"
    override val operands = listOf(into, first, second)
}

data class MoveOpCode(val into: ArmRegister, val from: ArmRegisterOrConstant, val isNot: Boolean = false) : ArmOpCode {
    override val armName = if (isNot) "MVN" else "MOV"
    override val operands = listOf(into, from)
}

data class CompareOpCode(val thisValue: ArmRegister, val otherValue: ArmRegisterOrConstant) : ArmOpCode {
    override val armName = "CMP"
    override val operands = listOf(thisValue, otherValue)
}

data class BranchOpCode(val label: ArmLabel, val condition: BranchCondition = BranchCondition.Always) : ArmOpCode {
    override val armName = "B${condition.armName}"
    override val operands = listOf(label)
}

enum class BranchCondition(override val armName: String) : ArmElement {
    Always(""), Never(""), Equal("EQ"), NotEqual("NE"), GreaterThan("GT"), LessThan("LT"), GreaterThanOrEqual("GE"), LessThanOrEqual(
        "LE"
    ), ;

    val decomposed: EnumSet<BranchCondition>
        get() = when (this) {
            Never -> enumSetOf()
            GreaterThanOrEqual -> enumSetOf(GreaterThan, Equal)
            LessThanOrEqual -> enumSetOf(LessThan, Equal)
            else -> enumSet
        }

    val negated
        get(): BranchCondition = when (this) {
            Always -> Never
            Never -> Always
            Equal -> NotEqual
            NotEqual -> Equal
            GreaterThan -> LessThanOrEqual
            LessThan -> GreaterThanOrEqual
            GreaterThanOrEqual -> LessThan
            LessThanOrEqual -> GreaterThan
        }

    /**
     * Flips the condition so that numbers less than in become greater than it and vice versa.
     * Doesn't affect equality.
     */
    val flipped
        get(): BranchCondition = when (this) {
            Always, Never, Equal, NotEqual -> this
            GreaterThan -> LessThan
            LessThan -> GreaterThan
            GreaterThanOrEqual -> LessThanOrEqual
            LessThanOrEqual -> GreaterThanOrEqual
        }

    infix fun or(other: BranchCondition): BranchCondition = when (this) {
        other -> this
        Always -> Always
        Never -> other
        Equal -> when (other) {
            NotEqual -> Always
            GreaterThan -> GreaterThanOrEqual
            LessThan -> LessThanOrEqual
            GreaterThanOrEqual, LessThanOrEqual -> other
            Always, Never, Equal -> other or this
        }
        NotEqual -> when (other) {
            GreaterThan, LessThan -> NotEqual
            GreaterThanOrEqual, LessThanOrEqual -> Always
            Always, Never, Equal, NotEqual -> other or this
        }
        GreaterThan -> when (other) {
            LessThan -> NotEqual
            GreaterThanOrEqual -> GreaterThanOrEqual
            LessThanOrEqual -> Always
            Always, Never, Equal, NotEqual, GreaterThan -> other or this
        }
        LessThan -> when (other) {
            GreaterThanOrEqual -> Always
            LessThanOrEqual -> LessThanOrEqual
            Always, Never, Equal, NotEqual, GreaterThan, LessThan -> other or this
        }
        GreaterThanOrEqual -> when (other) {
            LessThanOrEqual -> Always
            Always, Never, Equal, NotEqual, GreaterThan, LessThan, GreaterThanOrEqual -> other or this
        }
        LessThanOrEqual -> other or this
    }

    infix fun and(other: BranchCondition): BranchCondition = when (this) {
        other -> this
        Always -> other
        Never -> Never
        Equal -> when (other) {
            NotEqual, GreaterThan, LessThan -> Never
            GreaterThanOrEqual, LessThanOrEqual -> Equal
            Always, Never, Equal -> other and this
        }
        NotEqual -> when (other) {
            GreaterThan, LessThan -> other
            GreaterThanOrEqual -> GreaterThan
            LessThanOrEqual -> LessThan
            Always, Never, Equal, NotEqual -> other and this
        }
        GreaterThan -> when (other) {
            LessThan -> Never
            GreaterThanOrEqual -> GreaterThan
            LessThanOrEqual -> Never
            Always, Never, Equal, NotEqual, GreaterThan -> other and this
        }
        LessThan -> when (other) {
            GreaterThanOrEqual -> Never
            LessThanOrEqual -> LessThan
            Always, Never, Equal, NotEqual, GreaterThan, LessThan -> other and this
        }
        GreaterThanOrEqual -> when (other) {
            LessThanOrEqual -> Equal
            Always, Never, Equal, NotEqual, GreaterThan, LessThan, GreaterThanOrEqual -> other and this
        }
        LessThanOrEqual -> other and this
    }
}

fun BranchCondition.negateIf(condition: Boolean) = if (condition) this.negated else this

fun <E : Enum<E>> enumSetOf(vararg elements: E): EnumSet<E> = EnumSet.copyOf(elements.asList())
val <E : Enum<E>> E.enumSet: EnumSet<E> get() = EnumSet.of(this)

data class LogicalOpCode(
    val operation: LogicalOperation, val into: ArmRegister, val first: ArmRegister, val second: ArmRegisterOrConstant
) : ArmOpCode {
    override val armName = operation.armName
    override val operands = listOf(into, first, second)
}

enum class LogicalOperation(override val armName: String) : ArmElement {
    And("AND"), Or("ORR"), ExclusiveOr("EOR"), ShiftLeft("LSL"), ShiftRight("LSR");

    val isCommutative
        get() = when (this) {
            And, Or, ExclusiveOr -> true
            else -> false
        }
}

object HaltOpCode : ArmOpCode {
    override val armName = "HALT"
    override val operands = emptyList<ArmOperand>()
}

data class AddCalculation(val first: ArmRegister, val second: ArmRegisterOrConstant) : ArmCalculationOperand {
    override val armName = "+"
    override val parameters = setOfIsInstance<ArmRegister>(first, second)

    override fun storeInto(register: ArmRegister, parent: ArmBuilder) = AddOpCode(register, first, second)
}

data class SubtractCalculation(val first: ArmRegister, val second: ArmRegisterOrConstant) : ArmCalculationOperand {
    override val armName = "-"
    override val parameters = setOfIsInstance<ArmRegister>(first, second)

    override fun storeInto(register: ArmRegister, parent: ArmBuilder) = SubtractOpCode(register, first, second)
}

data class NotCalculation(val value: ArmRegisterOrConstant) : ArmCalculationOperand {
    override val armName = "!"
    override val parameters = setOfIsInstance<ArmRegister>(value)

    override fun storeInto(register: ArmRegister, parent: ArmBuilder) = MoveOpCode(register, value, isNot = true)
}

data class LogicalCalculation(
    val operation: LogicalOperation, val first: ArmRegister, val second: ArmRegisterOrConstant
) : ArmCalculationOperand {
    override val armName = when (operation) {
        LogicalOperation.And -> "&"
        LogicalOperation.Or -> "|"
        LogicalOperation.ExclusiveOr -> "^"
        LogicalOperation.ShiftLeft -> "<<"
        LogicalOperation.ShiftRight -> ">>"
    }
    override val parameters = setOfIsInstance<ArmRegister>(first, second)

    override fun storeInto(register: ArmRegister, parent: ArmBuilder) =
        LogicalOpCode(operation, register, first, second)
}