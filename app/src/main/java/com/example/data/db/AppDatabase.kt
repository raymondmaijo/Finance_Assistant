package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.AccountRuleDao
import com.example.data.dao.BudgetDao
import com.example.data.dao.DebtDao
import com.example.data.dao.GoalDao
import com.example.data.dao.InvestmentDao
import com.example.data.dao.LearnedMappingDao
import com.example.data.dao.RecurringExpenseDao
import com.example.data.dao.TransactionDao
import com.example.data.dao.UserProfileDao
import com.example.data.model.AccountRuleEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.DebtEntity
import com.example.data.model.GoalEntity
import com.example.data.model.InvestmentEntity
import com.example.data.model.LearnedMappingEntity
import com.example.data.model.RecurringExpenseEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserProfileEntity

@Database(
    entities = [
        TransactionEntity::class,
        AccountRuleEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        RecurringExpenseEntity::class,
        InvestmentEntity::class,
        UserProfileEntity::class,
        LearnedMappingEntity::class,
        DebtEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountRuleDao(): AccountRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun investmentDao(): InvestmentDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun learnedMappingDao(): LearnedMappingDao
    abstract fun debtDao(): DebtDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "campusfin_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
