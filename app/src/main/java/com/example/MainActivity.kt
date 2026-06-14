package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

enum class AppTab(val title: String) {
    MARKET("Marketplace"),
    CALCULATOR("Footprint"),
    PORTFOLIO("My Ledger"),
    WALLETS("Wallets")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: CarbonViewModel = viewModel(
        factory = CarbonViewModelFactory(application)
    )

    // Data Subscription
    val listings by viewModel.listings.collectAsStateWithLifecycle(initialValue = emptyList())
    val transactions by viewModel.transactions.collectAsStateWithLifecycle(initialValue = emptyList())
    val balances by viewModel.balances.collectAsStateWithLifecycle(initialValue = emptyList())
    val profile by viewModel.emissionProfile.collectAsStateWithLifecycle(initialValue = null)
    val purchaseState by viewModel.purchaseState.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(AppTab.MARKET) }
    var selectedListingForDetail by remember { mutableStateOf<CarbonCreditListing?>(null) }
    var showWireDepositDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Carbon logo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CARBON TRADE",
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(
                        onClick = { showWireDepositDialog = true },
                        modifier = Modifier.testTag("wire_deposit_header_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Simulate Deposit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        label = { Text(tab.title) },
                        icon = {
                            val icon = when (tab) {
                                AppTab.MARKET -> Icons.Default.Search
                                AppTab.CALCULATOR -> Icons.Default.Settings
                                AppTab.PORTFOLIO -> Icons.Default.List
                                AppTab.WALLETS -> Icons.Default.Person
                            }
                            Icon(imageVector = icon, contentDescription = tab.title)
                        },
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    AppTab.MARKET -> MarketplaceTab(
                        listings = listings,
                        balances = balances,
                        viewModel = viewModel,
                        onListingSelected = { selectedListingForDetail = it }
                    )
                    AppTab.CALCULATOR -> CalculatorTab(
                        profile = profile,
                        viewModel = viewModel,
                        listings = listings,
                        onBuyShortcut = { listing ->
                            selectedListingForDetail = listing
                        }
                    )
                    AppTab.PORTFOLIO -> PortfolioTab(
                        transactions = transactions,
                        profile = profile,
                        viewModel = viewModel,
                        balances = balances
                    )
                    AppTab.WALLETS -> WalletsTab(
                        balances = balances,
                        viewModel = viewModel,
                        transactions = transactions
                    )
                }
            }

            // Purchase / Detail Modal overlay
            selectedListingForDetail?.let { listing ->
                TradingDetailsDialog(
                    listing = listing,
                    balances = balances,
                    viewModel = viewModel,
                    purchaseState = purchaseState,
                    onDismiss = {
                        selectedListingForDetail = null
                        viewModel.clearPurchaseStatus()
                    }
                )
            }

            // Simple Wire Deposit Dialog
            if (showWireDepositDialog) {
                WireDepositDialog(
                    viewModel = viewModel,
                    onDismiss = { showWireDepositDialog = false }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// MARKETPLACE TAB
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarketplaceTab(
    listings: List<CarbonCreditListing>,
    balances: List<WalletBalance>,
    viewModel: CarbonViewModel,
    onListingSelected: (CarbonCreditListing) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedOffsetTypeFilter by remember { mutableStateOf("All") }
    var selectedLocationFilter by remember { mutableStateOf("All") }

    val filterTypes = listOf("All", "Blue Carbon (Mangroves)", "Agroforestry", "Soil Sequestration", "Afforestation")
    val filterLocations = listOf("All", "India", "Australia", "Brazil", "New Zealand")

    val filteredListings = listings.filter {
        val matchesSearch = it.title.contains(searchQuery, ignoreCase = true) ||
                it.sellerName.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)

        val matchesType = selectedOffsetTypeFilter == "All" || it.co2OffsetType.contains(selectedOffsetTypeFilter, ignoreCase = true)
        val matchesLocation = selectedLocationFilter == "All" || it.location.contains(selectedLocationFilter, ignoreCase = true)

        matchesSearch && matchesType && matchesLocation
    }

    // Dynamic Balance Calculation
    val totalBalanceINR = balances.sumOf { wb ->
        viewModel.convertAmount(wb.balance, wb.currencyCode, "INR")
    }
    
    val df = DecimalFormat("#,##0.00")
    val dfShort = DecimalFormat("#,##0.0")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Professional Polish - Premium Enterprise Balance Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary // ForestDeepGreen (emerald-950)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("enterprise_balance_card"),
            shape = RoundedCornerShape(32.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "Enterprise Account Balance",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA7F3D0), // emerald-200
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "₹${df.format(totalBalanceINR)}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Light,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "INR EQVD",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Offsetted pill
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "OFFSET RETIRED",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFA7F3D0)
                            )
                            Text(
                                "12.4 kt",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    // Pending offset pill
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "UNPAID LIABILITIES",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFA7F3D0)
                            )
                            Text(
                                "2.8 kt",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search projects, developers, tags...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("search_bar"),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        // Filters UI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Filter by Technology",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = "Region",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        // Filter chips (Type)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = Int.MAX_VALUE
        ) {
            filterTypes.forEach { type ->
                FilterChip(
                    selected = selectedOffsetTypeFilter == type,
                    onClick = { selectedOffsetTypeFilter = type },
                    label = { Text(type.take(15) + if(type.length > 15) "..." else "") },
                    modifier = Modifier.testTag("chip_type_$type")
                )
            }
        }

        // Filter chips (Location)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = Int.MAX_VALUE
        ) {
            filterLocations.forEach { loc ->
                FilterChip(
                    selected = selectedLocationFilter == loc,
                    onClick = { selectedLocationFilter = loc },
                    label = { Text(loc) },
                    modifier = Modifier.testTag("chip_loc_$loc")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Professional Polish - Multi-currency settlement active Row
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)), // soft slate bg
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Overlapping currency circle badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-8).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("$", "€", "₹").forEach { sym ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(1.dp, Color(0xFFCBD5E1), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(sym, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Secure multi-currency settlement active",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569)
                    )
                }

                // Pulsing light
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Available Projects Title Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Available Projects",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF334155) // slate-700
            )
            Text(
                text = "View All",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { }
            )
        }

        // Listings List
        if (filteredListings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Empty",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No ecological projects matched your filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("market_listings"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredListings) { listing ->
                    ListingCard(
                        listing = listing,
                        viewModel = viewModel,
                        onClick = { onListingSelected(listing) }
                    )
                }
            }
        }
    }
}

