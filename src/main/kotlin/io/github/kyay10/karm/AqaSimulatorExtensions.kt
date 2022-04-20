package io.github.kyay10.karm

@RequiresOptIn(
    "This is part of the extensions in the AQA simulator and aren't necessarily valid AQA instructions",
    RequiresOptIn.Level.ERROR
)
annotation class AqaSimulatorExtension

@AqaSimulatorExtension
data class DataOpCode(val value: ArmConstant) : ArmOpCode {
    override val armName = "DAT"
    override val operands = listOf(value)
}

@AqaSimulatorExtension
fun data(value: ArmConstant) = DataOpCode(value)

@AqaSimulatorExtension
fun data(value: Int) = data(value.c)
