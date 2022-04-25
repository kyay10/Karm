package io.github.kyay10.karm

fun main() {
    println(buildArm {
        var output by R1
        R0 `<-` 40.c
        R2 `<-` 42.c
        R3 `<-` 43.c
        R4 `<-` 44.c
        R5 `<-` 45.c
        R6 `<-` 46.c
        R7 `<-` 47.c
        R8 `<-` 48.c
        R9 `<-` 49.c
        R10 `<-` 50.c
        R12 `<-` 52.c
        output = multiply4(memory(100), memory(105))
        multiply5(memory(100), memory(105)) `->` memory(115)
        memory(120) `<-` multiply6(memory(100), memory(105))
        output = (output + 42.c) - (output + 69.c - output + R2 - R2) + 27.c
        halt()
    }.toAsmString())
}

fun ArmBuilder.multiply(
    first: ArmValueOperand, second: ArmValueOperand
): ArmCalculationOperand = buildSubroutine("multiply") {
    var operand1 by register(first)
    var operand2 by register(second)
    operand1.compare(operand2, "optimizeSmallerValue") {
        greaterThan("swapValues") {
            swap(operand1 as ArmStorageOperand, operand2 as ArmStorageOperand)
        }
    }
    var originalOperand2 by register()
    val quickLoop by labeled
    val quickLoopEnd by labeled
    label(quickLoop)
    If(operand1 and constant(1), "isOdd") {
        branch(quickLoopEnd)
    }
    operand2 = operand2 shl constant(1)
    operand1 = operand1 shr constant(1)
    branch(quickLoop)
    label(quickLoopEnd)
    originalOperand2 = operand2
    val mainLoop by labeled
    val mainLoopEnd by labeled
    label(mainLoop)
    operand1.compare(constant(1), "rangeCheck") {
        lessThan("outOfRange") {
            branch(mainLoopEnd)
        }
        equal = lessThan
    }
    operand2 += originalOperand2
    operand1--
    branch(mainLoop)
    label(mainLoopEnd)
    result = operand2
}

fun ArmBuilder.multiply2(
    first: ArmValueOperand, second: ArmValueOperand
): ArmCalculationOperand = buildSubroutine("multiply2") {
    var operand1 by register(first)
    var operand2 by register(second)
    operand1.compare(operand2, "optimizeSmallerValue") {
        greaterThan("swapValues") {
            swap(operand1 as ArmStorageOperand, operand2 as ArmStorageOperand)
        }
    }
    result = constant(0)
    val mainLoop by labeled
    val mainLoopEnd by labeled
    label(mainLoop)
    operand1.compare(constant(0), "rangeCheck") {
        lessThan = mainLoopEnd
        equal = lessThan
    }
    If(operand1 and constant(1), "oddCheck") {
        result += operand2
    }
    operand2 = operand2 shl constant(1)
    operand1 = operand1 shr constant(1)
    branch(mainLoop)
    label(mainLoopEnd)
}

fun ArmBuilder.multiply3(
    first: ArmValueOperand, second: ArmValueOperand
): ArmCalculationOperand = buildSubroutine("multiply3") {
    var operand1 by register(first)
    var operand2 by register(second)
    If(operand1 greaterThan operand2, "swapValues") {
        swap(operand1 as ArmStorageOperand, operand2 as ArmStorageOperand)
    }
    result = constant(0)
    val mainLoop by labeled
    val mainLoopEnd by labeled
    label(mainLoop)
    If(operand1 lessThanOrEqual constant(0), "rangeCheck") {
        branch(mainLoopEnd)
    } Else If(operand1 and constant(1), "oddCheck") {
        result += operand2
    }
    operand2 = operand2 shl constant(1)
    operand1 = operand1 shr constant(1)
    branch(mainLoop)
    label(mainLoopEnd)
}

fun ArmBuilder.multiply4(
    first: ArmValueOperand, second: ArmValueOperand
): ArmCalculationOperand = buildSubroutine("multiply4") {
    var operand1 by register(first)
    var operand2 by register(second)
    If(operand1 `>` operand2, "swapValues") {
        operand1.out `<->` operand2.out
    }
    result = 0.c
    While(operand1 `>` 0.c, "mainLoop") {
        If(operand1 `&` 1.c, "oddCheck") {
            result += operand2
        }
        operand2 = operand2 `<<` 1.c
        operand1 = operand1 `>>` 1.c
    }
}

fun ArmBuilder.multiply5(
    first: ArmValueOperand, second: ArmValueOperand
): ArmCalculationOperand = buildSubroutine("multiply5") {
    val operand2 = register(second)
    result = 0.c
    For(first, { it `>` 0.c }, { it `>>=` 1.c; operand2 `<<=` 1.c; }, "mainLoop") { operand1 ->
        If(operand1 `&` 1.c, "oddCheck") {
            result += operand2
        }
    }
}

fun ArmBuilder.multiply6(
    first: ArmValueOperand, second: ArmValueOperand
): ArmCalculationOperand = buildSubroutine("multiply6") {
    var operand2 by register(second)
    result = 0.c
    for (operand1 in first..1.c `>>` 1.c) loop("multiplicationStep") {
        If(operand1 `&` 1.c, "oddCheck") {
            result += operand2
        }
        operand2 = operand2 `<<` 1.c
    }
}
