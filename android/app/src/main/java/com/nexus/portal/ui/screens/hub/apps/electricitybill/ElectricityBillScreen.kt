package com.nexus.portal.ui.screens.hub.apps.electricitybill

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.portal.ui.components.TechGridBackground
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectricityBillScreen(onBackClick: () -> Unit) {
    var isHistoryView by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF16161A)) // Premium dark base color
    ) {
        TechGridBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHistoryView) "Billing History" else "Electricity Bill Calc",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isSyncing = true
                                val result = ElectricityBillRepository.syncWithCloud(context)
                                isSyncing = false
                                if (result.first) {
                                    android.widget.Toast.makeText(context, "Calculations Synchronized!", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "Sync Failed: ${result.second ?: "Network Error"}", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }, 
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFFFFB300), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = "Sync Cloud",
                                tint = Color.LightGray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { isHistoryView = !isHistoryView }) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "History",
                            tint = if (isHistoryView) Color(0xFFFFB300) else Color.White
                        )
                    }
                }
            }

            if (isHistoryView) {
                ElectricityBillHistoryView()
            } else {
                ElectricityBillCalculatorView()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectricityBillCalculatorView() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var unitsStr by remember { mutableStateOf("250") }
    var loadStr by remember { mutableStateOf("2.0") }
    var hasSmartMeter by remember { mutableStateOf(true) }
    var digitalPayment by remember { mutableStateOf(true) }

    val units = unitsStr.toDoubleOrNull() ?: 0.0
    val loadKw = loadStr.toDoubleOrNull() ?: 1.0

    // Core Calculation Logic
    val billResult = remember(units, loadKw, hasSmartMeter, digitalPayment) {
        calculateBill(units, loadKw, hasSmartMeter, digitalPayment)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // --- INPUT CONTROLS CARD ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF22222E).copy(alpha = 0.85f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "CONSUMPTION SETTINGS",
                    color = Color(0xFFFFB300),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Monthly Units
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Units Consumed", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        OutlinedTextField(
                            value = unitsStr,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.toDoubleOrNull() != null) {
                                    unitsStr = input
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFFB300),
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = units.coerceIn(0.0, 1000.0).toFloat(),
                    onValueChange = { valRounded ->
                        unitsStr = valRounded.toInt().toString()
                    },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFB300),
                        activeTrackColor = Color(0xFFFFB300),
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Connected Load (kW)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Connected Load (kW)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        OutlinedTextField(
                            value = loadStr,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.toDoubleOrNull() != null) {
                                    loadStr = input
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFFB300),
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = loadKw.coerceIn(1.0, 15.0).toFloat(),
                    onValueChange = { valRounded ->
                        // Round load to 0.5 steps
                        val rounded = (Math.round(valRounded * 2) / 2.0)
                        loadStr = rounded.toString()
                    },
                    valueRange = 1f..15f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFB300),
                        activeTrackColor = Color(0xFFFFB300),
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(16.dp))

                // Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Smart Meter Exemption", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Waives ₹60 meter rent if load ≤ 2 kW", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = hasSmartMeter,
                        onCheckedChange = { hasSmartMeter = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFFB300),
                            checkedTrackColor = Color(0xFFFFB300).copy(alpha = 0.3f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Digital Payment Rebate", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Get 4% discount on total bills", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = digitalPayment,
                        onCheckedChange = { digitalPayment = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFFB300),
                            checkedTrackColor = Color(0xFFFFB300).copy(alpha = 0.3f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- CUMULATIVE TARIFF SLABS CARD ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF22222E).copy(alpha = 0.85f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "TARIFF SLABS BREAKDOWN",
                    color = Color(0xFFFFB300),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                billResult.slabBreakdown.forEachIndexed { index, slab ->
                    val color = when (index) {
                        0 -> Color(0xFF00E5FF) // Cyan
                        1 -> Color(0xFF76FF03) // Lime
                        2 -> Color(0xFFFFC400) // Amber
                        else -> Color(0xFFFF3D00) // Orange/Red
                    }

                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(slab.slabName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Rate: ₹${"%.2f".format(slab.rate)} / unit", color = Color.LightGray, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${"%.1f".format(slab.unitsConsumed)} units",
                                    color = if (slab.unitsConsumed > 0) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "₹${"%.2f".format(slab.charge)}",
                                    color = if (slab.charge > 0) color else Color.Gray,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        SlabProgressBar(progress = slab.progress, color = color)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- ITEMIZED ESTIMATED RECEIPT ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24).copy(alpha = 0.95f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ITEMIZED RECEIPT",
                        color = Color(0xFFFFB300),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "TPCODL DOMESTIC",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                ReceiptItemRow(label = "Energy Charge", value = billResult.energyCharge)
                ReceiptItemRow(label = "Fixed Charges (Load: ${"%.1f".format(loadKw)} kW)", value = billResult.fixedCharge)
                ReceiptItemRow(
                    label = "Meter Rent",
                    value = billResult.meterRent,
                    customValueStr = if (billResult.meterRent == 0.0) "Waived (Smart Meter)" else null,
                    customValueColor = if (billResult.meterRent == 0.0) Color(0xFF76FF03) else Color.White
                )
                ReceiptItemRow(label = "Electricity Duty (4% of EC)", value = billResult.electricityDuty)

                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

                ReceiptItemRow(label = "Gross Total Bill", value = billResult.grossTotal, isSemiHighlight = true)

                if (digitalPayment) {
                    ReceiptItemRow(
                        label = "Digital Payment Rebate (4%)",
                        value = -billResult.rebate,
                        customValueColor = Color(0xFF76FF03)
                    )
                }

                HorizontalDivider(color = Color(0xFFFFB300).copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Net Payable Amount",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "₹${"%.2f".format(billResult.netPayable)}",
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- BUTTONS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val record = ElectricityBillRecord(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        units = units,
                        loadKw = loadKw,
                        hasSmartMeter = hasSmartMeter,
                        digitalPayment = digitalPayment,
                        energyCharge = billResult.energyCharge,
                        fixedCharge = billResult.fixedCharge,
                        meterRent = billResult.meterRent,
                        electricityDuty = billResult.electricityDuty,
                        grossTotal = billResult.grossTotal,
                        rebate = billResult.rebate,
                        netPayable = billResult.netPayable,
                        lastUpdated = System.currentTimeMillis(),
                        isDeleted = false
                    )
                    ElectricityBillRepository.addRecord(context, record)
                    android.widget.Toast.makeText(context, "Bill Calculation Saved & Syncing", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Bill", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            IconButton(
                onClick = {
                    val fakeRecord = ElectricityBillRecord(
                        id = "",
                        timestamp = System.currentTimeMillis(),
                        units = units,
                        loadKw = loadKw,
                        hasSmartMeter = hasSmartMeter,
                        digitalPayment = digitalPayment,
                        energyCharge = billResult.energyCharge,
                        fixedCharge = billResult.fixedCharge,
                        meterRent = billResult.meterRent,
                        electricityDuty = billResult.electricityDuty,
                        grossTotal = billResult.grossTotal,
                        rebate = billResult.rebate,
                        netPayable = billResult.netPayable,
                        lastUpdated = System.currentTimeMillis(),
                        isDeleted = false
                    )
                    copyBillToClipboard(context, fakeRecord)
                    android.widget.Toast.makeText(context, "Calculations copied to clipboard!", android.widget.Toast.LENGTH_LONG).show()
                },
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF22222E))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
            }
        }
    }
}

