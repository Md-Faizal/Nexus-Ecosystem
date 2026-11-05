package com.nexus.portal.ui.screens.hub.apps.feecalculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Delete
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
import com.nexus.portal.ui.screens.hub.apps.feecalculator.FeeCalculationRecord
import com.nexus.portal.ui.screens.hub.apps.feecalculator.FeeHistoryRepository
import com.nexus.portal.ui.components.TechGridBackground
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeeCalculatorScreen(onBackClick: () -> Unit) {
    var isHistoryView by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E24))
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
                        text = if (isHistoryView) "Calculation History" else "Fee Calculator",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                IconButton(onClick = { isHistoryView = !isHistoryView }) {
                    Icon(Icons.Default.History, contentDescription = "History", tint = if (isHistoryView) Color(0xFF1E88E5) else Color.White)
                }
            }
            
            if (isHistoryView) {
                FeeHistoryView()
            } else {
                FeeCalculatorView()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeeCalculatorView() {
    val context = LocalContext.current
    
    var totalBalanceStr by remember { mutableStateOf("") }
    var amountToLeaveStr by remember { mutableStateOf("") }
    var feePercentStr by remember { mutableStateOf("3.31") }
    
    var amountToEnter by remember { mutableStateOf(0.0) }
    var feeAmount by remember { mutableStateOf(0.0) }
    var totalDeducted by remember { mutableStateOf(0.0) }
    
    // Auto-calculate on change
    LaunchedEffect(totalBalanceStr, amountToLeaveStr, feePercentStr) {
        try {
            val totalBalance = totalBalanceStr.toDoubleOrNull() ?: 0.0
            val amountToLeave = amountToLeaveStr.toDoubleOrNull() ?: 0.0
            val feePercent = feePercentStr.toDoubleOrNull() ?: 0.0
            
            if (totalBalance > 0 && feePercent >= 0) {
                val feeRate = feePercent / 100.0
                amountToEnter = (totalBalance - amountToLeave) / (1 + feeRate)
                totalDeducted = amountToEnter * (1 + feeRate)
                feeAmount = totalDeducted - amountToEnter
            } else {
                amountToEnter = 0.0
                feeAmount = 0.0
                totalDeducted = 0.0
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = totalBalanceStr,
            onValueChange = { totalBalanceStr = it },
            label = { Text("Total Balance (₹)", color = Color.LightGray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF1E88E5),
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = amountToLeaveStr,
            onValueChange = { amountToLeaveStr = it },
            label = { Text("Amount to Leave (₹)", color = Color.LightGray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF1E88E5),
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = feePercentStr,
            onValueChange = { feePercentStr = it },
            label = { Text("Convenience Fee (%)", color = Color.LightGray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF1E88E5),
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Results Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2C2C34))
                .border(1.dp, Color(0xFF1E88E5).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(24.dp)
        ) {
            Text("RESULTS", color = Color(0xFF1E88E5), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            ResultRow("Amount to Enter", amountToEnter, isHighlight = true)
            HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))
            ResultRow("Convenience Fee", feeAmount)
            HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))
            ResultRow("Total Deducted", totalDeducted)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                val totalBalance = totalBalanceStr.toDoubleOrNull() ?: 0.0
                if (totalBalance > 0 && amountToEnter > 0) {
                    val record = FeeCalculationRecord(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        totalBalance = totalBalance,
                        amountToLeave = amountToLeaveStr.toDoubleOrNull() ?: 0.0,
                        feePercent = feePercentStr.toDoubleOrNull() ?: 0.0,
                        amountToEnter = amountToEnter,
                        feeAmount = feeAmount,
                        totalDeducted = totalDeducted
                    )
                    FeeHistoryRepository.addRecord(context, record)
                    android.widget.Toast.makeText(context, "Saved to History", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save to History", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ResultRow(label: String, value: Double, isHighlight: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.LightGray, fontSize = 16.sp)
        Text(
            text = "₹${"%.2f".format(value)}",
            color = if (isHighlight) Color(0xFF00FF00) else Color.White,
            fontSize = if (isHighlight) 24.sp else 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun FeeHistoryView() {
    val history by FeeHistoryRepository.history.collectAsState()
    val context = LocalContext.current
    
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No history records found.", color = Color.Gray)
        }
        return
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(history) { record ->
            HistoryRecordCard(
                record = record,
                onDelete = {
                    FeeHistoryRepository.deleteRecord(context, record.id)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun HistoryRecordCard(record: FeeCalculationRecord, onDelete: () -> Unit) {
    val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
    val dateStr = sdf.format(Date(record.timestamp))
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2C2C34))
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(dateStr, color = Color.Gray, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fee: ${record.feePercent}%", color = Color(0xFF1E88E5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Balance: ₹${"%.2f".format(record.totalBalance)}", color = Color.LightGray, fontSize = 14.sp)
            Text("Leave: ₹${"%.2f".format(record.amountToLeave)}", color = Color.LightGray, fontSize = 14.sp)
        }
        HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Enter Amount:", color = Color.White, fontSize = 16.sp)
            Text("₹${"%.2f".format(record.amountToEnter)}", color = Color(0xFF00FF00), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
