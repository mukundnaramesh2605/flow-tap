package com.flowtap.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flowtap.data.SampleFlows
import com.flowtap.data.db.FlowDao
import com.flowtap.data.db.FlowTapDatabase
import com.flowtap.data.db.toEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        dao: Provider<FlowDao>,
    ): FlowTapDatabase =
        Room.databaseBuilder(context, FlowTapDatabase::class.java, "flowtap.db")
            .addCallback(object : RoomDatabase.Callback() {
                // Runs once per install, so deleting the samples later doesn't bring them back.
                override fun onCreate(db: SupportSQLiteDatabase) {
                    scope.launch {
                        dao.get().appendAll(SampleFlows.all.map { it.toEntity(position = 0) })
                    }
                }
            })
            .build()

    @Provides
    fun provideFlowDao(db: FlowTapDatabase): FlowDao = db.flowDao()
}
