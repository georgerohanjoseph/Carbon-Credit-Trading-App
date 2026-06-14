package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "carbon_listings")
data class CarbonCreditListing(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val sellerType: String, // "Farmer", "Forest Department", "Community Cooperative"
    val sellerName: String,
    val location: String,
    val availableCredits: Double, // in Metric Tons of CO2 equivalent (tCO2e)
    val pricePerCreditUSD: Double, // base price in USD
    val co2OffsetType: String, // "Blue Carbon (Mangroves)", "Agroforestry", "Soil Sequestration", "Afforestation"
    val description: String,
    val vintage: String, // e.g. "2024", "2025"
    val standard: String, // "VCS (Verra)", "Gold Standard", "Plan Vivo"
    val thumbnailId: String // Identifier or icon name for UI
)

@Entity(tableName = "carbon_transactions")
data class CarbonTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val listingId: Int,
    val projectTitle: String,
    val sellerName: String,
    val creditsPurchased: Double,
    val pricePerCreditNative: Double,
    val totalPriceNative: Double,
    val currencyUsed: String, // "INR", "USD", "AUD", "NZD", "EUR"
    val exchangeRateToUSD: Double, // Rate used at purchase (1 base Currency = X USD or vice versa)
    val usdEquivalentAmount: Double,
    val timestamp: Long,
    val gatewayTxId: String, // e.g. "TXN_MULTI_982182"
    val status: String // "PENDING", "SETTLED", "RETIRED"
)

@Entity(tableName = "wallet_balances")
data class WalletBalance(
    @PrimaryKey val currencyCode: String, // "INR", "USD", "AUD", "NZD", "EUR"
    val balance: Double
)

@Entity(tableName = "emission_profiles")
data class EmissionProfile(
    @PrimaryKey val id: Int = 1, // Single profile for the local industrial user
    val industryName: String,
    val sector: String, // "Manufacturing", "Power Generation", "Logistics", "Chemicals", "Other"
    val energyMWh: Double, // yearly electricity
    val coalTons: Double,  // yearly coal consumed
    val fuelLiters: Double, // yearly logistics fuel
    val targetOffsetPercentage: Int // e.g. 50%, 100%
)
