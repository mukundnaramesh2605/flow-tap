package com.flowtap.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [FlowEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class FlowTapDatabase : RoomDatabase() {
    abstract fun flowDao(): FlowDao
}
