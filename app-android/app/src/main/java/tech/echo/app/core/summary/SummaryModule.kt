package tech.echo.app.core.summary

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SummaryModule {

    @Binds
    abstract fun bindLlmClient(client: DeepSeekLlmClient): LlmClient
}
