package com.example.deutschtrainer

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val english: String,
    val level: String,
    val topic: String,
    val focus: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "quiz_sets",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class QuizSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val title: String,
    val level: String,
    val topic: String,
    val focus: String,
    val questionIdsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attempts",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("questionId"), Index("profileId"), Index("quizSetId")]
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val profileId: Long = 1,
    val quizSetId: Long? = null,
    val answer: String,
    val score: Int,
    val correctedTranslation: String,
    val shortFeedback: String,
    val mistakesJson: String,
    val improveJson: String,
    val grammarTopicsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class AttemptWithQuestion(
    @Embedded val attempt: AttemptEntity,
    @Relation(parentColumn = "questionId", entityColumn = "id") val question: QuestionEntity
)

@Dao
interface QuizDao {
    @Query("SELECT * FROM profiles ORDER BY id ASC")
    fun profiles(): Flow<List<ProfileEntity>>

    @Insert
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun profileSnapshot(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles ORDER BY id ASC")
    suspend fun profilesSnapshot(): List<ProfileEntity>

    @Query("SELECT * FROM questions ORDER BY id DESC")
    fun questions(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions ORDER BY id ASC")
    suspend fun questionsSnapshot(): List<QuestionEntity>

    @Insert
    suspend fun insertQuestion(q: QuestionEntity): Long

    @Transaction
    @Query("SELECT * FROM attempts WHERE profileId = :profileId ORDER BY id DESC")
    fun attempts(profileId: Long): Flow<List<AttemptWithQuestion>>

    @Query("SELECT * FROM attempts WHERE profileId = :profileId ORDER BY createdAt ASC, id ASC")
    suspend fun attemptsSnapshot(profileId: Long): List<AttemptEntity>

    @Insert
    suspend fun insertAttempt(a: AttemptEntity): Long

    @Query("SELECT * FROM quiz_sets WHERE profileId = :profileId ORDER BY createdAt DESC, id DESC")
    fun quizSets(profileId: Long): Flow<List<QuizSetEntity>>

    @Query("SELECT * FROM quiz_sets WHERE profileId = :profileId ORDER BY createdAt ASC, id ASC")
    suspend fun quizSetsSnapshot(profileId: Long): List<QuizSetEntity>

    @Insert
    suspend fun insertQuizSet(quizSet: QuizSetEntity): Long

    @Query("SELECT COUNT(*) FROM quiz_sets WHERE profileId = :profileId")
    suspend fun quizSetCount(profileId: Long): Int

    @Query("DELETE FROM quiz_sets WHERE id = :id")
    suspend fun deleteQuizSet(id: Long)
}

@Database(
    entities = [ProfileEntity::class, QuestionEntity::class, AttemptEntity::class, QuizSetEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun quizDao(): QuizDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `profiles` (`id`,`name`,`createdAt`) VALUES (1,'Default',strftime('%s','now') * 1000)"
                )
                db.execSQL("ALTER TABLE `attempts` ADD COLUMN `profileId` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attempts_profileId` ON `attempts` (`profileId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quiz_sets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`profileId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`level` TEXT NOT NULL, " +
                        "`topic` TEXT NOT NULL, " +
                        "`focus` TEXT NOT NULL, " +
                        "`questionIdsJson` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_sets_profileId` ON `quiz_sets` (`profileId`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `attempts` ADD COLUMN `quizSetId` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attempts_quizSetId` ON `attempts` (`quizSetId`)")
            }
        }

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "deutsch_trainer.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { INSTANCE = it }
        }
    }
}
