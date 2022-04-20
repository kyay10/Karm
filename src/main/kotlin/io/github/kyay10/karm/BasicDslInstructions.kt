package io.github.kyay10.karm

import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KProperty


fun R(index: Int): ArmRegister = builtInRegisters.getOrElse(index) { RCustom(index) }
fun constant(value: Int): ArmConstant = ArmConstant(value)
val Int.c: ArmConstant get() = constant(this)
fun label(name: String): ArmLabel = ArmLabel(name)
fun memory(address: Int): ArmMemoryAddress = MemoryAddress(address)
val labeled = PropertyDelegateProvider<Any?, ArmLabel> { _, property ->
    label(property.name)
}

operator fun ArmLabel.getValue(thisRef: Any?, property: KProperty<*>) = this

val ArmValueOperand.out get() = this as ArmStorageOperand

fun ArmBuilder.label(label: ArmLabel): LabelOpCode = +LabelOpCode(label)
fun ArmBuilder.load(into: ArmRegister, from: ArmMemoryAddress): LoadRegisterOpCode = +LoadRegisterOpCode(into, from)

fun ArmBuilder.store(from: ArmRegister, into: ArmMemoryAddress): StoreRegisterOpCode = +StoreRegisterOpCode(from, into)

fun ArmBuilder.move(into: ArmRegister, from: ArmRegisterOrConstant): MoveOpCode = +MoveOpCode(into, from)
fun ArmBuilder.moveNot(into: ArmRegister, from: ArmRegisterOrConstant): MoveOpCode =
    +MoveOpCode(into, from, isNot = true)

fun ArmBuilder.register(initializer: ArmValueOperand? = null): ArmRegister = availableRegisters.last().also {
    useRegister(it)
    initializer?.storeInto(it)
}

fun ArmBuilder.memory(initializer: ArmValueOperand? = null): ArmMemoryAddress = TemporaryAddress(-1).also {
    ownAddressesCount++
    initializer?.storeInto(it)
}

fun ArmBuilder.temporary(initializer: ArmValueOperand? = null): ArmStorageOperand =
    if (shouldUseRegistersForTemporaries) register(initializer) else memory(initializer)

val ArmBuilder.shouldUseRegistersForTemporaries get() = availableRegisters.size > 7

fun ArmBuilder.call(subroutine: ArmSubroutine): ArmBlockOpCode = +subroutine.callIn(this)
fun ArmBuilder.compare(first: ArmRegister, second: ArmRegisterOrConstant): CompareOpCode = +CompareOpCode(first, second)
fun ArmBuilder.branch(label: ArmLabel, condition: BranchCondition = BranchCondition.Always): BranchOpCode =
    +BranchOpCode(label, condition)

fun ArmBuilder.branchLessThan(label: ArmLabel): BranchOpCode = branch(label, BranchCondition.LessThan)
fun ArmBuilder.branchGreaterThan(label: ArmLabel): BranchOpCode = branch(label, BranchCondition.GreaterThan)
fun ArmBuilder.branchLessThanOrEqual(label: ArmLabel): BranchOpCode = branch(label, BranchCondition.LessThanOrEqual)
fun ArmBuilder.branchGreaterThanOrEqual(label: ArmLabel): BranchOpCode =
    branch(label, BranchCondition.GreaterThanOrEqual)

fun ArmBuilder.branchEqual(label: ArmLabel): BranchOpCode = branch(label, BranchCondition.Equal)
fun ArmBuilder.branchNotEqual(label: ArmLabel): BranchOpCode = branch(label, BranchCondition.NotEqual)
fun ArmBuilder.halt(): HaltOpCode = +HaltOpCode
