package io.github.kyay10.karm

import io.github.kyay10.prettifykotlin.Pretty
import kotlin.reflect.KProperty

context(ArmBuilder) fun ArmRegister.assignFrom(value: ArmValueOperand) {
    when (value) {
        is ArmRegisterOrConstant -> move(this, value)
        is ArmMemoryAddress -> load(this, value)
        is ArmCalculationOperand -> +value.storeInto(this, given<ArmBuilder>())
    }
}

context(ArmBuilder) fun ArmMemoryAddress.assignFrom(value: ArmValueOperand) {
    when (value) {
        is ArmRegister -> store(value, this)
        is ArmMemoryAddress, is ArmConstant, is ArmCalculationOperand -> call(buildSubroutine(
            "setMemoryAddress"
        ) {
            store(into = this@assignFrom, from = register(value))
        })
    }
}

context(ArmBuilder) fun ArmStorageOperand.assignFrom(value: ArmValueOperand) {
    when (this) {
        is ArmRegister -> assignFrom(value)
        is ArmMemoryAddress -> assignFrom(value)
    }
}

context(ArmBuilder)
@Pretty("->")
infix fun ArmValueOperand.storeInto(storage: ArmStorageOperand) = storage.assignFrom(this)

context(ArmBuilder)
@Pretty("<-")
infix fun ArmStorageOperand.arrowAssign(value: ArmValueOperand) = assignFrom(value)


context(ArmBuilder)
@Pretty("=")
fun ArmStorageOperand.assign(value: ArmValueOperand) = assignFrom(value)

context(ArmBuilder)
@Pretty(":=")
infix fun ArmStorageOperand.colonAssign(value: ArmValueOperand) = assignFrom(value)

context(ArmBuilder) operator fun ArmRegister.provideDelegate(thisRef: Any?, property: KProperty<*>): ArmRegister =
    this.apply { useRegister(this) }

context(ArmBuilder) operator fun ArmStorageOperand.getValue(thisRef: Any?, property: KProperty<*>): ArmValueOperand =
    this.also {
        childDelegateForDelegates.let {
            if (it is ArmSubroutineBuilder && this is ArmRegister) it.addParameter(this)
        }
    }

context(ArmBuilder) operator fun ArmStorageOperand.setValue(
    thisRef: Any?, property: KProperty<*>, value: ArmValueOperand
) {
    // Weird quirk with delegates: the delegate's dispatch receiver (i.e. the ArmBuilder) is bound to it
    // upon delegation, and so this results in unexpected behaviours when you have 2 builders inside each
    // other and the inner builder tries to set a value "owned" by the outer builder. This fixes it by delegating
    // to the child that's currently being created.
    childDelegateForDelegates?.run {
        setValue(thisRef, property, value)
    } ?: assignFrom(value)
}

context(ArmBuilder)
@Pretty("<->")
infix fun ArmStorageOperand.swap(other: ArmStorageOperand) = call(buildSubroutine("swap") {
    val temp by register(this@swap)
    this@swap arrowAssign other
    other arrowAssign temp
})

