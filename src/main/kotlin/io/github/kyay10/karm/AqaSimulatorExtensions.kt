package io.github.kyay10.karm

@RequiresOptIn(
    "This is part of the extensions in the AQA simulator and aren't necessarily valid AQA instructions",
    RequiresOptIn.Level.ERROR
)
annotation class AqaSimulatorExtension

@AqaSimulatorExtension
object AqaSimulatorExtensions

@AqaSimulatorExtension
data class DataOpCode private constructor(val value: ArmConstant) : ArmOpCode {
    context(AqaSimulatorExtensions)
    constructor(value: ArmConstant, unit: Unit = Unit) : this(value)

    override val armName = "DAT"
    override val operands = listOf(value)
}

context(AqaSimulatorExtensions)
        @AqaSimulatorExtension
fun data(value: ArmConstant) = DataOpCode(value)

context(AqaSimulatorExtensions)
        @AqaSimulatorExtension
fun data(value: Int) = data(value.c)
