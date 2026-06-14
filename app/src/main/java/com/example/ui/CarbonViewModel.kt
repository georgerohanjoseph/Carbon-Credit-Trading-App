package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CarbonViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AppRepository(db)

    // Data Flows
    val listings = repository.listingsFlow
    val transactions = repository.transactionsFlow
    val balances = repository.balancesFlow
    val emissionProfile = repository.emissionProfileFlow

    // UI state for operations
    private val _purchaseState = MutableStateFlow<PurchaseStatus>(PurchaseStatus.Idle)
    val purchaseState: StateFlow<PurchaseStatus> = _purchaseState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedDatabaseIfEmpty()
        }
    }

    // Exchange rate helpers (Base: USD = 1.0)
    fun getExchangeRateToUSD(currency: String): Double {
        return when (currency) {
            "USD" -> 1.0
            "INR" -> 1.0 / 83.50
            "AUD" -> 1.0 / 1.54
            "NZD" -> 1.0 / 1.63
            "EUR" -> 1.0 / 0.92
            else -> 1.0
        }
    }

    fun getExchangeFromUSD(currency: String): Double {
        return when (currency) {
            "USD" -> 1.0
            "INR" -> 83.50
            "AUD" -> 1.54
            "NZD" -> 1.63
            "EUR" -> 0.92
            else -> 1.0
        }
    }

    fun convertAmount(amount: Double, fromCurrency: String, toCurrency: String): Double {
        if (fromCurrency == toCurrency) return amount
        val inUSD = amount * getExchangeRateToUSD(fromCurrency)
        return inUSD * getExchangeFromUSD(toCurrency)
    }

    // Dynamic Carbon Math
    // CO2 Factors:
    // Electricity MWh -> 0.42 tCO2e
    // Coal Tons -> 2.42 tCO2e
    // Fuel Liters -> 0.00268 tCO2e
    fun calculateYearlyFootprint(profile: EmissionProfile?): Double {
        if (profile == null) return 0.0
        return (profile.energyMWh * 0.42) + (profile.coalTons * 2.42) + (profile.fuelLiters * 0.00268)
    }

    fun calculateYearlyFootprintBreakdown(profile: EmissionProfile?): Triple<Double, Double, Double> {
        if (profile == null) return Triple(0.0, 0.0, 0.0)
        return Triple(
            profile.energyMWh * 0.42,
            profile.coalTons * 2.42,
            profile.fuelLiters * 0.00268
        )
    }

    fun updateEmissionProfile(
        industryName: String,
        sector: String,
        energyMWh: Double,
        coalTons: Double,
        fuelLiters: Double,
        targetOffsetPercentage: Int
    ) {
        viewModelScope.launch {
            val updated = EmissionProfile(
                id = 1,
                industryName = industryName,
                sector = sector,
                energyMWh = energyMWh,
                coalTons = coalTons,
                fuelLiters = fuelLiters,
                targetOffsetPercentage = targetOffsetPercentage
            )
            repository.saveEmissionProfile(updated)
        }
    }

    fun addFunds(currency: String, amount: Double) {
        viewModelScope.launch {
            repository.addWalletBalance(currency, amount)
        }
    }

    fun clearPurchaseStatus() {
        _purchaseState.value = PurchaseStatus.Idle
    }

    fun executeCarbonTrade(
        listingId: Int,
        creditsToBuy: Double,
        currencyCode: String,
        pricePerCreditUSD: Double
    ) {
        viewModelScope.launch {
            _purchaseState.value = PurchaseStatus.Processing

            // Let it mock gateway delay for high visual fidelity
            kotlinx.coroutines.delay(1800)

            val nativePricePerCredit = pricePerCreditUSD * getExchangeFromUSD(currencyCode)
            val rateToUSD = getExchangeRateToUSD(currencyCode)

            val success = repository.processPurchase(
                listingId = listingId,
                creditsToBuy = creditsToBuy,
                currencyCode = currencyCode,
                nativePricePerCredit = nativePricePerCredit,
                rateToUSD = rateToUSD
            )

            if (success) {
                _purchaseState.value = PurchaseStatus.Success(
                    credits = creditsToBuy,
                    currency = currencyCode,
                    total = creditsToBuy * nativePricePerCredit
                )
            } else {
                _purchaseState.value = PurchaseStatus.Error("Insufficient wallet funds or credits no longer available.")
            }
        }
    }
}

sealed interface PurchaseStatus {
    object Idle : PurchaseStatus
    object Processing : PurchaseStatus
    data class Success(val credits: Double, val currency: String, val total: Double) : PurchaseStatus
    data class Error(val message: String) : PurchaseStatus
}

class CarbonViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CarbonViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CarbonViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
