package app.mlino.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CardE::class, TxE::class, RawE::class, CategoryE::class, RuleE::class], version = 1, exportSchema = true)
abstract class Db : RoomDatabase() {
    abstract fun cards(): CardDao
    abstract fun tx(): TxDao
    abstract fun raw(): RawDao
    abstract fun categories(): CategoryDao
    abstract fun rules(): RuleDao

    companion object {
        fun create(ctx: Context): Db = Room.databaseBuilder(ctx, Db::class.java, "mlino.db").build()
    }
}
