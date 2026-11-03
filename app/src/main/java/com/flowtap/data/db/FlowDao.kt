package com.flowtap.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FlowDao {
    @Query("SELECT * FROM flows ORDER BY position, id")
    fun observeAll(): Flow<List<FlowEntity>>

    @Query("SELECT * FROM flows WHERE id = :id")
    fun observe(id: Long): Flow<FlowEntity?>

    @Query("SELECT * FROM flows ORDER BY position, id")
    suspend fun getAll(): List<FlowEntity>

    @Query("SELECT * FROM flows WHERE id = :id")
    suspend fun get(id: Long): FlowEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM flows")
    suspend fun maxPosition(): Int

    @Insert
    suspend fun insert(entity: FlowEntity): Long

    @Update
    suspend fun update(entity: FlowEntity)

    @Query("DELETE FROM flows WHERE id = :id")
    suspend fun delete(id: Long)

    /** Appends [entities] after the existing flows, in order. Ids are assigned fresh. */
    @Transaction
    suspend fun appendAll(entities: List<FlowEntity>): List<Long> {
        var position = maxPosition()
        return entities.map { insert(it.copy(id = 0, position = ++position)) }
    }
}