@Composable
fun ListingCard(
    listing: CarbonCreditListing,
    viewModel: CarbonViewModel,
    onClick: () -> Unit
) {
    val displayPriceINR = listing.pricePerCreditUSD * viewModel.getExchangeFromUSD("INR")
    val df = DecimalFormat("#,##0.00")
    
    // Choose emoji & colors based on type
    val (emoji, containerBg, emojiTint) = when (listing.co2OffsetType) {
        "Blue Carbon (Mangroves)" -> Triple("🌊", Color(0xFFE0F2FE), Color(0xFF0284C7))
        "Agroforestry" -> Triple("🌲", Color(0xFFFEF3C7), Color(0xFFD97706))
        "Soil Sequestration" -> Triple("🌾", Color(0xFFECFDF5), Color(0xFF059669))
        else -> Triple("🌳", Color(0xFFFFF1F2), Color(0xFFE11D48))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("listing_card_${listing.id}"),
        shape = RoundedCornerShape(24.dp), // exact Polish theme specification
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)), // pure Slate borders
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Block
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(containerBg),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 28.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Info Block
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = listing.sellerName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A), // Slate-900
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${listing.location} • ${listing.co2OffsetType}",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B) // Slate-500
                )

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFECFDF5))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${df.format(listing.availableCredits)} Credits",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF047857)
                        )
                    }
                    Text(
                        text = "₹${df.format(displayPriceINR)}/t",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                }
            }

            // CTA Arrow Button matching custom HTML
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFECFDF5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Details",
                    tint = Color(0xFF047857),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// CALCULATOR TAB
