package io.github.kyay10.karm

inline fun buildArm(
    vararg transformations: FlattenedArmBuilder.() -> Unit, block: ArmBuilder.() -> Unit
): FlattenedArmBuilder = ArmBuilder().apply(block).apply {
    resolveMemoryConflicts()
}.flatten().apply {
    transformations.forEach { it() }
    removeNoOps()
    removeBranchesToNextInstruction()
    removeUnusedLabels()
    introduceNoOpsForOverlappingLabels()
    removeLabelConflicts()
}

class FlattenedArmBuilder(override val instructions: MutableList<ArmOpCode>) : ArmBlockOpCode {
    override val armName = ""
}

fun ArmBuilder.flatten(): FlattenedArmBuilder {
    val children = collectElementsOfType<ArmOpCode> { it !is ArmOperand && it !is ArmBlockOpCode }
    return FlattenedArmBuilder(children as MutableList<ArmOpCode>)
}

fun ArmBuilder.resolveMemoryConflicts() {
    val realAddresses =
        collectElementsOfType<ArmMemoryAddress> { it !is TemporaryAddress }.distinct()
    val sortedRealAddresses = IntArray(realAddresses.size) { realAddresses[it].address }.apply(IntArray::sort)
/*    val availableAddresses = (0 until AVAILABLE_MEMORY).reversed().asSequence()
        .filter { possibleAddress -> sortedRealAddresses.binarySearch(possibleAddress) < 0 }*/

    var sortedRealAddressIndex = sortedRealAddresses.lastIndex
    val availableAddresses = generateSequence({
        val seed = AVAILABLE_MEMORY
        sortedRealAddressIndex = sortedRealAddresses.lastIndex
        while (seed < sortedRealAddresses.getOrElse(sortedRealAddressIndex) { -1 }) {
            sortedRealAddressIndex--
        }
        seed
    }) { previousAddress ->
        var currentAddress = previousAddress - 1
        while (currentAddress == sortedRealAddresses.getOrElse(sortedRealAddressIndex) { -1 }) {
            currentAddress--
            sortedRealAddressIndex--
        }
        currentAddress.takeIf { it >= 0 }
    }.drop(1) // Ignore the seed address

    fun ArmBuilder.assignAddressesToUnassignedTemporaries() {
        /*val availableAddressesIterator = availableAddresses.iterator().apply {
            repeat(maxChildAddressesCount) { next() }
        }*/
        val availableAddressesIterator = availableAddresses.iterator().apply {
            repeat(maxChildAddressesCount) { next() }
        }
        for (temporaryAddress in temporaryAddresses) {
            temporaryAddress.address = availableAddressesIterator.next()
        }
    }

    collectElementsDeepestFirstOfType<ArmBuilder>().forEach {
        it.assignAddressesToUnassignedTemporaries()
    }
}
/*fun ArmBuilder.resolveMemoryConflicts() {
    val addresses: Set<ArmMemoryAddress> = collectElementsOfType<ArmMemoryAddress>().toSet()
    val (temporaryAddresses, realAddresses) = addresses.partition { it is TemporaryAddress } as Pair<List<TemporaryAddress>, List<ArmMemoryAddress>>
    val sortedRealAddresses = IntArray(realAddresses.size) { realAddresses[it].address }.apply(IntArray::sort)
    val availableAddresses = (0 until AVAILABLE_MEMORY).reversed().asSequence()
        .filter { possibleAddress -> sortedRealAddresses.binarySearch(possibleAddress) < 0 }.iterator()
    var sortedRealAddressIndex = sortedRealAddresses.lastIndex
    val availableAddresses2 = generateSequence(116) {
        var currentAddress = it - 1
        while (currentAddress == sortedRealAddresses.getOrElse(sortedRealAddressIndex) { -1 }) {
            currentAddress--
            sortedRealAddressIndex--
        }
        currentAddress.takeIf { it >= 0 }
    }.iterator().apply { next() }
    println(sortedRealAddresses.contentToString())
    for (temporaryAddress in temporaryAddresses) {
        if (temporaryAddress.address < 0) {
            temporaryAddress.address = availableAddresses.next()
            println(temporaryAddress.address == availableAddresses2.next())
        }
    }
}*/

