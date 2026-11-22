package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AccountRuleEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.DebtEntity
import com.example.data.model.GoalEntity
import com.example.data.model.InvestmentEntity
import com.example.data.model.LearnedMappingEntity
import com.example.data.model.RecurringExpenseEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date >= :startTime AND date <= :endTime ORDER BY date DESC, id DESC")
    fun getTransactionsBetween(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    suspend fun getAllTransactionsSnapshot(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface AccountRuleDao {
    @Query("SELECT * FROM account_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<AccountRuleEntity>>

    @Query("SELECT * FROM account_rules WHERE isEnabled = 1")
    suspend fun getActiveRulesSnapshot(): List<AccountRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AccountRuleEntity): Long

    @Update
    suspend fun updateRule(rule: AccountRuleEntity)

    @Delete
    suspend fun deleteRule(rule: AccountRuleEntity)

    @Query("DELETE FROM account_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgetsSnapshot(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBudget(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE categoryName = :categoryName")
    suspend fun deleteByCategory(categoryName: String)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM financial_goals")
    fun getAllGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM financial_goals")
    suspend fun getAllGoalsSnapshot(): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity): Long

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Delete
    suspend fun deleteGoal(goal: GoalEntity)
}

@Dao
interface RecurringExpenseDao {
    @Query("SELECT * FROM recurring_expenses ORDER BY amount DESC")
    fun getAllRecurring(): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses ORDER BY amount DESC")
    suspend fun getAllRecurringSnapshot(): List<RecurringExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurring(recurring: RecurringExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<RecurringExpenseEntity>)

    @Update
    suspend fun updateRecurring(recurring: RecurringExpenseEntity)

    @Delete
    suspend fun deleteRecurring(recurring: RecurringExpenseEntity)
}

@Dao
interface InvestmentDao {
    @Query("SELECT * FROM investments ORDER BY date DESC")
    fun getAllInvestments(): Flow<List<InvestmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvestment(investment: InvestmentEntity): Long

    @Update
    suspend fun updateInvestment(investment: InvestmentEntity)

    @Delete
    suspend fun deleteInvestment(investment: InvestmentEntity)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfileSnapshot(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: UserProfileEntity)
}

@Dao
interface LearnedMappingDao {
    @Query("SELECT * FROM learned_mappings ORDER BY lastUpdated DESC")
    fun observeAllLearnedMappings(): Flow<List<LearnedMappingEntity>>

    @Query("SELECT * FROM learned_mappings ORDER BY lastUpdated DESC")
    suspend fun getAllLearnedMappings(): List<LearnedMappingEntity>

    @Query("SELECT * FROM learned_mappings WHERE merchantNormalized = :merchant LIMIT 1")
    suspend fun getMappingForMerchant(merchant: String): LearnedMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: LearnedMappingEntity)

    @Query("DELETE FROM learned_mappings WHERE merchantNormalized = :merchant")
    suspend fun deleteByMerchant(merchant: String)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY annualInterestRate DESC, currentBalance DESC")
    fun getAllDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts ORDER BY annualInterestRate DESC, currentBalance DESC")
    suspend fun getAllDebtsSnapshot(): List<DebtEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(debts: List<DebtEntity>)

    @Update
    suspend fun updateDebt(debt: DebtEntity)

    @Delete
    suspend fun deleteDebt(debt: DebtEntity)
}

