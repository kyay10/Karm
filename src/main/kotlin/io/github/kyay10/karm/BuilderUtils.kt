package io.github.kyay10.karm


inline fun buildArm(block: ArmBuilder.() -> Unit): ArmBuilder = ArmBuilder().apply(block).apply {
    collectElementsDeepestFirstOfType<ArmBuilder>().forEach {
        it.resolveMemoryConflicts()
    }
}.flatten().apply {
    //resolveMemoryConflicts()
    removeNoOps()
    removeBranchesToNextInstruction()
    removeUnusedLabels()
    introduceNoOpsForOverlappingLabels()
    removeLabelConflicts()
}

fun ArmBuilder.flatten(): ArmBuilder {
    val children = collectElements { it is ArmOpCode && it !is ArmOperand && it !is ArmBlockOpCode }
    return ArmBuilder(children as MutableList<ArmOpCode>)
}

fun ArmBuilder.resolveMemoryConflicts() {
    val addresses: Set<ArmMemoryAddress> = collectElementsOfType<ArmMemoryAddress>().toSet()
    val (temporaryAddresses, realAddresses) = addresses.partition { it is TemporaryAddress } as Pair<List<TemporaryAddress>, List<ArmMemoryAddress>>
    val sortedRealAddresses = realAddresses.map { it.address }.toIntArray().apply(IntArray::sort)
    val availableAddresses = (0 until AVAILABLE_MEMORY).reversed().asSequence()
        .filter { possibleAddress -> sortedRealAddresses.binarySearch(possibleAddress) < 0 }.iterator()
    for (temporaryAddress in temporaryAddresses) {
        if (temporaryAddress.address < 0)
            temporaryAddress.address = availableAddresses.next()
    }
}
/*fun ArmBuilder.resolveMemoryConflicts() {
    val addresses: Set<ArmMemoryAddress> = collectElementsOfType<ArmMemoryAddress>().toSet()
    val sortedAddresses = addresses.map { it.address }.toIntArray().apply(IntArray::sort)
    val (temporaryAddresses, realAddresses) = addresses.partition { it is TemporaryAddress } as Pair<List<TemporaryAddress>, List<ArmMemoryAddress>>
    val sortedRealAddresses = realAddresses.map { it.address }.toIntArray().apply(IntArray::sort)
    val availableAddresses = (0 until AVAILABLE_MEMORY).reversed().asSequence()
        .filter { possibleAddress -> sortedAddresses.binarySearch(possibleAddress) < 0 }.iterator()
    for (temporaryAddress in temporaryAddresses) {
        if (sortedRealAddresses.binarySearch(temporaryAddress.address) >= 0) {
            temporaryAddress.address = availableAddresses.next()
        }
    }
}*/

fun ArmBuilder.removeNoOps() {
    val instructions = instructions.listIterator()
    for (instruction in instructions) {
        if (instruction.operands.map { if (it is RTemp) it.computeTrueUnderlying() else it }
                .contains(RNull) || (instruction is MoveOpCode && instruction.from.armName == instruction.into.armName)) {
            instructions.remove()
        }
    }
}

