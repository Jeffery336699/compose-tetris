package com.jetgame.tetris.logic

import androidx.compose.ui.geometry.Offset
import kotlin.math.absoluteValue
import kotlin.random.Random


data class Spirit(
    val shape: List<Offset> = emptyList(),
    val offset: Offset = Offset(0, 0),
) {
    val location: List<Offset> = shape.map { it + offset }

    fun moveBy(step: Pair<Int, Int>): Spirit =
        copy(offset = offset + Offset(step.first, step.second))

    fun rotate(): Spirit {
        val newShape = shape.toMutableList()
        for (i in shape.indices) {
            newShape[i] = Offset(shape[i].y, -shape[i].x)
        }
        return copy(shape = newShape)
    }

    /**
     * 当前形状偏移调整使其可以在规定的matrix中显示（一般用于右侧4x2的计分板nextSpirit形状）
     */
    fun adjustOffset(matrix: Pair<Int, Int>, adjustY: Boolean = true): Spirit {
        val yOffset =
            if (adjustY)
                (location.minByOrNull { it.y }?.y?.takeIf { it < 0 }?.absoluteValue ?: 0).toInt() +
                        (location.maxByOrNull { it.y }?.y?.takeIf { it > matrix.second - 1 }
                            ?.let { matrix.second - it - 1 } ?: 0).toInt()
            else 0
        val xOffset =
            (location.minByOrNull { it.x }?.x?.takeIf { it < 0 }?.absoluteValue ?: 0).toInt() +
                    (location.maxByOrNull { it.x }?.x?.takeIf { it > matrix.first - 1 }
                        ?.let { matrix.first - it - 1 } ?: 0).toInt()
        return moveBy(xOffset to yOffset)
    }

    companion object {
        val Empty = Spirit()
    }
}


val SpiritType = listOf(
    listOf(Offset(1, -1), Offset(1, 0), Offset(0, 0), Offset(0, 1)),//Z
    listOf(Offset(0, -1), Offset(0, 0), Offset(1, 0), Offset(1, 1)),//S
    listOf(Offset(0, -1), Offset(0, 0), Offset(0, 1), Offset(0, 2)),//I
    listOf(Offset(0, 1), Offset(0, 0), Offset(0, -1), Offset(1, 0)),//T
    listOf(Offset(1, 0), Offset(0, 0), Offset(1, -1), Offset(0, -1)),//O
    listOf(Offset(0, -1), Offset(1, -1), Offset(1, 0), Offset(1, 1)),//L
    listOf(Offset(1, -1), Offset(0, -1), Offset(0, 0), Offset(0, 1))//J
)


fun Spirit.isValidInMatrix(blocks: List<Brick>, matrix: Pair<Int, Int>): Boolean {
    return location.none { location ->
        location.x < 0 || location.x > matrix.first - 1 || location.y > matrix.second - 1 ||
                blocks.any { it.location.x == location.x && it.location.y == location.y }
    }
}

fun Spirit.findInValidInMatrix(blocks: List<Brick>, matrix: Pair<Int, Int>): Offset? {
    return location.find { location ->
        location.x < 0 || location.x > matrix.first - 1 || location.y > matrix.second - 1 ||
                blocks.any { it.location.x == location.x && it.location.y == location.y }
    }
}

fun generateSpiritReverse(matrix: Pair<Int, Int>): List<Spirit> {
    return SpiritType.map {
        // y轴往上偏移一个单位是在这里进行的（生成的时候）
        Spirit(it, Offset(Random.nextInt(matrix.first - 1), -1)).adjustOffset(matrix, false)
    }.shuffled()
}

/** 生成List<Brick> */
fun testGenerateListBrick(index: Int = 0,downStep:Boolean = true): List<Brick> {
    return Brick.of(SpiritType[index.coerceIn(SpiritType.indices)].map { it + Offset(0, if (downStep) 1 else 0) })
}

/** 生成Spirit */
fun testGenerateSpirit(index: Int = 0 ,downStep:Boolean = true): Spirit {
    return Spirit(SpiritType[index.coerceIn(SpiritType.indices)], Offset(0, if (downStep) 1 else 0))
}

