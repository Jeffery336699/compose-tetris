package com.jetgame.tetris.logic

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetgame.tetris.logic.Spirit.Companion.Empty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min


class GameViewModel : ViewModel() {

    private val _viewState: MutableState<ViewState> = mutableStateOf(ViewState())
    val viewState : State<ViewState> = _viewState


    fun dispatch(action: Action) =
        reduce(viewState.value, action)


    private fun reduce(state: ViewState, action: Action) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {

                emit(when (action) {
                    Action.Reset -> run {
                        if (state.gameStatus == GameStatus.Onboard || state.gameStatus == GameStatus.GameOver)
                            return@run ViewState(
                                gameStatus = GameStatus.Running,
                                isMute = state.isMute
                            )
                        state.copy(
                            gameStatus = GameStatus.ScreenClearing
                        ).also {
                            // Optimize: 细节这里是另起一个协程，不会阻塞also的返回ViewState，由于cleanScreen有延迟，launch中的emit是发生在后面的会替换之前的
                            launch {
                                clearScreen(state = state)
                                emit(
                                    ViewState(
                                        gameStatus = GameStatus.Onboard,
                                        isMute = state.isMute
                                    )
                                )
                            }
                        }
                    }

                    Action.Pause -> if (state.isRuning) {
                        state.copy(gameStatus = GameStatus.Paused)
                    } else state

                    Action.Resume ->
                        if (state.isPaused) {
                            state.copy(gameStatus = GameStatus.Running)
                        } else state

                    is Action.Move -> run {
                        if (!state.isRuning) return@run state
                        SoundUtil.play(state.isMute, SoundType.Move)
                        val offset = action.direction.toOffset()
                        val spirit = state.spirit.moveBy(offset)
                        // 就算是移动也是一直保证它的合法的，跟GAME_OVER形成对比
                        if (spirit.isValidInMatrix(state.bricks, state.matrix)) {
                            state.copy(spirit = spirit)
                        } else {
                            state
                        }
                    }

                    Action.Rotate -> run {
                        if (!state.isRuning) return@run state
                        SoundUtil.play(state.isMute, SoundType.Rotate)
                        val spirit = state.spirit.rotate().adjustOffset(state.matrix)
                        // 同样旋转也是一直保证它的合法的，跟GAME_OVER形成对比
                        if (spirit.isValidInMatrix(state.bricks, state.matrix)) {
                            state.copy(spirit = spirit)
                        } else {
                            state
                        }
                    }

                    Action.Drop -> run {
                        if (!state.isRuning) return@run state
                        SoundUtil.play(state.isMute, SoundType.Drop)
                        var i = 0
                        while (state.spirit.moveBy(0 to ++i)
                                .isValidInMatrix(state.bricks, state.matrix)
                        ) { //nothing to do
                        }
                        // 一直保证数据的合法
                        val spirit = state.spirit.moveBy(0 to i - 1)

                        state.copy(spirit = spirit)
                    }

                    Action.GameTick -> run {
                        if (!state.isRuning) return@run state

                        //Spirit continue falling
                        // 个人理解是在尝试往下移动一步的情况下，
                        // 一切OK的话就认定是正常下落，直接返回不进行后续逻辑判断
                        if (state.spirit != Empty) {
                            val spirit = state.spirit.moveBy(Direction.Down.toOffset())
                            if (spirit.isValidInMatrix(state.bricks, state.matrix)) {
                                return@run state.copy(spirit = spirit)
                            }
                        }

                        //GameOver
                        if (!state.spirit.isValidInMatrix(state.bricks, state.matrix)) {
                            state.spirit.findInValidInMatrix(state.bricks, state.matrix)
                                ?.let { offset ->
                                    println("GameOver: $offset")
                                    println("GameOver: ${state.spirit.location}")
                                }
                            return@run state.copy(
                                gameStatus = GameStatus.ScreenClearing
                            ).also {
                                launch {
                                    emit(
                                        clearScreen(state = state).copy(gameStatus = GameStatus.GameOver)
                                    )
                                }
                            }
                        }

                        //Next Spirit
                        val (updatedBricks, clearedLines) = updateBricks(
                            state.bricks,
                            state.spirit,
                            matrix = state.matrix
                        )
                        val (noClear, clearing, cleared) = updatedBricks
                        // println("[Clean] clearedLines=$clearedLines")
                        // println("[Clean] noClear=${noClear.size}")
                        // println("[Clean] clearing=${clearing.size}")
                        // println("[Clean] cleared=${cleared.size}")

                        val newState = state.copy(
                            spirit = state.spiritNext,
                            // 这里不断的移除之前的，更新后备的形状集合→spiritReserve
                            spiritReserve = (state.spiritReserve - state.spiritNext).takeIf { it.isNotEmpty() }
                                ?: generateSpiritReverse(state.matrix),
                            score = state.score + calculateScore(clearedLines) +
                                    if (state.spirit != Empty) ScoreEverySpirit else 0,
                            line = state.line + clearedLines
                        )
                        /**
                         * 1. 这里触发的时机是有新的形状出现，所以这里的spirit是新的形状
                         * 2. spiritReserve.first()是下一个形状
                         */
                        println("newState: spirit=              ${newState.spirit}")
                        println("newState: spiritReserve.first= ${newState.spiritReserve.first()}")
                        if (clearedLines != 0) {// has cleared lines
                            SoundUtil.play(state.isMute, SoundType.Clean)
                            state.copy(
                                gameStatus = GameStatus.LineClearing
                            ).also {
                                launch {
                                    //animate the clearing lines
                                    repeat(5) {
                                        emit(
                                            state.copy(
                                                gameStatus = GameStatus.LineClearing,
                                                spirit = Empty,
                                                bricks = if (it % 2 == 0) noClear else clearing
                                            )
                                        )
                                        delay(100)
                                    }
                                    //delay emit new state
                                    emit(
                                        newState.copy(
                                            bricks = cleared,
                                            gameStatus = GameStatus.Running
                                        )
                                    )
                                }
                            }
                        } else {
                            newState.copy(bricks = noClear)
                        }
                    }

                    Action.Mute -> state.copy(isMute = !state.isMute)

                })
            }
        }

    }

    private suspend fun clearScreen(state: ViewState): ViewState {
        SoundUtil.play(state.isMute, SoundType.Start)
        val xRange = 0 until state.matrix.first
        var newState = state

        (state.matrix.second downTo 0).forEach { y ->
            emit(
                state.copy(
                    gameStatus = GameStatus.ScreenClearing,
                    bricks = state.bricks + Brick.of(
                        xRange, y until state.matrix.second
                    )
                )
            )
            delay(50)
        }
        (0..state.matrix.second).forEach { y ->
            emit(
                state.copy(
                    gameStatus = GameStatus.ScreenClearing,
                    bricks = Brick.of(xRange, y until state.matrix.second),
                    spirit = Empty
                ).also { newState = it }
            )
            delay(50)
        }
        return newState
    }

    private fun emit(state: ViewState) {
        _viewState.value = state
        println("emit:  bricks=${state.bricks.size} , spiritReserve=${state.spiritReserve.size} , spirit=${state.spirit}")
    }

    /**
     * Return a [Triple] to store clear-info for bricks:
     * - [Triple.first]:  Bricks before line clearing (Current bricks plus Spirit)
     * - [Triple.second]: Bricks after line cleared but not offset (bricks minus lines should be cleared)
     * - [Triple.third]: Bricks after line cleared (after bricks offset)
     */
    private fun updateBricks(
        curBricks: List<Brick>,
        spirit: Spirit,
        matrix: Pair<Int, Int>
    ): Pair<Triple<List<Brick>, List<Brick>, List<Brick>>, Int> {
        val bricks = (curBricks + Brick.of(spirit))
        // 1. 这里使用set其实是为了去重，后面有用
        val map = mutableMapOf<Float, MutableSet<Float>>()
        bricks.forEach {
            map.getOrPut(it.location.y) {
                mutableSetOf()
            }.add(it.location.x)
        }
        var clearing = bricks
        var cleared = bricks
        val clearLines = map.entries.sortedBy { it.key }
            // 2. it.value(Set)的size等于matrix的宽度，说明这一行可以消除
            .filter { it.value.size == matrix.first }.map { it.key }
            .onEach { line -> // 3. 此时的y轴line号表示的是可以被清除的
                //clear line
                clearing = clearing.filter { it.location.y != line }
                //clear line and then offset brick
                cleared = cleared.filter { it.location.y != line }
                    .map { if (it.location.y < line) it.offsetBy(0 to 1) else it }

            }
        // 4. 全程无关正在消除的行，只关心最后保留的下来的砖块位置，前（clearing）与后（cleared）
        return Triple(bricks, clearing, cleared) to clearLines.size
    }

    // MVI中UI层监听的状态，通常使用mutableStateOf包裹，这样在ViewModel中修改状态时，UI层使用到的地方会自动刷新（重组）
    data class ViewState(
        /**
         * 这个是屏幕底层的所有砖块
         */
        val bricks: List<Brick> = emptyList(),
        /**
         * ### 这个描述下落中的形状
         * 1. 其中shape是方块形状的坐标（相对原始坐标往y轴负方向偏移一个单位）
         * 2. offset是形状的偏移
         *      * 比如下落过程中没其他操作，x轴不断y轴不断+1
         *      * 初始值y轴上也有个-1的偏移，eg offset=Offset(9.0, -1.0))
         * 3. location是shape和offset的和
         */
        val spirit: Spirit = Empty,
        /**
         * 接下来的形状列表，每次把所有形状都放进去，然后取出第一个,
         * 其Size(7,6,..2,1,7,6,..2,1,7,6,..2,1)
         */
        val spiritReserve: List<Spirit> = emptyList(),
        val matrix: Pair<Int, Int> = MatrixWidth to MatrixHeight,
        val gameStatus: GameStatus = GameStatus.Onboard,
        val score: Int = 0,
        val line: Int = 0,
        val isMute: Boolean = false,
    ) {
        val level: Int
            get() = min(10, 1 + line / 20)

        val spiritNext: Spirit
            get() = spiritReserve.firstOrNull() ?: Empty

        val isPaused
            get() = gameStatus == GameStatus.Paused

        val isRuning
            get() = gameStatus == GameStatus.Running
    }

}

sealed interface Action {
    data class Move(val direction: Direction) : Action
    object Reset : Action
    object Pause : Action
    object Resume : Action
    object Rotate : Action
    object Drop : Action
    object GameTick : Action
    object Mute : Action
}

enum class GameStatus {
    Onboard, //游戏欢迎页
    Running, //游戏进行中
    LineClearing,// 消行动画中
    Paused,//游戏暂停
    ScreenClearing, //清屏动画中
    GameOver//游戏结束
}


private const val MatrixWidth = 12
private const val MatrixHeight = 24