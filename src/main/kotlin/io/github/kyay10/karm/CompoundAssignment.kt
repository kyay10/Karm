package io.github.kyay10.karm

import io.github.kyay10.prettifykotlin.Pretty

context(ArmBuilder)
@Pretty("&=")
infix fun ArmStorageOperand.andAssign(other: ArmValueOperand) {
    this.assign(this and other)
}

context(ArmBuilder)
@Pretty("|=")
infix fun ArmStorageOperand.orAssign(other: ArmValueOperand) {
    this.assign(this or other)
}

context(ArmBuilder)
@Pretty("^=")
infix fun ArmStorageOperand.xorAssign(other: ArmValueOperand) {
    this.assign(this xor other)
}

context(ArmBuilder)
@Pretty("<<=")
infix fun ArmStorageOperand.shlAssign(other: ArmValueOperand) {
    this.assign(this shr other)
}

context(ArmBuilder)
@Pretty(">>=")
infix fun ArmStorageOperand.shrAssign(other: ArmValueOperand) {
    this.assign(this shl other)
}

context(ArmBuilder) operator fun ArmStorageOperand.plusAssign(other: ArmValueOperand) {
    this + other storeInto this
}

context(ArmBuilder) operator fun ArmStorageOperand.minusAssign(other: ArmValueOperand) {
    this - other storeInto this
}

context(ArmBuilder) operator fun ArmValueOperand.inc(): ArmValueOperand = apply {
    this + 1.c storeInto out
}

context(ArmBuilder) operator fun ArmValueOperand.dec(): ArmValueOperand = apply {
    this - 1.c storeInto out
}