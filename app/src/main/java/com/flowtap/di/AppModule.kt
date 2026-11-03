package com.flowtap.di

import com.flowtap.data.FlowRepository
import com.flowtap.data.RoomFlowRepository
import com.flowtap.data.run.PreviewFlowRunner
import com.flowtap.domain.run.FlowRunner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindFlowRepository(impl: RoomFlowRepository): FlowRepository

    @Binds
    abstract fun bindFlowRunner(impl: PreviewFlowRunner): FlowRunner
}
