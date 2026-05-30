package tech.echo.app.core.audio

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 录音层 Hilt 模块：把 [RecordingController] 接口绑定到真实实现。
 *
 * UI 层只注入接口；要换回 Fake 调试时改这里一行即可。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindRecordingController(impl: RealRecordingController): RecordingController
}
