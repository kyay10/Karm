@file:OptIn(ExperimentalContracts::class)

package io.github.kyay10.karm

import io.github.kyay10.prettifykotlin.Pretty
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@DslMarker
annotation class ArmDsl

//@ArmDsl
open class ArmBuilder(
    override val instructions: MutableList<ArmOpCode> = mutableListOf(), val parent: ArmBuilder? = null
) : ArmBlockOpCode {
    override var armName = ""

    val availableRegisters: MutableSet<ArmRegister> = (parent?.availableRegisters ?: builtInRegisters).toMutableSet()
    val usedRegisters: MutableSet<ArmRegister> = mutableSetOf()

    val temporaryAddresses: MutableSet<TemporaryAddress> = mutableSetOf()
    val childBuilders: MutableSet<ArmBuilder> = mutableSetOf()

    val usedAddressesCount get() = ownAddressesCount + maxChildAddressesCount

    // These are addresses that are available throughout the whole block
    val ownAddressesCount get() = temporaryAddresses.size

    // These are the maximum amount of addresses needed for each sub-block
    val maxChildAddressesCount: Int
        get() = childBuilders.maxOfOrNull { it.usedAddressesCount } ?: 0

    var childDelegateForDelegates: ArmBuilder? = null

    open fun useRegister(register: ArmRegister) {
        markRegister(register)
        removeRegister(register)
    }

    open fun removeRegister(register: ArmRegister) {
        if (availableRegisters.remove(register)) {
            usedRegisters.add(register)
            // If this is a register that we own, then ensure we remove it from being available anywhere inside
            // child builders. This is to fix a bug where ArmConditionalBuilders would receive an extra parameter
            // while building, and so it'd remove a register, but the truthBlock would still have that removed
            // register as available, and so it'd result in those registers not having an underlying value.
            childBuilders.forEach {
                it.removeRegister(register)
            }
        }
    }

    open fun useRegisters(registers: Set<ArmRegister>) {
        markRegisters(registers)
        removeRegisters(registers)
    }

    open fun removeRegisters(registers: Set<ArmRegister>) {
        val ownRegisters = availableRegisters intersect registers
        availableRegisters.removeAll(ownRegisters)
        usedRegisters.addAll(ownRegisters)
        if (ownRegisters.isNotEmpty()) {
            childBuilders.forEach {
                it.removeRegisters(registers)
            }
        }
    }

    open fun markRegister(register: ArmRegister) {
        parent?.markRegister(register)
    }

    open fun markRegisters(registers: Set<ArmRegister>) {
        parent?.markRegisters(registers)
    }

    operator fun <T : ArmOpCode> T.unaryPlus() = addInstruction(this)
    fun <T : ArmOpCode> addInstruction(instruction: T) {
        instructions.add(instruction)
        useRegisters(instruction.operands.filterIsInstance<ArmRegister>().toSet())
        if (instruction is ArmBuilder) {
            childBuilders.add(instruction)
        }
    }
}

open class ArmSubroutineBuilder(
    name: String,
    parent: ArmBuilder,
    parameters: Set<ArmRegister>,
    instructions: MutableList<ArmOpCode> = mutableListOf()
) : ArmBuilder(instructions, parent), ArmSubroutine {
    override val internalRegisters: MutableSet<RTemp> = mutableSetOf()

    private val _parameters = parameters.toMutableSet()
    override val parameters: Set<ArmRegister> = _parameters

    fun addParameter(parameter: ArmRegister) {
        if (_parameters.add(parameter)) useRegister(availableRegisters.first())
    }

    val returnValue = RTemp(RNull)
    var result by returnValue

    val labelsDependentOnBaseName = mutableMapOf<ArmLabel, String>()
    val subroutinesDependentOnBaseName = mutableMapOf<ArmSubroutineBuilder, String>()

    val end by labelWithPostfix("_end")

    override var armName = name
        set(newName) {
            field = newName
            refreshLabels()
        }

    fun refreshLabels() {
        labelsDependentOnBaseName.forEach { (label, format) ->
            label.armName = format.format(armName)
        }
        subroutinesDependentOnBaseName.forEach { (subroutine, format) ->
            subroutine.armName = format.format(armName)
        }
    }

    fun labelWithPostfix(postfix: String): ArmLabel {
        val format = "%s$postfix"
        val label = ArmLabel(format.format(armName))
        labelsDependentOnBaseName[label] = format
        return label
    }

    context(ArmBuilder)
    fun Return(value: ArmValueOperand? = null) {
        if (value != null) result = value
        branch(end)
    }

    init {
        availableRegisters.clear()
        availableRegisters.addAll(builtInRegisters.map { RTemp(RNull) }.drop(parameters.size))
    }

    override fun markRegister(register: ArmRegister) {
        super.markRegister(register)
        if (register in availableRegisters && register is RTemp) internalRegisters.add(register)
        if (register in parent!!.usedRegisters) addParameter(register)
    }

    override fun markRegisters(registers: Set<ArmRegister>) {
        for (register in registers) {
            markRegister(register)
        }
    }

    override fun storeInto(register: ArmRegister, parent: ArmBuilder): ArmBlockOpCode {
        parent.markRegisters(parameters)
        returnValue.underlying = register
        val internalRegistersIterator = internalRegisters.iterator()
        parent.availableRegisters.forEach { availableRegister ->
            if (internalRegistersIterator.hasNext()) {
                internalRegistersIterator.next().underlying = availableRegister
                parent.markRegister(availableRegister)
            }
        }
        val usedRegisters = parent.usedRegisters.filter { it !in parameters && it != register }.iterator()
        val builder = ArmBuilder(instructions.toMutableList(), parent).apply {
            armName = this@ArmSubroutineBuilder.armName
            temporaryAddresses.addAll(temporaryAddresses)
            childBuilders.addAll(childBuilders)
        }
        //builder.ownAddressesCount will automatically increase because of builder.memory()
        //builder.ownAddressesCount += (internalRegisters.size - parent.availableRegisters.size).coerceAtLeast(0)
        for (i in parent.availableRegisters.size until internalRegisters.size) {
            val usedRegister = usedRegisters.next()
            val temporaryAddress = builder.memory()
            builder.instructions.add(
                0, StoreRegisterOpCode(usedRegister, temporaryAddress)
            )
            internalRegistersIterator.next().underlying = usedRegister
            builder.instructions.add(LoadRegisterOpCode(usedRegister, temporaryAddress))
        }
        builder.instructions.add(LabelOpCode(end))
        return builder
    }
}

