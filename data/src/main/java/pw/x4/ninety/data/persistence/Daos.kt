package pw.x4.ninety.data.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id")
    suspend fun readAll(): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(values: List<ProfileEntity>)

    @Query("DELETE FROM profiles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes ORDER BY id")
    suspend fun readAll(): List<NodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(values: List<NodeEntity>)

    @Query("DELETE FROM nodes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM nodes")
    suspend fun deleteAll()
}
