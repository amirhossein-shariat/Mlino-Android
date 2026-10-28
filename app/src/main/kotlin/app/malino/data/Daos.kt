package app.malino.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY name") fun observe(): Flow<List<CardE>>
    @Query("SELECT * FROM cards") suspend fun all(): List<CardE>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(c: CardE)
    @Delete suspend fun delete(c: CardE)
}

@Dao
interface TxDao {
    @Query("SELECT * FROM tx ORDER BY at DESC, createdAt DESC") fun observe(): Flow<List<TxE>>
    @Query("SELECT * FROM tx WHERE at BETWEEN :from AND :to") suspend fun between(from: Long, to: Long): List<TxE>
    @Query("SELECT * FROM tx WHERE id = :id") suspend fun get(id: String): TxE?
    @Query("SELECT COUNT(*) FROM tx WHERE cardId = :cardId") suspend fun countForCard(cardId: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(list: List<TxE>)
    @Query("DELETE FROM tx WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface RawDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(r: RawE)
    @Query("SELECT * FROM raw WHERE state IN ('unparsed','unmapped') ORDER BY receivedAt DESC") fun observeAttention(): Flow<List<RawE>>
    @Query("SELECT * FROM raw WHERE state IN ('unparsed','unmapped')") suspend fun attention(): List<RawE>
    @Query("UPDATE raw SET state = :state, txId = :txId WHERE id = :id") suspend fun setState(id: String, state: String, txId: String?)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sort") fun observe(): Flow<List<CategoryE>>
    @Query("SELECT COUNT(*) FROM categories") suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(list: List<CategoryE>)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules") suspend fun all(): List<RuleE>
    @Query("SELECT * FROM rules WHERE `key` = :key") suspend fun get(key: String): RuleE?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(r: RuleE)
}
