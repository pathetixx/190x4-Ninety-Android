package pw.x4.ninety.data.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class, NodeEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NinetyDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun nodeDao(): NodeDao

    companion object {
        fun create(context: Context): NinetyDatabase = Room.databaseBuilder(
            context.applicationContext,
            NinetyDatabase::class.java,
            "ninety.db",
        ).build()
    }
}