fun ArmBuilder.removeBranchesToNextInstruction() {
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

fun ArmBuilder.removeUnusedLabels() {
    val labelCounts = collectElementsOfType<ArmLabel>().groupingBy { it }.eachCount()
    val instructions = instructions.listIterator()
    for (instruction in instructions) {
        // If a label is never used, it'll only be mentioned once i.e. in the LabelOpcode itself
        if (instruction is LabelOpCode && labelCounts[instruction.label] == 1) {
            instructions.remove()
        }
    }
}

fun ArmBuilder.introduceNoOpsForOverlappingLabels() {
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

fun ArmBuilder.removeLabelConflicts() {
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

inline fun <reified T : ArmElement> ArmElement.collectElementsOfType(): List<T> = collectElements { it is T } as List<T>

inline fun <reified T : ArmElement> ArmElement.collectElementsDeepestFirstOfType(): List<T> =
    collectElementsDeepestFirst { it is T } as List<T>

val ArmBuilder.lastEffectiveInstruction get(): ArmOpCode? = computeParentOfLastEffectiveInstruction().instructions.lastOrNull()
tailrec fun ArmBuilder.computeParentOfLastEffectiveInstruction(): ArmBuilder {
    val lastInstruction = instructions.lastOrNull()
    return if (lastInstruction is ArmBuilder) lastInstruction.computeParentOfLastEffectiveInstruction() else this
}
/*
inline fun buildArm(block: ArmBuilder.() -> Unit): ArmBuilder = ArmBuilder().apply(block).apply {
    resolveMemoryConflicts()
    removeNoOps()
    introduceNoOpsForOverlappingLabels()
    removeLabelConflicts()
}

fun ArmBuilder.resolveMemoryConflicts() {
    val addresses: Set<ArmMemoryAddress> = collectElementsOfType<ArmMemoryAddress>().toSet()
    val sortedAddresses = addresses.map { it.address }.toIntArray().apply(IntArray::sort)
    val (temporaryAddresses, realAddresses) = addresses.partition { it is TemporaryAddress } as Pair<List<TemporaryAddress>, List<ArmMemoryAddress>>
    val sortedRealAddresses = realAddresses.map { it.address }.toIntArray().apply(IntArray::sort)
    val availableAddresses = (0 until AVAILABLE_MEMORY).reversed().asSequence()
        .filter { possibleAddress -> sortedAddresses.binarySearch(possibleAddress) < 0 }.iterator()
    for (temporaryAddress in temporaryAddresses) {
        if (sortedRealAddresses.binarySearch(temporaryAddress.address) >= 0) {
            temporaryAddress.address = availableAddresses.next()
        }
    }
}

fun ArmBuilder.removeNoOps() = listOf(this).removeNoOps()

tailrec fun List<ArmBuilder>.removeNoOps() {
    val children = buildList {
        this@removeNoOps.forEach { builder ->
            val instructions = builder.instructions.listIterator()
            while (instructions.hasNext()) {
                val instruction = instructions.next()
                if (instruction.operands.map { if (it is RTemp) it.computeTrueUnderlying() else it }
                        .contains(RNull) || (instruction is MoveOpCode && instruction.from.armName == instruction.into.armName)) {
                    instructions.remove()
                }
            }
            addAll(builder.instructions.filterIsInstance<ArmBuilder>())
        }
    }
    if (children.isNotEmpty()) children.removeNoOps()
}

fun ArmBuilder.removeLabelConflicts() {
    val allLabels = collectElementsOfType<ArmLabel>().groupBy { it.armName }.mapValues { it.value.distinct() }
    allLabels.forEach { (_, labels) ->
        labels.forEachIndexed { index, label ->
            if (index != 0) label.armName += "_$index"
        }
    }
}

fun ArmBuilder.introduceNoOpsForOverlappingLabels() {
    val flattenedElements = collectElements { it !is ArmBlockOpCode && it is ArmOpCode }
    var previousElement: ArmElement? = null
    for (element in flattenedElements) {
        if (element is LabelOpCode && previousElement is LabelOpCode) {
            previousElement.includeNoOp = true
        }
        previousElement = element
    }
}

@OptIn(ExperimentalStdlibApi::class)
inline fun ArmElement.collectElements(
    storeIn: MutableList<ArmElement> = mutableListOf(), crossinline predicate: (ArmElement) -> Boolean
): List<ArmElement> = _collectElements(storeIn, predicate)(this)

@OptIn(ExperimentalStdlibApi::class)
inline fun _collectElements(
    storeIn: MutableList<ArmElement> = mutableListOf(), crossinline predicate: (ArmElement) -> Boolean
) = DeepRecursiveFunction<ArmElement, List<ArmElement>> {
    if (predicate(it)) storeIn.add(it)
    when (it) {
        is ArmBlockOpCode -> it.instructions.forEach { callRecursive(it) }
        is ArmOpCode -> it.operands.forEach { callRecursive(it) }
        else -> {}
    }
    storeIn
}

inline fun <reified T : ArmElement> ArmElement.collectElementsOfType(): List<T> = collectElements { it is T } as List<T>

val ArmBuilder.lastEffectiveInstruction get(): ArmOpCode? = computeParentOfLastEffectiveInstruction().instructions.lastOrNull()
tailrec fun ArmBuilder.computeParentOfLastEffectiveInstruction(): ArmBuilder {
    val lastInstruction = instructions.lastOrNull()
    return if (lastInstruction is ArmBuilder) lastInstruction.computeParentOfLastEffectiveInstruction() else this
}
*/