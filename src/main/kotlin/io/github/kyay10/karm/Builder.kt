@file:OptIn(ExperimentalContracts::class)

package io.github.kyay10.karm

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

open class ArmBuilder(
    override val instructions: MutableList<ArmOpCode> = mutableListOf(), val parent: ArmBuilder? = null
) : ArmBlockOpCode {
    val availableRegisters: MutableSet<ArmRegister> = (parent?.availableRegisters ?: builtInRegisters).toMutableSet()
    val usedRegisters: MutableSet<ArmRegister> = mutableSetOf()

    val usedAddressesCount get() = ownAddressesCount + maxChildAddressesCount

    // These are addresses that are available throughout the whole block
    var ownAddressesCount = 0

    // These are the maximum amount of addresses needed for each sub-block
    var maxChildAddressesCount = 0
        set(value) {
            field = kotlin.math.max(field, value)
        }

    var childDelegateForDelegates: ArmBuilder? = null

    open fun useRegister(register: ArmRegister) {
        markRegister(register)
        if (availableRegisters.remove(register))
            usedRegisters.add(register)
    }

    open fun useRegisters(registers: Set<ArmRegister>) {
        markRegisters(registers)
        val ownRegisters = availableRegisters intersect registers
        availableRegisters.removeAll(ownRegisters)
        usedRegisters.addAll(ownRegisters)
    }

    open fun markRegister(register: ArmRegister) {
        parent?.markRegister(register)
    }

    open fun markRegisters(registers: Set<ArmRegister>) {
        parent?.markRegisters(registers)
    }

    override val armName = ""

    operator fun <T : ArmOpCode> T.unaryPlus(): T = addInstruction(this)
    fun <T : ArmOpCode> addInstruction(instruction: T): T = instruction.also {
        instructions.add(it)
        useRegisters(it.operands.filterIsInstance<ArmRegister>().toSet())
        if (it is ArmBuilder) {
            maxChildAddressesCount = it.usedAddressesCount
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
        _parameters.add(parameter)
        if (parameter !in parameters) availableRegisters.remove(availableRegisters.first())
    }

    fun addParameters(parameters: Iterable<ArmRegister>) {
        parameters.forEach(::addParameter)
    }

    val returnValue = RTemp(RNull)
    var result by returnValue

    val labelsDependentOnBaseName = mutableMapOf<ArmLabel, String>()
    val subroutinesDependentOnBaseName = mutableMapOf<ArmSubroutineBuilder, String>()

    val end by labelWithPostfix("_end")

    var baseName = name
        set(newName) {
            field = newName
            refreshLabels()
        }

    fun refreshLabels() {
        labelsDependentOnBaseName.forEach { (label, format) ->
            label.armName = format.format(baseName)
        }
        subroutinesDependentOnBaseName.forEach { (subroutine, format) ->
            subroutine.baseName = format.format(baseName)
        }
    }

    fun labelWithPostfix(postfix: String): ArmLabel {
        val format = "%s$postfix"
        val label = ArmLabel(format.format(baseName))
        labelsDependentOnBaseName[label] = format
        return label
    }

    fun ArmBuilder.Return(value: ArmValueOperand? = null) {
        if (value != null) result = value
        branch(end)
    }

    init {
        availableRegisters.clear()
        availableRegisters.addAll(builtInRegisters.map { RTemp(it) }.drop(parameters.size))
    }

    override fun markRegister(register: ArmRegister) {
        super.markRegister(register)
        if (register in availableRegisters && register is RTemp) internalRegisters.add(register)
        if (register in parent!!.usedRegisters) addParameter(register)
    }

    override fun markRegisters(registers: Set<ArmRegister>) {
        super.markRegisters(registers)
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
        val builder = ArmBuilder(instructions.toMutableList(), parent)
        builder.maxChildAddressesCount = maxChildAddressesCount
        builder.ownAddressesCount = ownAddressesCount
        builder.ownAddressesCount += (internalRegisters.size - parent.availableRegisters.size).coerceAtLeast(0)
        for (i in parent.availableRegisters.size until internalRegisters.size) {
            val usedRegister = usedRegisters.next()
            val temporaryAddress = memory()
            //TemporaryAddress(AVAILABLE_MEMORY - builder.usedAddressesCount + (i - parent.availableRegisters.size))
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

inline fun ArmBuilder.buildSubroutine(
    name: String,
    vararg localParameters: ArmValueOperand,
    dependencies: Iterable<ArmValueOperand> = emptyList(),
    block: ArmSubroutineBuilder.(LocalParameterList) -> Unit
): ArmSubroutine {
    contract {
        callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    val parameterList = localParameters.asList()
    val builder = ArmSubroutineBuilder(name, this, parameterList.collectRegisterParameters())
    dependencies.forEach { if (it is ArmRegister) builder.addParameter(it) }
    withinBuilder(builder) { block(parameterList.localized()) }
//    println(builder.baseName)
    println(builder.parameters)
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

/*
inline fun ArmBuilder.buildSubroutine(
    name: String, block: ArmSubroutineBuilder.() -> Unit
): ArmSubroutine {
    contract {
        callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    val builder = ArmSubroutineBuilder(name, this, emptyList())
    withinBuilder(builder, block)
    println(builder.parameters)
    return builder
}

inline fun ArmBuilder.buildSubroutine(
    name: String, param1: ArmValueOperand, block: ArmSubroutineBuilder.(ArmValueOperand) -> Unit
): ArmSubroutine {
    contract {
        callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return buildSubroutine(name) {
        block(register(param1))
    }
}*/