fun FlattenedArmBuilder.removeNoOps() {
    val instructions = instructions.listIterator()
    for (instruction in instructions) {
        if (instruction.operands.any { (if (it is RTemp) it.computeTrueUnderlying() else it) == RNull } || (instruction is MoveOpCode && instruction.from.armName == instruction.into.armName)) {
            instructions.remove()
        }
    }
}

fun FlattenedArmBuilder.removeBranchesToNextInstruction() {
    // Remove a redundant branch call to the next instruction. For instance, a B call at the end of the subroutine
    // E.g.:
    // B mainLoop_end
    //mainLoop_end:
    val instructions = instructions.listIterator()
    var previousInstruction: BranchOpCode? = null
    for (instruction in instructions) {
        when {
            instruction is BranchOpCode -> previousInstruction = instruction
            instruction !is LabelOpCode -> previousInstruction = null
            previousInstruction?.label == instruction.label -> {
                instructions.previousUntilOrNull { it == previousInstruction }?.let {
                    instructions.remove()
                    instructions.nextUntilOrNull { it == instruction }
                }
            }
        }
    }
}

fun FlattenedArmBuilder.removeUnusedLabels() {
    val labelCounts = collectElementsOfType<ArmLabel>().groupingBy { it }.eachCount()
    val instructions = instructions.listIterator()
    for (instruction in instructions) {
        // If a label is never used, it'll only be mentioned once i.e. in the LabelOpcode itself
        if (instruction is LabelOpCode && labelCounts[instruction.label] == 1) {
            instructions.remove()
        }
    }
}

fun FlattenedArmBuilder.introduceNoOpsForOverlappingLabels() {
    val instructions = instructions.listIterator()
    var previousElement: ArmElement? = null
    for (instruction in instructions) {
        if (instruction is LabelOpCode && previousElement is LabelOpCode) {
            instructions.set(MoveOpCode(R0, R0))
            instructions.add(instruction)
        }
        previousElement = instruction
    }
}

fun FlattenedArmBuilder.removeLabelConflicts() {
    val allLabels = collectElementsOfType<ArmLabel>().groupBy { it.armName }.mapValues { it.value.distinct() }
    allLabels.forEach { (_, labels) ->
        labels.forEachIndexed { index, label ->
            if (index != 0) label.armName += "_$index"
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
inline fun ArmElement.collectElements(
    storeIn: MutableList<ArmElement> = mutableListOf(), crossinline predicate: (ArmElement) -> Boolean
): MutableList<ArmElement> = _collectElements(storeIn, predicate)(this)

@OptIn(ExperimentalStdlibApi::class)
inline fun _collectElements(
    storeIn: MutableList<ArmElement> = mutableListOf(), crossinline predicate: (ArmElement) -> Boolean
) = DeepRecursiveFunction<ArmElement, MutableList<ArmElement>> { element ->
    if (predicate(element)) storeIn.add(element)
    when (element) {
        is ArmBlockOpCode -> element.instructions.forEach { callRecursive(it) }
        is ArmOpCode -> element.operands.forEach { callRecursive(it) }
        else -> {}
    }
    storeIn
}

@OptIn(ExperimentalStdlibApi::class)
inline fun ArmElement.collectElementsDeepestFirst(
    storeIn: MutableList<ArmElement> = mutableListOf(), crossinline predicate: (ArmElement) -> Boolean
): MutableList<ArmElement> = _collectElementsDeepestFirst(storeIn, predicate)(this)

@OptIn(ExperimentalStdlibApi::class)
inline fun _collectElementsDeepestFirst(
    storeIn: MutableList<ArmElement> = mutableListOf(), crossinline predicate: (ArmElement) -> Boolean
) = DeepRecursiveFunction<ArmElement, MutableList<ArmElement>> { element ->
    when (element) {
        is ArmBlockOpCode -> element.instructions.forEach { callRecursive(it) }
        is ArmOpCode -> element.operands.forEach { callRecursive(it) }
        else -> {}
    }
    if (predicate(element)) storeIn.add(element)
    storeIn
}

inline fun <reified T : ArmElement> ArmElement.collectElementsOfType(
    crossinline predicate: (T) -> Boolean = { true }
): List<T> = collectElements { it is T && predicate(it) } as List<T>

inline fun <reified T : ArmElement> ArmElement.collectElementsDeepestFirstOfType(
    crossinline predicate: (T) -> Boolean = { true }
): List<T> = collectElementsDeepestFirst { it is T && predicate(it) } as List<T>
