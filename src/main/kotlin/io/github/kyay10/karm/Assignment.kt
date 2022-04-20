package io.github.kyay10.karm

import kotlin.reflect.KProperty

context(ArmBuilder) fun ArmRegister.assignFrom(value: ArmValueOperand): ArmOpCode = when (value) {
    is ArmRegisterOrConstant -> move(this, value)
    is ArmMemoryAddress -> load(this, value)
    is ArmCalculationOperand -> +value.storeInto(this, this@ArmBuilder)
}

context(ArmBuilder) fun ArmMemoryAddress.assignFrom(value: ArmValueOperand): ArmOpCode = when (value) {
    is ArmRegister -> store(value, this)
    is ArmMemoryAddress, is ArmConstant, is ArmCalculationOperand -> call(buildSubroutine(
        "setMemoryAddress"
    ) {
        store(into = this@assignFrom, from = register(value))
    })
}

context(ArmBuilder) fun ArmStorageOperand.assignFrom(value: ArmValueOperand): ArmOpCode = when (this) {
    is ArmRegister -> assignFrom(value)
    is ArmMemoryAddress -> assignFrom(value)
}

context(ArmBuilder) fun ArmValueOperand.storeInto(storage: ArmStorageOperand): ArmOpCode = storage.assignFrom(this)

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("arrowAssign")
        infix fun ArmStorageOperand.`<-`(value: ArmValueOperand) {
    assignFrom(value)
}

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("dotAssign")
fun ArmStorageOperand.`=`(value: ArmValueOperand) {
    assignFrom(value)
}

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("colonAssign")
        infix fun ArmStorageOperand.`:=`(value: ArmValueOperand) {
    assignFrom(value)
}

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("arrowStore")
        infix fun ArmValueOperand.`->`(storage: ArmStorageOperand) {
    storeInto(storage)

}

context(ArmBuilder) operator fun ArmRegister.provideDelegate(thisRef: Any?, property: KProperty<*>): ArmRegister =
    this.apply { useRegister(this) }

context(ArmBuilder) operator fun ArmStorageOperand.getValue(thisRef: Any?, property: KProperty<*>): ArmValueOperand =
    this.also {
        childDelegateForDelegates.let {
            if (it is ArmSubroutineBuilder && this@getValue is ArmRegister) it.addParameter(this@getValue)
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

fun ArmBuilder.swap(first: ArmStorageOperand, second: ArmStorageOperand) = call(buildSubroutine("swap") {
    val temp by register(first)
    first `<-` second
    second `<-` temp
})

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("arrowSwap")
        infix fun ArmStorageOperand.`<->`(second: ArmStorageOperand) = swap(this, second)

