package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class AppRepository(private val db: AppDatabase) {

    private val listingDao = db.listingDao()
    private val transactionDao = db.transactionDao()
    private val walletDao = db.walletDao()
    private val emissionDao = db.emissionDao()

    val listingsFlow: Flow<List<CarbonCreditListing>> = listingDao.getAllListings()
    val transactionsFlow: Flow<List<CarbonTransaction>> = transactionDao.getAllTransactions()
    val balancesFlow: Flow<List<WalletBalance>> = walletDao.getAllBalances()
    val emissionProfileFlow: Flow<EmissionProfile?> = emissionDao.getEmissionProfileFlow()

    suspend fun getEmissionProfile() = emissionDao.getEmissionProfile()

    suspend fun seedDatabaseIfEmpty() {
        // Core Currencies: "INR", "USD", "AUD", "NZD", "EUR"
        val existingBalances = walletDao.getAllBalances().first()
        if (existingBalances.isEmpty()) {
            val defaultBalances = listOf(
                WalletBalance("INR", 10000000.0), // 1 Crore Rupees (10 Million INR) for Industry
                WalletBalance("USD", 250000.0),   // $250k
                WalletBalance("EUR", 150000.0),   // €150k
                WalletBalance("AUD", 100000.0),   // A$100k
                WalletBalance("NZD", 80000.0)     // NZ$80k
            )
            walletDao.insertBalances(defaultBalances)
        }

        val existingProfile = emissionDao.getEmissionProfile()
        if (existingProfile == null) {
            val defaultProfile = EmissionProfile(
                id = 1,
                industryName = "Deccan Green Steel & Cement K.K.",
                sector = "Manufacturing & Infra",
                energyMWh = 2450.0,   // MWh of electricity
                coalTons = 4800.0,    // Tons of coal burned
                fuelLiters = 120000.0, // Liters of transport diesel
                targetOffsetPercentage = 100
            )
            emissionDao.insertOrUpdateProfile(defaultProfile)
        }

        val existingListings = listingDao.getAllListings().first()
        if (existingListings.isEmpty()) {
            val defaultListings = listOf(
                CarbonCreditListing(
                    title = "Sunderbans Blue Carbon Recovery",
                    sellerType = "Forest Department",
                    sellerName = "Sunderbans Wildlife & Mangrove Authority",
                    location = "India (West Bengal)",
                    availableCredits = 85000.0,
                    pricePerCreditUSD = 18.0, // ~1,500 INR
                    co2OffsetType = "Blue Carbon (Mangroves)",
                    vintage = "2024",
                    standard = "VCS (Verra)",
                    thumbnailId = "mangrove",
                    description = "Protects critical mangrove salt-swamps in coastal Bengal. Mangroves capture carbon at rates up to 10x faster than tropical rainforests. Also provides storm defense and turtle breeding habitats."
                ),
                CarbonCreditListing(
                    title = "Himalayan Smallholders Tree-Intercropping",
                    sellerType = "Farmer",
                    sellerName = "Himachal Regenerative Orchards Association",
                    location = "India (Himachal Pradesh)",
                    availableCredits = 15200.0,
                    pricePerCreditUSD = 12.0, // ~1,000 INR
                    co2OffsetType = "Agroforestry",
                    vintage = "2025",
                    standard = "Plan Vivo",
                    thumbnailId = "agroforestry",
                    description = "Incentivizes over 1,200 mountain apple growers to plant native pine and nitrogen-fixing trees on barren boundary edges, driving carbon assimilation, soil health preservation, and extra farmer revenues."
                ),
                CarbonCreditListing(
                    title = "Queensland Eucalyptus Canopy Expansion",
                    sellerType = "Forest Department",
                    sellerName = "Queensland Forestry Directorate",
                    location = "Australia",
                    availableCredits = 62000.0,
                    pricePerCreditUSD = 28.0, // ~43 AUD
                    co2OffsetType = "Afforestation",
                    vintage = "2024",
                    standard = "ACCU Scheme",
                    thumbnailId = "eucalyptus",
                    description = "Large-scale afforestation and natural regeneration on heavily cleared cattle ranches. Re-establishes ecological pathways for koala species while conducting multi-year physical trunk volume biometrics."
                ),
                CarbonCreditListing(
                    title = "Rondônia Tribal Amazon Protection",
                    sellerType = "Community Cooperative",
                    sellerName = "Paiter Suruí Indigenous Forest Alliance",
                    location = "Brazil",
                    availableCredits = 145000.0,
                    pricePerCreditUSD = 14.5, // ~13.3 EUR
                    co2OffsetType = "Afforestation (REDD+)",
                    vintage = "2025",
                    standard = "Gold Standard",
                    thumbnailId = "amazon",
                    description = "Led by Indigenous guards utilizing satellite remote sensing to combat illegal logging in tribal ancestral territories, avoiding huge-scale carbon deforestation."
                ),
                CarbonCreditListing(
                    title = "Canterbury High-Density Pasture Sequestration",
                    sellerType = "Farmer",
                    sellerName = "Canterbury Plains Regenerative Farm Hub",
                    location = "New Zealand",
                    availableCredits = 28400.0,
                    pricePerCreditUSD = 21.0, // ~34.2 NZD
                    co2OffsetType = "Soil Sequestration",
                    vintage = "2025",
                    standard = "Gold Standard",
                    thumbnailId = "pasture",
                    description = "Promotes multi-species, deep-root pasture combinations, cover cropping, and planned cell-grazing. Results in robust soil-organic matter and verified direct organic carbon retention."
                )
            )
            listingDao.insertListings(defaultListings)
        }
    }

    suspend fun saveEmissionProfile(profile: EmissionProfile) {
        emissionDao.insertOrUpdateProfile(profile)
    }

    suspend fun addWalletBalance(currency: String, amount: Double) {
        walletDao.creditBalance(currency, amount)
    }

    suspend fun processPurchase(
        listingId: Int,
        creditsToBuy: Double,
        currencyCode: String,
        nativePricePerCredit: Double,
        rateToUSD: Double
    ): Boolean {
        val listing = listingDao.getListingById(listingId) ?: return false
        if (listing.availableCredits < creditsToBuy) return false

        val nativeTotal = creditsToBuy * nativePricePerCredit
        val currentBalance = walletDao.getBalanceByCurrency(currencyCode) ?: 0.0
        if (currentBalance < nativeTotal) return false

        // Deduct from wallet
        walletDao.debitBalance(currencyCode, nativeTotal)

        // Deduct available credits
        val remainingCredits = listing.availableCredits - creditsToBuy
        listingDao.updateAvailableCredits(listingId, remainingCredits)

        // Calculate USD equivalent
        // If native currency is e.g. INR and rate is 1 USD = 83.5 INR, then USD = nativeAmount / 83.5
        // Let's pass rateToUSD as exchange rate where: 1 base native unit = X USD (e.g. 1 INR = 0.012 USD)
        val usdEquivalent = nativeTotal * rateToUSD

        // Log transaction
        val tx = CarbonTransaction(
            listingId = listingId,
            projectTitle = listing.title,
            sellerName = listing.sellerName,
            creditsPurchased = creditsToBuy,
            pricePerCreditNative = nativePricePerCredit,
            totalPriceNative = nativeTotal,
            currencyUsed = currencyCode,
            exchangeRateToUSD = rateToUSD,
            usdEquivalentAmount = usdEquivalent,
            timestamp = System.currentTimeMillis(),
            gatewayTxId = "ECO-${currencyCode}-${UUID.randomUUID().toString().take(8).uppercase()}",
            status = "SETTLED"
        )
        transactionDao.insertTransaction(tx)
        return true
    }
}