// -------------------------------------------------------------
@Composable
fun CalculatorTab(
    profile: EmissionProfile?,
    viewModel: CarbonViewModel,
    listings: List<CarbonCreditListing>,
    onBuyShortcut: (CarbonCreditListing) -> Unit
) {
    if (profile == null) return

    val totalCO2 = viewModel.calculateYearlyFootprint(profile)
    var isEditing by remember { mutableStateOf(false) }

    // Input fields state
    var editIndustryName by remember(profile) { mutableStateOf(profile.industryName) }
    var editSector by remember(profile) { mutableStateOf(profile.sector) }
    var editEnergyMWh by remember(profile) { mutableStateOf(profile.energyMWh.toString()) }
    var editCoalTons by remember(profile) { mutableStateOf(profile.coalTons.toString()) }
    var editFuelLiters by remember(profile) { mutableStateOf(profile.fuelLiters.toString()) }
    var editOffsetPct by remember(profile) { mutableStateOf(profile.targetOffsetPercentage) }

    val df = DecimalFormat("#,##0.0")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Hero Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = profile.industryName.uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "Operational Footprint",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${df.format(totalCO2)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 44.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Metric Tons\nCO2e / year",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Sector: ${profile.sector}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Button(
                        onClick = { isEditing = !isEditing },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("edit_profile_toggle")
                    ) {
                        Text(if (isEditing) "Cancel View" else "Adjust Metrics")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isEditing) {
            // Edit UI Form
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_profile_form")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Customize Emissions Parameters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = editIndustryName,
                        onValueChange = { editIndustryName = it },
                        label = { Text("Industrial Firm Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("input_firm_name")
                    )

                    OutlinedTextField(
                        value = editSector,
                        onValueChange = { editSector = it },
                        label = { Text("Industrial Segment / Sector") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )

                    OutlinedTextField(
                        value = editEnergyMWh,
                        onValueChange = { editEnergyMWh = it },
                        label = { Text("Annual Electricity (MWh)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("input_energy")
                    )

                    OutlinedTextField(
                        value = editCoalTons,
                        onValueChange = { editCoalTons = it },
                        label = { Text("Annual Coal Combusted (Tons)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("input_coal")
                    )

                    OutlinedTextField(
                        value = editFuelLiters,
                        onValueChange = { editFuelLiters = it },
                        label = { Text("Annual Transport/Logistics Diesel (Liters)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("input_diesel")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Offset Target: $editOffsetPct%",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Slider(
                        value = editOffsetPct.toFloat(),
                        onValueChange = { editOffsetPct = it.toInt() },
                        valueRange = 0f..100f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth().testTag("slider_target")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.updateEmissionProfile(
                                industryName = editIndustryName,
                                sector = editSector,
                                energyMWh = editEnergyMWh.toDoubleOrNull() ?: 0.0,
                                coalTons = editCoalTons.toDoubleOrNull() ?: 0.0,
                                fuelLiters = editFuelLiters.toDoubleOrNull() ?: 0.0,
                                targetOffsetPercentage = editOffsetPct
                            )
                            isEditing = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_profile_btn")
                    ) {
                        Text("Update Carbon Parameters")
                    }
                }
            }
        } else {
            // Live Breakdown and Math Charts
            val (elCO2, coalCO2, fuelCO2) = viewModel.calculateYearlyFootprintBreakdown(profile)

            Text(
                text = "Emission Vectors (tCO2e / year)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Dynamic Custom Drawn Chart using Canvas
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(130.dp)) {
                            val totalVal = elCO2 + coalCO2 + fuelCO2
                            if (totalVal > 0) {
                                val strokeW = 18.dp.toPx()
                                val startAngle = -90f
                                val sweepEl = (elCO2 / totalVal * 360).toFloat()
                                val sweepCoal = (coalCO2 / totalVal * 360).toFloat()
                                val sweepFuel = (fuelCO2 / totalVal * 360).toFloat()

                                // Electricity (Greenish Teal)
                                drawArc(
                                    color = Color(0xFF198754),
                                    startAngle = startAngle,
                                    sweepAngle = sweepEl,
                                    useCenter = false,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                )

                                // Coal (Alert Red)
                                drawArc(
                                    color = Color(0xFFDC3545),
                                    startAngle = startAngle + sweepEl,
                                    sweepAngle = sweepCoal,
                                    useCenter = false,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                )

                                // Fuel (Orange)
                                drawArc(
                                    color = Color(0xFFFD7E14),
                                    startAngle = startAngle + sweepEl + sweepCoal,
                                    sweepAngle = sweepFuel,
                                    useCenter = false,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Chart Legends with numerical rates
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendRow(color = Color(0xFFDC3545), label = "Coal", amount = df.format(coalCO2))
                        LegendRow(color = Color(0xFF198754), label = "Electricity", amount = df.format(elCO2))
                        LegendRow(color = Color(0xFFFD7E14), label = "Fuel", amount = df.format(fuelCO2))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Indian Context Action box
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Offset Recommendations",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "To achieve rapid Indian industrial compliance, buy verified local 'Sunderbans Blue Carbon' or forest-department backed offsets:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Shortcut to a listing
                    val recommended = listings.firstOrNull { it.location.contains("India") } ?: listings.firstOrNull()
                    recommended?.let { rec ->
                        Button(
                            onClick = { onBuyShortcut(rec) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = "Buy recommended", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Instantly Trade with: ${rec.title}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LegendRow(color: Color, label: String, amount: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text("$amount t", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

// -------------------------------------------------------------
// PORTFOLIO / LEDGER TAB
// -------------------------------------------------------------
@Composable
fun PortfolioTab(
    transactions: List<CarbonTransaction>,
    profile: EmissionProfile?,
    viewModel: CarbonViewModel,
    balances: List<WalletBalance>
) {
    val totalOffset = transactions.sumOf { it.creditsPurchased }
    val yearlyEmissions = viewModel.calculateYearlyFootprint(profile)
    val ratio = if (yearlyEmissions > 0) totalOffset / yearlyEmissions else 0.0

    val df = DecimalFormat("#,##0.0")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Net-Zero Gauges (Canvas drawn arc speedometer)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "INDIVIDUAL RETIREMENT GAUGE",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeW = 12.dp.toPx()
                        // Base dial (Gray)
                        drawArc(
                            color = Color.LightGray.copy(alpha = 0.4f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )

                        // Compensated dial (Forest Green)
                        val sweepAngle = (ratio.coerceAtMost(1.0) * 270).toFloat()
                        drawArc(
                            color = Color(0xFF14A44D),
                            startAngle = 135f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(ratio * 100).toInt()}%",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Offset Achieved",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (ratio >= 1.0) "🌿 Net-Zero Certified Firm" else "🛠️ Progressing Towards Carbon Neutrality",
                    fontWeight = FontWeight.Bold,
                    color = if (ratio >= 1.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Offsetted: ${df.format(totalOffset)} of ${df.format(yearlyEmissions)} tCO2e",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Settled Cross-Border Transactions",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "No trades",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No trades recorded in ledger yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("ledger_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transactions) { tx ->
                    TransactionItem(tx = tx)
                }
            }
        }
    }
}

@Composable
fun TransactionItem(tx: CarbonTransaction) {
    val df = DecimalFormat("#,##0.00")
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth().testTag("transaction_item_${tx.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.projectTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Receipt: ${tx.gatewayTxId}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val currSymbol = when(tx.currencyUsed) {
                        "INR" -> "₹"
                        "USD" -> "$"
                        "EUR" -> "€"
                        "NZD" -> "NZ$"
                        "AUD" -> "A$"
                        else -> "$"
                    }
                    Text(
                        text = "$currSymbol${df.format(tx.totalPriceNative)}",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Offset credits retired:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        text = "${tx.creditsPurchased} Tons (tCO2e)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = dateFormat.format(Date(tx.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

// -------------------------------------------------------------
// WALLETS MANAGEMENT TAB
// -------------------------------------------------------------
@Composable
fun WalletsTab(
    balances: List<WalletBalance>,
    viewModel: CarbonViewModel,
    transactions: List<CarbonTransaction>
) {
    val df = DecimalFormat("#,##0.00")
    var selectedWalletToFund by remember { mutableStateOf("INR") }
    var fundAmountIn by remember { mutableStateOf("1000000") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Your Multi-Currency Funds",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Wallet grid rendering
        balances.forEach { wb ->
            val currSymbol = when(wb.currencyCode) {
                "INR" -> "₹"
                "USD" -> "$"
                "EUR" -> "€"
                "NZD" -> "NZ$"
                "AUD" -> "A$"
                else -> "$"
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("wallet_card_${wb.currencyCode}"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currSymbol,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = wb.currencyCode,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Text(
                        text = "$currSymbol${df.format(wb.balance)}",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Wire In simulation console
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().testTag("simulate_wire_console")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Deposit Sandbox Simulation",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Instantly deposit dummy corporate funds via mock cross-border swift wire to check trading gateway capabilities:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Currency selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("INR", "USD", "EUR", "AUD", "NZD").forEach { cur ->
                        OutlinedButton(
                            onClick = { selectedWalletToFund = cur },
                            colors = if (selectedWalletToFund == cur) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            modifier = Modifier.weight(1f).testTag("fund_sel_$cur")
                        ) {
                            Text(cur, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = fundAmountIn,
                    onValueChange = { fundAmountIn = it },
                    label = { Text("Transfer Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("fund_amount_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val amt = fundAmountIn.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            viewModel.addFunds(selectedWalletToFund, amt)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("add_funds_btn")
                ) {
                    Text("Execute Sandbox Wire Transfer")
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECURE PAYMENTS DETAILS & TRADE OVERLAY DIALOG
// -------------------------------------------------------------
@Composable
fun TradingDetailsDialog(
    listing: CarbonCreditListing,
    balances: List<WalletBalance>,
    viewModel: CarbonViewModel,
    purchaseState: PurchaseStatus,
    onDismiss: () -> Unit
) {
    var quantityString by remember { mutableStateOf("100") }
    var selectedPaymentCurrency by remember { mutableStateOf("INR") }

    val doubleQty = quantityString.toDoubleOrNull() ?: 0.0
    val costPriceUSD = listing.pricePerCreditUSD
    val currencyMultiplier = viewModel.getExchangeFromUSD(selectedPaymentCurrency)

    val nativePricePerCredit = costPriceUSD * currencyMultiplier
    val nativeTotal = nativePricePerCredit * doubleQty
    val userBalanceNative = balances.firstOrNull { it.currencyCode == selectedPaymentCurrency }?.balance ?: 0.0

    val df = DecimalFormat("#,##0.00")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("trade_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = listing.title,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${listing.co2OffsetType} • ${listing.standard}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_trade_dialog")) {
                        Icon(Icons.Default.Close, contentDescription = "Close details")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = listing.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                when (purchaseState) {
                    is PurchaseStatus.Idle -> {
                        Text(
                            text = "Trade Configuration",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = quantityString,
                            onValueChange = { quantityString = it },
                            label = { Text("Metric Tons requested (tCO2e)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("input_trade_qty"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Selected currency radio group
                        Text(
                            text = "Standard Settlement Gateway Currency",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("INR", "USD", "EUR", "AUD", "NZD").forEach { cur ->
                                OutlinedButton(
                                    onClick = { selectedPaymentCurrency = cur },
                                    colors = if (selectedPaymentCurrency == cur) {
                                        ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = Color.White
                                        )
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    },
                                    modifier = Modifier.weight(1f).testTag("trade_cur_sel_$cur")
                                ) {
                                    Text(cur, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Dynamic conversion box
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                val symbol = when(selectedPaymentCurrency) {
                                    "INR" -> "₹"
                                    "USD" -> "$"
                                    "EUR" -> "€"
                                    "NZD" -> "NZ$"
                                    "AUD" -> "A$"
                                    else -> "$"
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Gateway Mid-Market rate:")
                                    Text(
                                        "1 USD = $symbol${df.format(currencyMultiplier)}",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Price per credit:")
                                    Text("$symbol${df.format(nativePricePerCredit)}")
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Immediate Total:", fontWeight = FontWeight.Bold)
                                    Text(
                                        "$symbol${df.format(nativeTotal)}",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Wallet funds available:")
                                    Text(
                                        "$symbol${df.format(userBalanceNative)}",
                                        color = if (userBalanceNative >= nativeTotal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (userBalanceNative < nativeTotal) {
                            Text(
                                text = "Insufficient funds. Use sandbox wire button at top right to add virtual funds in $selectedPaymentCurrency.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.executeCarbonTrade(
                                    listingId = listing.id,
                                    creditsToBuy = doubleQty,
                                    currencyCode = selectedPaymentCurrency,
                                    pricePerCreditUSD = costPriceUSD
                                )
                            },
                            enabled = doubleQty > 0 && userBalanceNative >= nativeTotal && doubleQty <= listing.availableCredits,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("execute_trade_btn")
                        ) {
                            Text("Settle & Clear Ecologic Ledger Flow")
                        }
                    }

                    is PurchaseStatus.Processing -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Contacting SWIFT Multi-Currency Interbank gateway...",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Leasing VCS dynamic certificate codes securely...",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    is PurchaseStatus.Success -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Success", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Trade Cleared Successfully!",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Transferred ${purchaseState.credits} credits. Cleared in country ledger of origin.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth().testTag("trade_success_dismiss")
                            ) {
                                Text("Go to Ledger Receipts")
                            }
                        }
                    }

                    is PurchaseStatus.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = purchaseState.message,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.clearPurchaseStatus() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WireDepositDialog(
    viewModel: CarbonViewModel,
    onDismiss: () -> Unit
) {
    var depCurrency by remember { mutableStateOf("INR") }
    var depAmount by remember { mutableStateOf("10000000") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("wire_deposit_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Wire Virtual Sandbox Capital",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("INR", "USD", "EUR", "AUD", "NZD").forEach { cur ->
                        OutlinedButton(
                            onClick = { depCurrency = cur },
                            colors = if (depCurrency == cur) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(cur, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = depAmount,
                    onValueChange = { depAmount = it },
                    label = { Text("Transfer Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("dep_dialog_amount_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val valDouble = depAmount.toDoubleOrNull() ?: 0.0
                        if (valDouble > 0) {
                            viewModel.addFunds(depCurrency, valDouble)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("dep_dialog_submit")
                ) {
                    Text("Settle Sandbox Ledger Deposit")
                }
            }
        }
    }
}