@Composable
fun SlabProgressBar(progress: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.06f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

@Composable
fun ReceiptItemRow(
    label: String,
    value: Double,
    customValueStr: String? = null,
    customValueColor: Color = Color.White,
    isSemiHighlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isSemiHighlight) Color.White else Color.Gray,
            fontSize = if (isSemiHighlight) 14.sp else 13.sp,
            fontWeight = if (isSemiHighlight) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = customValueStr ?: (if (value >= 0) "₹${"%.2f".format(value)}" else "-₹${"%.2f".format(-value)}"),
            color = if (isSemiHighlight) Color.White else customValueColor,
            fontSize = if (isSemiHighlight) 15.sp else 13.sp,
            fontWeight = if (isSemiHighlight) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
fun ElectricityBillHistoryView() {
    val history by ElectricityBillRepository.history.collectAsState()
    val context = LocalContext.current
    
    val activeHistory = remember(history) { history.filter { !it.isDeleted } }

    if (activeHistory.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No saved calculations found.", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Your billing estimates will appear here.", color = Color.DarkGray, fontSize = 12.sp)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(activeHistory) { record ->
            HistoryBillCard(
                record = record,
                onDelete = {
                    ElectricityBillRepository.deleteRecord(context, record.id)
                },
                onShare = {
                    copyBillToClipboard(context, record)
                    android.widget.Toast.makeText(context, "Calculations copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun HistoryBillCard(
    record: ElectricityBillRecord,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
    val dateStr = sdf.format(Date(record.timestamp))

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF22222E)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateStr, color = Color.Gray, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onShare, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Params Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Consumption: ${record.units.toInt()} Units", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Connected Load: ${"%.1f".format(record.loadKw)} kW", color = Color.LightGray, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (record.hasSmartMeter) "Smart Meter" else "Standard Meter",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Text(
                    text = if (record.digitalPayment) "Paid Digitally" else "Standard Cash Payment",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 10.dp))

            // Total Payable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Net Bill Amount:", color = Color.LightGray, fontSize = 14.sp)
                Text(
                    text = "₹${"%.2f".format(record.netPayable)}",
                    color = Color(0xFFFFB300),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// --- CALCULATION UTILITIES ---

data class SlabCalculation(
    val slabName: String,
    val range: String,
    val rate: Double,
    val unitsConsumed: Double,
    val charge: Double,
    val progress: Float
)

data class BillCalculationResult(
    val energyCharge: Double,
    val fixedCharge: Double,
    val meterRent: Double,
    val electricityDuty: Double,
    val grossTotal: Double,
    val rebate: Double,
    val netPayable: Double,
    val slabBreakdown: List<SlabCalculation>
)

fun calculateSlabs(units: Double): List<SlabCalculation> {
    val slabs = mutableListOf<SlabCalculation>()

    // Slab 1: First 50 Units
    val u1 = units.coerceIn(0.0, 50.0)
    slabs.add(
        SlabCalculation(
            slabName = "First 50 Units",
            range = "0 - 50",
            rate = 2.90,
            unitsConsumed = u1,
            charge = u1 * 2.90,
            progress = (u1 / 50.0).toFloat()
        )
    )

    // Slab 2: 51 to 200 Units
    val u2 = if (units > 50) (units - 50).coerceIn(0.0, 150.0) else 0.0
    slabs.add(
        SlabCalculation(
            slabName = "51 to 200 Units",
            range = "51 - 200",
            rate = 4.70,
            unitsConsumed = u2,
            charge = u2 * 4.70,
            progress = (u2 / 150.0).toFloat()
        )
    )

    // Slab 3: 201 to 400 Units
    val u3 = if (units > 200) (units - 200).coerceIn(0.0, 200.0) else 0.0
    slabs.add(
        SlabCalculation(
            slabName = "201 to 400 Units",
            range = "201 - 400",
            rate = 5.70,
            unitsConsumed = u3,
            charge = u3 * 5.70,
            progress = (u3 / 200.0).toFloat()
        )
    )

    // Slab 4: Above 400 Units
    val u4 = if (units > 400) units - 400 else 0.0
    slabs.add(
        SlabCalculation(
            slabName = "Above 400 Units",
            range = "401+",
            rate = 6.10,
            unitsConsumed = u4,
            charge = u4 * 6.10,
            progress = if (u4 > 0) (u4 / 200.0).coerceIn(0.0, 1.0).toFloat() else 0f
        )
    )

    return slabs
}

fun calculateBill(
    units: Double,
    loadKw: Double,
    hasSmartMeter: Boolean,
    digitalPayment: Boolean
): BillCalculationResult {
    val slabBreakdown = calculateSlabs(units)
    val energyCharge = slabBreakdown.sumOf { it.charge }

    // Fixed charges: ₹20 per kW per month
    val fixedCharge = loadKw * 20.0

    // Meter rent: ₹60 flat rate, waived if smart meter and load <= 2 kW
    val meterRent = if (hasSmartMeter && loadKw <= 2.0) 0.0 else 60.0

    // Electricity Duty: 4% of Energy Charge
    val electricityDuty = energyCharge * 0.04

    val grossTotal = energyCharge + fixedCharge + meterRent + electricityDuty

    // Digital Payment rebate: 4% on the total bill
    val rebate = if (digitalPayment) grossTotal * 0.04 else 0.0

    val netPayable = grossTotal - rebate

    return BillCalculationResult(
        energyCharge = energyCharge,
        fixedCharge = fixedCharge,
        meterRent = meterRent,
        electricityDuty = electricityDuty,
        grossTotal = grossTotal,
        rebate = rebate,
        netPayable = netPayable,
        slabBreakdown = slabBreakdown
    )
}

fun copyBillToClipboard(context: Context, record: ElectricityBillRecord) {
    val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
    val dateStr = sdf.format(Date(record.timestamp))
    val summary = """
        ⚡ Nexus Portal: TPCODL Electricity Bill Estimate ⚡
        Date: $dateStr
        ------------------------------------------
        Consumption: ${"%.1f".format(record.units)} Units
        Connected Load: ${"%.1f".format(record.loadKw)} kW
        Smart Meter: ${if (record.hasSmartMeter) "Yes" else "No"}
        Digital Payment: ${if (record.digitalPayment) "Yes" else "No"}
        
        Itemized Breakdown:
        - Energy Charge: ₹${"%.2f".format(record.energyCharge)}
        - Fixed Charge: ₹${"%.2f".format(record.fixedCharge)}
        - Meter Rent: ₹${"%.2f".format(record.meterRent)}
        - Electricity Duty (4%): ₹${"%.2f".format(record.electricityDuty)}
        ------------------------------------------
        Gross Total: ₹${"%.2f".format(record.grossTotal)}
        Digital Rebate (4%): -₹${"%.2f".format(record.rebate)}
        ------------------------------------------
        Net Payable: ₹${"%.2f".format(record.netPayable)}
        
        *Calculated using TPCODL Domestic Consumer Tariff Slabs (FY 2026-27).
    """.trimIndent()

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("Electricity Bill Estimate", summary)
    clipboard.setPrimaryClip(clip)
}
