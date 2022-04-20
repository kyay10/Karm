package io.github.kyay10.karm

context(ArmBuilder) infix fun ArmStorageOperand.`&=`(other: ArmValueOperand) {
    this.`=`(this `&` other)
}

context(ArmBuilder)
        @JvmName("orAssign")
        infix fun ArmStorageOperand.`|=`(other: ArmValueOperand) {
    this.`=`(this `|` other)
}

context(ArmBuilder) infix fun ArmStorageOperand.`^=`(other: ArmValueOperand) {
    this.`=`(this `^` other)
}

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("shiftLeftAssign")
        infix fun ArmStorageOperand.`<<=`(other: ArmValueOperand) {
    this.`=`(this `<<` other)
}

context(ArmBuilder)
        @Suppress("INVALID_CHARACTERS")
        @JvmName("shiftRightAssign")
        infix fun ArmStorageOperand.`>>=`(other: ArmValueOperand) {
    this.`=`(this `>>` other)
}

context(ArmBuilder) operator fun ArmStorageOperand.plusAssign(other: ArmValueOperand) {
    this + other `->` this
}

context(ArmBuilder) operator fun ArmStorageOperand.minusAssign(other: ArmValueOperand) {
    this - other `->` this
}

context(ArmBuilder) operator fun ArmValueOperand.inc(): ArmValueOperand = apply {
    this + 1.c `->` out
}

context(ArmBuilder) operator fun ArmValueOperand.dec(): ArmValueOperand = apply {
    this - 1.c `->` out
}