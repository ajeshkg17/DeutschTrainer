package com.example.deutschtrainer

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="profiles") data class ProfileEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val name:String,val createdAt:Long=System.currentTimeMillis())
@Entity(tableName="questions") data class QuestionEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val english:String,val level:String,val topic:String,val focus:String,val createdAt:Long=System.currentTimeMillis())
@Entity(tableName="attempts",foreignKeys=[ForeignKey(entity=QuestionEntity::class,parentColumns=["id"],childColumns=["questionId"],onDelete=ForeignKey.CASCADE),ForeignKey(entity=ProfileEntity::class,parentColumns=["id"],childColumns=["profileId"],onDelete=ForeignKey.CASCADE)],indices=[Index("questionId"),Index("profileId")])
data class AttemptEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val questionId:Long,val profileId:Long=1,val answer:String,val score:Int,val correctedTranslation:String,val shortFeedback:String,val mistakesJson:String,val improveJson:String,val grammarTopicsJson:String,val createdAt:Long=System.currentTimeMillis())
data class AttemptWithQuestion(@Embedded val attempt:AttemptEntity,@Relation(parentColumn="questionId",entityColumn="id") val question:QuestionEntity)

@Dao interface QuizDao {
 @Query("SELECT * FROM profiles ORDER BY id ASC") fun profiles():Flow<List<ProfileEntity>>
 @Insert suspend fun insertProfile(profile:ProfileEntity):Long
 @Query("SELECT * FROM questions ORDER BY id DESC") fun questions():Flow<List<QuestionEntity>>
 @Transaction @Query("SELECT * FROM attempts WHERE profileId = :profileId ORDER BY id DESC") fun attempts(profileId:Long):Flow<List<AttemptWithQuestion>>
 @Insert suspend fun insertQuestion(q:QuestionEntity):Long
 @Insert suspend fun insertAttempt(a:AttemptEntity):Long
}

@Database(entities=[ProfileEntity::class,QuestionEntity::class,AttemptEntity::class],version=2,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){abstract fun quizDao():QuizDao
 companion object{
  @Volatile private var INSTANCE:AppDatabase?=null
  private val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("CREATE TABLE IF NOT EXISTS `profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)");db.execSQL("INSERT OR IGNORE INTO `profiles` (`id`,`name`,`createdAt`) VALUES (1,'Default',strftime('%s','now') * 1000)");db.execSQL("ALTER TABLE `attempts` ADD COLUMN `profileId` INTEGER NOT NULL DEFAULT 1");db.execSQL("CREATE INDEX IF NOT EXISTS `index_attempts_profileId` ON `attempts` (`profileId`)")}}
  fun get(context:Context):AppDatabase=INSTANCE?:synchronized(this){INSTANCE?:Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"deutsch_trainer.db").addMigrations(MIGRATION_1_2).build().also{INSTANCE=it}}
 }
}
