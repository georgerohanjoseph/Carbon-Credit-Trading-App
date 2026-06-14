package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CarbonCreditListingDao {
    @Query("SELECT * FROM carbon_listings ORDER BY id DESC")
    fun getAllListings(): Flow<List<CarbonCreditListing>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListings(listings: List<CarbonCreditListing>)

    @Update
    suspend fun updateListing(listing: CarbonCreditListing)

    @Query("SELECT * FROM carbon_listings WHERE id = :id")
    suspend fun getListingById(id: Int): CarbonCreditListing?

    @Query("UPDATE carbon_listings SET availableCredits = :credits WHERE id = :id")
    suspend fun updateAvailableCredits(id: Int, credits: Double)
}

@Dao
interface CarbonTransactionDao {
    @Query("SELECT * FROM carbon_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<CarbonTransaction>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: CarbonTransaction)
}

@Dao
interface WalletBalanceDao {
    @Query("SELECT * FROM wallet_balances")
    fun getAllBalances(): Flow<List<WalletBalance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalances(balances: List<WalletBalance>)

    @Query("UPDATE wallet_balances SET balance = balance + :amount WHERE currencyCode = :currency")
    suspend fun creditBalance(currency: String, amount: Double)

    @Query("UPDATE wallet_balances SET balance = balance - :amount WHERE currencyCode = :currency")
    suspend fun debitBalance(currency: String, amount: Double)

    @Query("SELECT balance FROM wallet_balances WHERE currencyCode = :currency")
    suspend fun getBalanceByCurrency(currency: String): Double?
}

@Dao
interface EmissionProfileDao {
    @Query("SELECT * FROM emission_profiles WHERE id = 1")
    fun getEmissionProfileFlow(): Flow<EmissionProfile?>

    @Query("SELECT * FROM emission_profiles WHERE id = 1")
    suspend fun getEmissionProfile(): EmissionProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: EmissionProfile)
}
