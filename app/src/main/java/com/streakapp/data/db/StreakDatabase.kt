package com.streakapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.streakapp.data.model.Habit
import com.streakapp.data.model.HabitCompletion

@Database(
    entities = [Habit::class, HabitCompletion::class],
    version = 2,
    exportSchema = false
)
abstract class StreakDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile
        private var INSTANCE: StreakDatabase? = null

        fun getDatabase(context: Context): StreakDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StreakDatabase::class.java,
                    "streak_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
