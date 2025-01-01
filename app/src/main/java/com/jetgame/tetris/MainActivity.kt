package com.jetgame.tetris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jetgame.tetris.logic.*
import com.jetgame.tetris.ui.GameBody
import com.jetgame.tetris.ui.GameScreen
import com.jetgame.tetris.ui.PreviewGamescreen
import com.jetgame.tetris.ui.combinedClickable
import com.jetgame.tetris.ui.theme.ComposetetrisTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtil.transparentStatusBar(this)
        SoundUtil.init(this)

        setContent {
            ComposetetrisTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {

                    val viewModel = viewModel<GameViewModel>()
                    val viewState = viewModel.viewState.value

                    LaunchedEffect(key1 = Unit) {
                        while (isActive) {
                            delay(650L - 55 * (viewState.level - 1))
                            viewModel.dispatch(Action.GameTick)
                        }
                    }

                    // 细腻，通过DisposableEffect（能监听到Composable的销毁）附带作用的运用，监听生命周期的变化
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(key1 = Unit) {
                        val observer = object : DefaultLifecycleObserver {
                            override fun onResume(owner: LifecycleOwner) {
                                viewModel.dispatch(Action.Resume)
                            }

                            override fun onPause(owner: LifecycleOwner) {
                                viewModel.dispatch(Action.Pause)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }


                    GameBody(combinedClickable(
                        onMove = { direction: Direction ->
                            if (direction == Direction.Up) viewModel.dispatch(Action.Drop)
                            else viewModel.dispatch(Action.Move(direction))
                        },
                        onRotate = {
                            viewModel.dispatch(Action.Rotate)
                        },
                        onRestart = {
                            viewModel.dispatch(Action.Reset)
                        },
                        onPause = {
                            if (viewModel.viewState.value.isRuning) {
                                viewModel.dispatch(Action.Pause)
                            } else {
                                viewModel.dispatch(Action.Resume)
                            }
                        },
                        onMute = {
                            viewModel.dispatch(Action.Mute)
                        }
                    )) {
                        GameScreen(
                            Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        SoundUtil.release()
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    ComposetetrisTheme {
        GameBody {
            PreviewGamescreen(Modifier.fillMaxSize())
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview2() {
    Column(modifier = Modifier.height(240.dp)) {
        /**
         * modifier修饰符的作用是按链式处理依次生效的，前者的优先级大于后者
         */
        // 这里的100.dp写在20.dp的后面，并不会覆盖前者，仍然是以前者的大小为准
        Box(modifier = Modifier.size(20.dp).size(100.dp).background(Color.Blue)) {
        }

        Box(modifier = Modifier.size(100.dp).size(20.dp).background(Color.Green)) {
        }
        // 这里是在ColumnScope的作用域下设置weight(1f)，限制的是高度（剩余空间留给它）并没有限制宽度，所以size的设置仅仅可以影响到宽度
        Box(modifier = Modifier.weight(1f).size(10.dp).background(Color.Red)) {
        }
    }
}