inline fun <T : ArmBuilder> ArmBuilder.withinBuilder(builder: T, block: T.() -> Unit) {
    contract {
        callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    val previousChild = childDelegateForDelegates
    childDelegateForDelegates = builder
    builder.block()
    childDelegateForDelegates = previousChild
}

inline fun ArmBuilder.buildArm(block: ArmBuilder.() -> Unit): ArmBuilder {
    contract {
        callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    val builder = ArmBuilder(parent = this)
    withinBuilder(builder, block)
    return builder
}

@Pretty("λ")
inline fun ArmBuilder.buildSubroutine(
    name: String, vararg localParameters: ArmValueOperand, block: ArmSubroutineBuilder.(LocalParameterList) -> Unit
): ArmSubroutine {
    contract {
        callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    val parameterList = localParameters.asList()
    val builder = ArmSubroutineBuilder(name, this, parameterList.collectRegisterParameters())
    withinBuilder(builder) { block(parameterList.localized()) }
    return builder
}

@JvmInline
value class LocalParameterList(val parameters: List<ArmTransferableValueOperand>)

fun List<ArmValueOperand>.collectRegisterParameters(): Set<ArmRegister> {
    var isFirstCalculation = true
    return flatMap {
        when (it) {
            is ArmRegister -> listOf(it)
            is ArmCalculationOperand -> if (isFirstCalculation) {
                isFirstCalculation = false
                listOf()
            } else it.parameters
            else -> listOf()
        }
    }.toSet()
}

context(ArmBuilder)
fun List<ArmValueOperand>.localized(): LocalParameterList {
    return LocalParameterList(map {
        when (it) {
            is ArmRegisterOrConstant -> it
            is ArmMemoryAddress -> if (shouldUseRegistersForTemporaries) register(it) else it
            else -> temporary(it)
        }
    })
}

operator fun LocalParameterList.component1() = parameters[0]
operator fun LocalParameterList.component2() = parameters[1]
operator fun LocalParameterList.component3() = parameters[2]
operator fun LocalParameterList.component4() = parameters[3]
operator fun LocalParameterList.component5() = parameters[4]
operator fun LocalParameterList.component6() = parameters[5]
operator fun LocalParameterList.component7() = parameters[6]
operator fun LocalParameterList.component8() = parameters[7]
operator fun LocalParameterList.component9() = parameters[8]
operator fun LocalParameterList.component10() = parameters[9]
operator fun LocalParameterList.component11() = parameters[10]
operator fun LocalParameterList.component12() = parameters[11]
operator fun LocalParameterList.component13() = parameters[12]
operator fun LocalParameterList.component14() = parameters[13]
operator fun LocalParameterList.component15() = parameters[14]
operator fun LocalParameterList.component16() = parameters[15]
operator fun LocalParameterList.component17() = parameters[16]
operator fun LocalParameterList.component18() = parameters[17]
operator fun LocalParameterList.component19() = parameters[18]
operator fun LocalParameterList.component20() = parameters[19]
operator fun LocalParameterList.component21() = parameters[20]
operator fun LocalParameterList.component22() = parameters[21]
