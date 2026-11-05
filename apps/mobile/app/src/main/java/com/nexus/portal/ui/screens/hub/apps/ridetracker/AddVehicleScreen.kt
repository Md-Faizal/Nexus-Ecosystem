package com.nexus.portal.ui.screens.hub.apps.ridetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.portal.data.local.ridetracker.RideDatabase
import com.nexus.portal.data.local.ridetracker.VehicleEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar
import android.app.DatePickerDialog
import android.media.ExifInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleScreen(onBackClick: () -> Unit, vehicleId: String? = null) {
    val context = LocalContext.current
    val dao = remember { RideDatabase.getDatabase(context).vehicleDao() }
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Bike") }
    var isOccasional by remember { mutableStateOf(false) }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }
    var vin by remember { mutableStateOf("") }
    var engineCC by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("0") }
    var fuelType by remember { mutableStateOf("") }
    var tankCapacity by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var originalImageUrl by remember { mutableStateOf("") }
    val imageModel = remember(imageUrl) { getCoilImageModel(imageUrl) }
    
    var connectedDevices by remember { mutableStateOf(emptyList<com.nexus.portal.data.local.ridetracker.ConnectedDeviceData>()) }
    
    val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
    var pairedBluetoothDevices by remember { mutableStateOf(emptyList<android.bluetooth.BluetoothDevice>()) }
    var showDevicePicker by remember { mutableStateOf(false) }
    var selectedDeviceCategory by remember { mutableStateOf("Helmet Intercom") }
    var manualMacAddress by remember { mutableStateOf("") }
    var manualDeviceName by remember { mutableStateOf("") }

    val btPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            try {
                if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                    pairedBluetoothDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                    showDevicePicker = true
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val suffix = System.currentTimeMillis()
                    val file = java.io.File(context.filesDir, "vehicle_original_$suffix.jpg")
                    val outputStream = java.io.FileOutputStream(file)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    
                    originalImageUrl = file.absolutePath
                    imageUrl = file.absolutePath
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var showCropDialog by remember { mutableStateOf(false) }
    
    // Dates (Stored as strings in UI, parsed to Long on save)
    var insurancePolicyNumber by remember { mutableStateOf("") }
    var insuranceDateStr by remember { mutableStateOf("") }
    var pucDateStr by remember { mutableStateOf("") }
    var nextServiceDateStr by remember { mutableStateOf("") }
    var isDownloadingOriginal by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var showYearPickerDialog by remember { mutableStateOf(false) }
    var showFuelTypeDropdown by remember { mutableStateOf(false) }

    fun parseDate(dateString: String): Long {
        if (dateString.isBlank()) return 0L
        return try {
            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            sdf.parse(dateString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun formatIndianLicensePlate(input: String): String {
        val raw = input.uppercase().replace(Regex("[^A-Z0-9]"), "")
        val sb = StringBuilder()
        
        // 1. First 2 characters (State code) - should be letters
        val stateCode = raw.take(2).filter { it.isLetter() }
        sb.append(stateCode)
        if (raw.length <= 2) return sb.toString()
        if (stateCode.length < 2) return sb.toString()
        
        sb.append("-")
        
        // 2. Next 2 characters (RTO code) - should be digits
        val afterState = raw.drop(2)
        val rtoCode = afterState.take(2).filter { it.isDigit() }
        sb.append(rtoCode)
        if (afterState.length <= 2) return sb.toString()
        if (rtoCode.length < 2) return sb.toString()
        
        sb.append("-")
        
        // 3. Next characters can be letters (series) or digits (number)
        val afterRto = afterState.drop(2)
        val firstDigitIndex = afterRto.indexOfFirst { it.isDigit() }
        
        if (firstDigitIndex == -1) {
            val series = afterRto.take(3).filter { it.isLetter() }
            sb.append(series)
        } else {
            val series = afterRto.substring(0, firstDigitIndex).take(3).filter { it.isLetter() }
            sb.append(series)
            sb.append("-")
            val number = afterRto.substring(firstDigitIndex).filter { it.isDigit() }.take(4)
            sb.append(number)
        }
        
        return sb.toString()
    }

    // Pre-populate fields in Edit Mode
    LaunchedEffect(vehicleId) {
        if (vehicleId != null) {
            val v = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                dao.getVehicleById(vehicleId)
            }
            if (v != null) {
                name = v.name
                description = v.description
                type = v.type
                isOccasional = v.isOccasional
                make = v.make
                model = v.model
                year = v.year
                licensePlate = v.licensePlate
                vin = v.vin
                engineCC = v.engineCC
                color = v.color
                odometer = v.odometer.removeSuffix(" km")
                fuelType = v.fuelType
                tankCapacity = v.tankCapacity
                imageUrl = v.imageUrl
                originalImageUrl = if (v.originalImageUrl.isBlank()) v.imageUrl else v.originalImageUrl
                insurancePolicyNumber = v.insurancePolicyNumber
                insuranceDateStr = if (v.insuranceDate > 0L) {
                    SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(java.util.Date(v.insuranceDate))
                } else ""
                pucDateStr = if (v.pucDate > 0L) {
                    SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(java.util.Date(v.pucDate))
                } else ""
                nextServiceDateStr = if (v.nextServiceDate > 0L) {
                    SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(java.util.Date(v.nextServiceDate))
                } else ""
                
                val converter = com.nexus.portal.data.local.ridetracker.VehicleConverters()
                connectedDevices = converter.toConnectedDevicesList(v.connectedDevicesJson)
            }
        }
    }

    fun getIndianStateName(plate: String): String? {
        val clean = plate.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (clean.length < 2) return null
        val stateCode = clean.take(2)
        val stateMap = mapOf(
            "AN" to "Andaman and Nicobar Islands",
            "AP" to "Andhra Pradesh",
            "AR" to "Arunachal Pradesh",
            "AS" to "Assam",
            "BR" to "Bihar",
            "CH" to "Chandigarh",
            "CG" to "Chhattisgarh",
            "DD" to "Daman and Diu",
            "DN" to "Dadra and Nagar Haveli",
            "DL" to "Delhi",
            "GA" to "Goa",
            "GJ" to "Gujarat",
            "HR" to "Haryana",
            "HP" to "Himachal Pradesh",
            "JK" to "Jammu and Kashmir",
            "JH" to "Jharkhand",
            "KA" to "Karnataka",
            "KL" to "Kerala",
            "LA" to "Ladakh",
            "LD" to "Lakshadweep",
            "MP" to "Madhya Pradesh",
            "MH" to "Maharashtra",
            "MN" to "Manipur",
            "ML" to "Meghalaya",
            "MZ" to "Mizoram",
            "NL" to "Nagaland",
            "OD" to "Odisha",
            "OR" to "Odisha (Old)",
            "PY" to "Puducherry",
            "PB" to "Punjab",
            "RJ" to "Rajasthan",
            "SK" to "Sikkim",
            "TN" to "Tamil Nadu",
            "TG" to "Telangana",
            "TS" to "Telangana",
            "TR" to "Tripura",
            "UP" to "Uttar Pradesh",
            "UK" to "Uttarakhand",
            "UA" to "Uttarakhand (Old)",
            "WB" to "West Bengal"
        )
        return stateMap[stateCode]
    }

    val indianPlateRegex = remember { Regex("^[A-Z]{2}[-\\s]?\\d{2}[-\\s]?[A-Z]{0,3}[-\\s]?\\d{1,4}$", RegexOption.IGNORE_CASE) }

    fun showDatePicker(currentDateStr: String, onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        if (currentDateStr.isNotBlank()) {
            try {
                val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                sdf.parse(currentDateStr)?.let {
                    calendar.time = it
                }
            } catch (e: Exception) {
                // Keep default calendar
            }
        }
        DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = String.format("%02d-%02d-%04d", selectedDay, selectedMonth + 1, selectedYear)
                onDateSelected(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun detectVehicleTypeLocal(nameStr: String, makeStr: String, modelStr: String): String? {
        val query = "$nameStr $makeStr $modelStr".lowercase()
        val scooterKeys = listOf(
            "scooter", "activa", "dio", "grazia", "aviator", "access", "burgman", 
            "avenis", "jupiter", "ntorq", "pep", "zest", "wego", "streak", "scooty", 
            "vespa", "aprilia", "sxr", "sr", "aerox", "fascino", "rayzr", "ray zr", 
            "ola", "s1", "ather", "450x", "iqube", "chetak", "vida", "simple one", "river indie"
        )
        val bikeKeys = listOf(
            "bike", "motorcycle", "splendor", "passion", "hf deluxe", "glamour", 
            "super splendor", "xpulse", "xtreme", "karizma", "pulsar", "platina", 
            "ct100", "avenger", "discover", "dominar", "duke", "rc 390", "rc 200", 
            "adventure", "ktm", "husqvarna", "apache", "raider", "ronin", "star city", 
            "fz", "r15", "mt15", "fz25", "fazer", "yzf", "r1", "r6", "bullet", 
            "classic 350", "meteor", "hunter", "himalayan", "scram", "interceptor", 
            "continental", "super meteor", "royal enfield", "g310r", "cbr", "ninja", 
            "zx10r", "hayabusa", "gsxr", "gixxer", "unicorn", "shine", "sp 125"
        )
        val carKeys = listOf(
            "car", "swift", "baleno", "creta", "thar", "scorpio", "city", "civic", 
            "fortuner", "alto", "i10", "i20", "nexon", "punch", "brezza", "harrier", 
            "safari", "xuv", "verna", "rapid", "octavia", "virtus", "slavia", "compass", 
            "duster", "kwid", "triber", "kiger", "tiago", "tigor", "altroz", "magnite", 
            "celerio", "wagonr", "wagon r", "spresso", "s-presso", "ignis", "dzire", 
            "ertiga", "xl6", "ciaz", "vitara", "jimny", "fronx", "invicto", "glanza", 
            "rumion", "urban cruiser", "camry", "vellfire", "hilux", "innova", "crysta", 
            "hycross", "brio", "amaze", "jazz", "wrv", "wr-v", "accord", "crv", "cr-v", 
            "elevate", "micra", "sunny", "terrano", "kicks", "captur", "pulse", "scala", 
            "tesla", "model s", "model 3", "model x", "model y", "audi", "bmw", "mercedes", 
            "porsche", "ferrari", "lamborghini", "mustang", "camaro", "corvette", "honda", 
            "toyota", "hyundai", "ford", "chevrolet", "nissan", "volkswagen", "skoda", 
            "renault", "mahindra", "tata", "maruti", "suzuki", "kia", "seltos", "sonet", 
            "carens", "carnival"
        )
        for (key in scooterKeys) {
            if (query.contains(key)) return "Scooter"
        }
        for (key in bikeKeys) {
            if (query.contains(key)) return "Bike"
        }
        for (key in carKeys) {
            if (query.contains(key)) return "Car"
        }
        return null
    }

    suspend fun detectVehicleType(nameStr: String, makeStr: String, modelStr: String): String? {
        if (nameStr.isBlank() && makeStr.isBlank() && modelStr.isBlank()) return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val queryParams = String.format(
                    "name=%s&make=%s&model=%s",
                    java.net.URLEncoder.encode(nameStr, "UTF-8"),
                    java.net.URLEncoder.encode(makeStr, "UTF-8"),
                    java.net.URLEncoder.encode(modelStr, "UTF-8")
                )
                val url = java.net.URL("${com.nexus.portal.core.NetworkConfig.backendUrl}/api/vehicles/detect?$queryParams")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    conn.disconnect()
                    val json = org.json.JSONObject(response.toString())
                    if (json.optBoolean("success", false) && json.has("type")) {
                        return@withContext json.getString("type")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext detectVehicleTypeLocal(nameStr, makeStr, modelStr)
        }
    }

    // Debounced dynamic type auto-detection
    LaunchedEffect(name, make, model) {
        if (name.isBlank() && make.isBlank() && model.isBlank()) return@LaunchedEffect
        delay(500)
        detectVehicleType(name, make, model)?.let { detectedType ->
            type = detectedType
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vehicleId == null) "Add Vehicle" else "Edit Vehicle", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF050505))
            )
        },
        floatingActionButton = {
            val isPlateValid = licensePlate.isEmpty() || indianPlateRegex.matches(licensePlate)
            val canSave = name.isNotBlank() && type.isNotBlank() && !isSaving && isPlateValid

            FloatingActionButton(
                onClick = {
                    if (!canSave) return@FloatingActionButton
                    isSaving = true
                    
                    coroutineScope.launch {
                        try {
                            val vId = vehicleId ?: java.util.UUID.randomUUID().toString()
                            
                            // Upload original and cropped images if they are local file paths
                            val finalOriginalUrl = if (originalImageUrl.isNotBlank()) {
                                uploadImageToServer(originalImageUrl, vId)
                            } else ""
                            
                            val finalImageUrl = if (imageUrl.isNotBlank()) {
                                if (imageUrl == originalImageUrl) {
                                    finalOriginalUrl
                                } else {
                                    uploadImageToServer(imageUrl, vId)
                                }
                            } else "https://images.unsplash.com/photo-1558981806-ec527fa84c39?q=80&w=2070&auto=format&fit=crop"
                            
                            val newVehicle = VehicleEntity(
                                id = vId,
                                name = name.trim(),
                                type = type,
                                make = make,
                                model = model,
                                year = year,
                                licensePlate = licensePlate,
                                vin = vin,
                                insurancePolicyNumber = insurancePolicyNumber,
                                insuranceDate = parseDate(insuranceDateStr),
                                pucDate = parseDate(pucDateStr),
                                nextServiceDate = parseDate(nextServiceDateStr),
                                fuelType = fuelType,
                                tankCapacity = tankCapacity,
                                description = description,
                                color = color,
                                engineCC = engineCC,
                                odometer = "$odometer km",
                                topSpeed = "0 km/h", // Default
                                fuelRange = "0 km", // Default
                                maintenanceDue = "Checkup required",
                                isActive = true,
                                imageUrl = finalImageUrl,
                                originalImageUrl = finalOriginalUrl,
                                statsAvgSpeed = "0.0", // Default to show Recent Stats section
                                statsLeanAngle = "0.0",
                                connectedDevicesJson = com.nexus.portal.data.local.ridetracker.VehicleConverters().fromConnectedDevicesList(connectedDevices),
                                tripHistoryPreviewCount = 3, // Default to show Trip History Preview section
                                isOccasional = isOccasional
                            )
                            
                            dao.insertVehicle(newVehicle)
                            com.nexus.portal.util.BluetoothMacUtils.refreshKnownMacsCache(context)
                            com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
                            
                            android.widget.Toast.makeText(context, "Vehicle saved successfully", android.widget.Toast.LENGTH_SHORT).show()
                            onBackClick() // Navigate back after saving
                        } catch (e: Exception) {
                            e.printStackTrace()
                            android.widget.Toast.makeText(context, "Error saving vehicle: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                        } finally {
                            isSaving = false
                        }
                    }
                },
                containerColor = if (canSave) Color(0xFF00E5FF) else Color.DarkGray
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = "Save",
                        color = if (canSave) Color.Black else Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        },
        containerColor = Color(0xFF050505)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Basic Information", color = Color.White, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            
            if (imageUrl.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF151515))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Upload Vehicle Photo",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Upload Vehicle Photo", color = Color.White, fontSize = 14.sp)
                        Text("Tap to select from gallery", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E))
                ) {
                    coil.compose.SubcomposeAsyncImage(
                        model = imageModel,
                        contentDescription = "Vehicle Image",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFF00E5FF))
                            }
                        }
                    )
                    
                    // Gradient overlay at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xAA000000))
                                )
                            )
                    )
                    
                    // Download original full-resolution image button
                    if (originalImageUrl.isNotBlank()) {
                        IconButton(
                            onClick = {
                                downloadOriginalImageToPublicDownloads(
                                    context = context,
                                    originalUrl = originalImageUrl,
                                    vehicleName = if (name.isBlank()) "vehicle" else name,
                                    coroutineScope = coroutineScope,
                                    onStart = { isDownloadingOriginal = true },
                                    onComplete = { isDownloadingOriginal = false }
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(36.dp)
                                .background(Color(0x99000000), shape = RoundedCornerShape(18.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download Original Full-Res Image",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    var isDownloadingForCrop by remember { mutableStateOf(false) }

                    // Action controls Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Change Image",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change", color = Color.White, fontSize = 11.sp, maxLines = 1)
                        }
                        
                        Button(
                            onClick = {
                                val isRemoteUrl = originalImageUrl.startsWith("http://") || originalImageUrl.startsWith("https://") || originalImageUrl.startsWith("/uploads/")
                                if (isRemoteUrl) {
                                    isDownloadingForCrop = true
                                    coroutineScope.launch {
                                        val finalUrl = if (originalImageUrl.startsWith("/uploads/")) "${com.nexus.portal.core.NetworkConfig.backendUrl}$originalImageUrl" else originalImageUrl
                                        val localPath = downloadImageToLocalFile(context, finalUrl)
                                        isDownloadingForCrop = false
                                        if (localPath != null) {
                                            originalImageUrl = localPath
                                            showCropDialog = true
                                        } else {
                                            android.widget.Toast.makeText(context, "Failed to load original image for cropping", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    showCropDialog = true
                                }
                            },
                            enabled = !isDownloadingForCrop,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isDownloadingForCrop) {
                                CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Crop,
                                    contentDescription = "Crop Image",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Crop", color = Color.White, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                        
                        if (imageUrl != originalImageUrl && originalImageUrl.isNotBlank()) {
                            Button(
                                onClick = { imageUrl = originalImageUrl },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Reset Image",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset", color = Color.White, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                        
                        Button(
                            onClick = { 
                                imageUrl = ""
                                originalImageUrl = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x88FF5252)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Image",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", color = Color.White, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Vehicle Name (e.g. My R1) *") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                isError = name.isBlank(),
                colors = defaultTextFieldColors()
            )
            
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description / Notes") },
                modifier = Modifier.fillMaxWidth(),
                colors = defaultTextFieldColors()
            )

            Text("Vehicle Type *", color = Color.Gray, fontSize = 14.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Bike", "Scooter", "Car").forEach { vType ->
                    FilterChip(
                        selected = type == vType,
                        onClick = { type = vType },
                        label = { Text(vType) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00E5FF),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E1E1E),
                            labelColor = Color.White
                        )
                    )
                }
            }

            HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
            Text("Specifications", color = Color.White, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text("Make") }, modifier = Modifier.weight(1f), singleLine = true, colors = defaultTextFieldColors())
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, modifier = Modifier.weight(1f), singleLine = true, colors = defaultTextFieldColors())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Year") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = defaultTextFieldColors()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showYearPickerDialog = true }
                    )
                }
                
                OutlinedTextField(
                    value = engineCC,
                    onValueChange = { input -> if (input.all { it.isDigit() }) engineCC = input },
                    label = { Text("Engine CC") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = defaultTextFieldColors()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text("Color") }, modifier = Modifier.weight(1f), singleLine = true, colors = defaultTextFieldColors())
                
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { input -> if (input.all { it.isDigit() }) odometer = input },
                    label = { Text("Odometer (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = defaultTextFieldColors()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val standardFuelTypes = listOf("Petrol", "Diesel", "Hybrid", "Bio-fuel")
                val isCustomFuel = fuelType.isNotEmpty() && !standardFuelTypes.contains(fuelType)

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = fuelType,
                        onValueChange = { if (isCustomFuel || fuelType == "Custom") fuelType = it },
                        readOnly = !isCustomFuel && fuelType != "Custom",
                        label = { Text("Fuel Type") },
                        trailingIcon = {
                            IconButton(onClick = { showFuelTypeDropdown = true }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Fuel Type",
                                    tint = Color(0xFF00E5FF)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = defaultTextFieldColors()
                    )

                    if (!isCustomFuel && fuelType != "Custom") {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showFuelTypeDropdown = true }
                        )
                    }

                    DropdownMenu(
                        expanded = showFuelTypeDropdown,
                        onDismissRequest = { showFuelTypeDropdown = false },
                        modifier = Modifier.background(Color(0xFF0F0F0F))
                    ) {
                        val fuelOptions = listOf("Petrol", "Diesel", "Hybrid", "Bio-fuel", "Custom")
                        fuelOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, color = Color.White) },
                                onClick = {
                                    fuelType = if (option == "Custom") "Custom" else option
                                    showFuelTypeDropdown = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = tankCapacity,
                    onValueChange = { input ->
                        if (input.isEmpty() || (input.all { it.isDigit() || it == '.' } && input.count { it == '.' } <= 1)) {
                            tankCapacity = input
                        }
                    },
                    label = { Text("Tank Capacity (L)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = defaultTextFieldColors()
                )
            }

            HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
            
            // Paired Devices Section
            Text("Automated Ride Tracking", color = Color.White, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("Select Bluetooth devices (like your helmet, car screen, or dash) to automatically start/stop tracking and map data to this vehicle.", color = Color.Gray, fontSize = 12.sp)

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (connectedDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF151515))
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No devices paired.", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    connectedDevices.forEach { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF151515))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (device.iconType) {
                                    "headset" -> Icons.Default.Headset
                                    "car" -> Icons.Default.DirectionsCar
                                    "smartphone" -> Icons.Default.Smartphone
                                    else -> Icons.Default.Bluetooth
                                },
                                contentDescription = "Device Icon",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name, color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                Text("${device.deviceCategory} • ${device.macAddress}", color = Color.Gray, fontSize = 12.sp)
                            }
                            IconButton(onClick = {
                                connectedDevices = connectedDevices.filter { it.macAddress != device.macAddress }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Device", tint = Color(0xFFFF5252))
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            android.Manifest.permission.BLUETOOTH_CONNECT
                        } else {
                            android.Manifest.permission.BLUETOOTH
                        }
                        if (androidx.core.app.ActivityCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            try {
                                pairedBluetoothDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                                showDevicePicker = true
                            } catch (e: SecurityException) {
                                e.printStackTrace()
                            }
                        } else {
                            btPermissionLauncher.launch(permission)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = "Add Device", tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Bluetooth Device", color = Color.White)
                }
            }

            HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
            Text("Legal & Insurance", color = Color.White, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)

            val isPlateValid = licensePlate.isEmpty() || indianPlateRegex.matches(licensePlate)
            val detectedState = remember(licensePlate) { getIndianStateName(licensePlate) }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = licensePlate,
                    onValueChange = { input -> licensePlate = formatIndianLicensePlate(input) },
                    label = { Text("License Plate (e.g. KL-17-AB-1234)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !isPlateValid,
                    colors = defaultTextFieldColors()
                )
                if (detectedState != null) {
                    Text(
                        text = "📍 Detected State: $detectedState",
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                if (!isPlateValid) {
                    Text(
                        text = "⚠ Invalid Indian plate format (e.g., KL-17-AB-1234)",
                        color = Color(0xFFFF8A65),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            
            OutlinedTextField(value = vin, onValueChange = { vin = it }, label = { Text("VIN / Chassis Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = defaultTextFieldColors())

            OutlinedTextField(value = insurancePolicyNumber, onValueChange = { insurancePolicyNumber = it }, label = { Text("Insurance Policy Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = defaultTextFieldColors())
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = insuranceDateStr,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Insurance Issue Date (dd-mm-yyyy)") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Date",
                            tint = Color(0xFF00E5FF)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = defaultTextFieldColors()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker(insuranceDateStr) { insuranceDateStr = it } }
                )
            }
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = pucDateStr,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("PUC Issue Date (dd-mm-yyyy)") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Date",
                            tint = Color(0xFF00E5FF)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = defaultTextFieldColors()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker(pucDateStr) { pucDateStr = it } }
                )
            }
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = nextServiceDateStr,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Next Service Date (dd-mm-yyyy)") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Date",
                            tint = Color(0xFF00E5FF)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = defaultTextFieldColors()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker(nextServiceDateStr) { nextServiceDateStr = it } }
                )
            }

            Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
        }

        if (showYearPickerDialog) {
            AlertDialog(
                onDismissRequest = { showYearPickerDialog = false },
                title = { Text("Select Year", color = Color.White, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                text = {
                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                    val years = (currentYear downTo 1950).toList()
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(years.size) { index ->
                                val y = years[index]
                                Button(
                                    onClick = {
                                        year = y.toString()
                                        showYearPickerDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (year == y.toString()) Color(0xFF00E5FF) else Color(0xFF1E1E1E),
                                        contentColor = if (year == y.toString()) Color.Black else Color.White
                                    ),
                                    contentPadding = PaddingValues(8.dp)
                                ) {
                                    Text(y.toString(), fontSize = 14.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showYearPickerDialog = false }) {
                        Text("Cancel", color = Color(0xFF00E5FF))
                    }
                },
                containerColor = Color(0xFF0F0F0F)
            )
        }

        if (showCropDialog && originalImageUrl.isNotBlank()) {
            CropRepositionDialog(
                imagePath = originalImageUrl,
                onDismiss = { showCropDialog = false },
                onCropApplied = { croppedPath ->
                    imageUrl = croppedPath
                }
            )
        }

        if (showDevicePicker) {
            // Helper to add device to this vehicle's connectedDevices list
            fun addDevice(mac: String, name: String) {
                if (connectedDevices.any { it.macAddress == mac }) return
                val icon = when (selectedDeviceCategory) {
                    "Helmet Intercom" -> "headset"
                    "Vehicle Dash"   -> "car"
                    "Car Screen"     -> "car"
                    "Scooter Dash"   -> "smartphone"
                    else             -> "bluetooth"
                }
                connectedDevices = connectedDevices + com.nexus.portal.data.local.ridetracker.ConnectedDeviceData(
                    name           = name.ifBlank { "Unknown Device" },
                    status         = "Disconnected",
                    iconType       = icon,
                    macAddress     = mac,
                    deviceCategory = selectedDeviceCategory
                )
                showDevicePicker = false
                manualMacAddress = ""
                manualDeviceName = ""
            }

            AlertDialog(
                onDismissRequest = { showDevicePicker = false },
                title = { Text("Add Bluetooth Device", color = Color.White, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        // ── Category chips ──
                        Text("Device Type", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Helmet Intercom", "Vehicle Dash", "Car Screen", "Scooter Dash").forEach { cat ->
                                FilterChip(
                                    selected = selectedDeviceCategory == cat,
                                    onClick = { selectedDeviceCategory = cat },
                                    label = { Text(cat, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF00E5FF),
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF1E1E1E),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                        // ── Manual MAC entry ──
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFF2A2A2A))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Enter MAC Manually", color = Color.Gray, fontSize = 12.sp)
                        Text(
                            "Use this for devices not visible in the paired list (e.g. Suzuki Ride Connect dash).",
                            color = Color(0xFF555555), fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualDeviceName,
                            onValueChange = { manualDeviceName = it },
                            label = { Text("Device Name", fontSize = 12.sp) },
                            placeholder = { Text("e.g. Suzuki Dash", color = Color(0xFF555555), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = defaultTextFieldColors()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualMacAddress,
                            onValueChange = { manualMacAddress = it.uppercase().take(20) },
                            label = { Text("MAC Address", fontSize = 12.sp) },
                            placeholder = { Text("AA:BB:CC:DD:EE:FF", color = Color(0xFF555555), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = defaultTextFieldColors()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val cleanMac = remember(manualMacAddress) { manualMacAddress.replace('O', '0').replace(Regex("[^0-9A-F]"), "") }
                        val isMacValid = cleanMac.length == 12
                        Button(
                            onClick = {
                                if (isMacValid) {
                                    val formattedMac = cleanMac.chunked(2).joinToString(":")
                                    addDevice(formattedMac, manualDeviceName.ifBlank { "Manual Device" })
                                }
                            },
                            enabled = isMacValid,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF), contentColor = Color.Black,
                                disabledContainerColor = Color(0xFF1E1E1E), disabledContentColor = Color(0xFF444444)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("Add Manual Device", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                        // ── Paired devices ──
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFF2A2A2A))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Paired Devices", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (pairedBluetoothDevices.isEmpty()) {
                            Text("No paired devices found.", color = Color(0xFF555555), fontSize = 14.sp)
                        } else {
                            pairedBluetoothDevices.forEach { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clickable {
                                            try {
                                                addDevice(
                                                    mac  = device.address,
                                                    name = try { device.name ?: "Unknown" } catch (e: SecurityException) { "Unknown" }
                                                )
                                            } catch (e: SecurityException) { e.printStackTrace() }
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(try { device.name ?: "Unknown" } catch (e: SecurityException) { "Unknown" }, color = Color.White, fontSize = 14.sp)
                                        Text(device.address, color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDevicePicker = false; manualMacAddress = ""; manualDeviceName = "" }) {
                        Text("Close", color = Color(0xFF00E5FF))
                    }
                },
                containerColor = Color(0xFF0F0F0F)
            )
        }
    }
}

@Composable
fun defaultTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.LightGray,
    focusedBorderColor = Color(0xFF00E5FF),
    unfocusedBorderColor = Color.DarkGray,
    focusedLabelColor = Color(0xFF00E5FF),
    unfocusedLabelColor = Color.Gray,
    cursorColor = Color(0xFF00E5FF)
)

@Composable
fun CropRepositionDialog(
    imagePath: String,
    onDismiss: () -> Unit,
    onCropApplied: (String) -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var rotation by remember { mutableStateOf(0f) }
    
    val density = androidx.compose.ui.platform.LocalDensity.current
    val viewportWidthDp = 300.dp
    val viewportHeightDp = 200.dp
    val viewportWidthPx = with(density) { viewportWidthDp.roundToPx() }
    val viewportHeightPx = with(density) { viewportHeightDp.roundToPx() }
    val imageModel = remember(imagePath) { getCoilImageModel(imagePath) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Crop & Reposition",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Pinch to zoom or use the slider. Drag the image to reposition it within the frame.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                
                Box(
                    modifier = Modifier
                        .size(viewportWidthDp, viewportHeightDp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    coil.compose.SubcomposeAsyncImage(
                        model = imageModel,
                        contentDescription = "Crop Preview",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFF00E5FF))
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY,
                                rotationZ = rotation
                            )
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.Gray)
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 1f..5f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFF00E5FF),
                            thumbColor = Color(0xFF00E5FF)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.Gray)
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { rotation = (rotation + 90f) % 360f },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF2A2A2A)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = "Rotate 90 degrees",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val croppedPath = cropBitmapToFile(
                        context = context,
                        originalPath = imagePath,
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        rotation = rotation,
                        viewportWidth = viewportWidthPx,
                        viewportHeight = viewportHeightPx,
                        targetWidth = 1200,
                        targetHeight = 800
                    )
                    if (croppedPath != null) {
                        onCropApplied(croppedPath)
                    }
                    onDismiss()
                }
            ) {
                Text("Apply Crop", color = Color(0xFF00E5FF), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF151515)
    )
}

fun decodeAndRotateBitmap(path: String): android.graphics.Bitmap? {
    try {
        val original = android.graphics.BitmapFactory.decodeFile(path) ?: return null
        val exif = android.media.ExifInterface(path)
        val orientation = exif.getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = android.graphics.Matrix()
        var rotated = false
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.postRotate(90f)
                rotated = true
            }
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.postRotate(180f)
                rotated = true
            }
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.postRotate(270f)
                rotated = true
            }
        }
        return if (rotated) {
            val bmp = android.graphics.Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            original.recycle()
            bmp
        } else {
            original
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

fun cropBitmapToFile(
    context: android.content.Context,
    originalPath: String,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    rotation: Float,
    viewportWidth: Int,
    viewportHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): String? {
    try {
        val original = decodeAndRotateBitmap(originalPath) ?: return null
        
        val cropped = android.graphics.Bitmap.createBitmap(targetWidth, targetHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(cropped)
        
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        val matrix = android.graphics.Matrix()
        
        val scaleX = viewportWidth.toFloat() / original.width
        val scaleY = viewportHeight.toFloat() / original.height
        val baseScale = maxOf(scaleX, scaleY)
        
        val initialWidth = original.width * baseScale
        val initialHeight = original.height * baseScale
        val initialDx = (viewportWidth - initialWidth) / 2f
        val initialDy = (viewportHeight - initialHeight) / 2f
        
        matrix.postScale(baseScale, baseScale)
        matrix.postTranslate(initialDx, initialDy)
        
        val centerX = viewportWidth / 2f
        val centerY = viewportHeight / 2f
        
        matrix.postScale(scale, scale, centerX, centerY)
        matrix.postRotate(rotation, centerX, centerY)
        matrix.postTranslate(offsetX, offsetY)
        
        val densityScale = targetWidth.toFloat() / viewportWidth.toFloat()
        matrix.postScale(densityScale, densityScale)
        
        canvas.drawBitmap(original, matrix, paint)
        original.recycle()
        
        val file = java.io.File(context.filesDir, "vehicle_cropped_${System.currentTimeMillis()}.jpg")
        val outputStream = java.io.FileOutputStream(file)
        cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        cropped.recycle()
        
        return file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

suspend fun downloadImageToLocalFile(context: android.content.Context, urlString: String): String? {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val url = java.net.URL(urlString)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val file = java.io.File(context.filesDir, "vehicle_downloaded_${System.currentTimeMillis()}.jpg")
                val inputStream = conn.inputStream
                val outputStream = java.io.FileOutputStream(file)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
                return@withContext file.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}

suspend fun uploadImageToServer(localPath: String, vehicleId: String): String {
    if (localPath.isBlank() || localPath.startsWith("http://") || localPath.startsWith("https://") || localPath.startsWith("/uploads/")) {
        return localPath
    }
    val file = java.io.File(localPath)
    if (!file.exists()) return localPath
    
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val url = java.net.URL("${com.nexus.portal.core.NetworkConfig.backendUrl}/api/vehicles/upload-image")
            val conn = url.openConnection() as java.net.HttpURLConnection
            val boundary = "Boundary-" + System.currentTimeMillis()
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            
            val os = java.io.DataOutputStream(conn.outputStream)
            
            os.writeBytes("--$boundary\r\n")
            os.writeBytes("Content-Disposition: form-data; name=\"vehicleId\"\r\n\r\n")
            os.writeBytes(vehicleId + "\r\n")
            
            os.writeBytes("--$boundary\r\n")
            os.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"${file.name}\"\r\n")
            os.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            
            val fis = java.io.FileInputStream(file)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                os.write(buffer, 0, bytesRead)
            }
            fis.close()
            os.writeBytes("\r\n")
            os.writeBytes("--$boundary--\r\n")
            os.flush()
            os.close()
            
            if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                conn.disconnect()
                val json = org.json.JSONObject(response.toString())
                if (json.optBoolean("success", false) && json.has("imageUrl")) {
                    return@withContext json.getString("imageUrl")
                } else {
                    throw Exception(json.optString("message", "Server returned failure"))
                }
            } else {
                throw Exception("HTTP ${conn.responseCode}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Upload failed: ${e.message}")
        }
    }
}

fun downloadOriginalImageToPublicDownloads(
    context: android.content.Context,
    originalUrl: String,
    vehicleName: String,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onStart: () -> Unit,
    onComplete: () -> Unit
) {
    if (originalUrl.isBlank()) {
        android.widget.Toast.makeText(context, "No original image available", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    
    onStart()
    
    coroutineScope.launch {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val finalUrl = if (originalUrl.startsWith("/uploads/")) "${com.nexus.portal.core.NetworkConfig.backendUrl}$originalUrl" else originalUrl
                val bytes = if (finalUrl.startsWith("http://") || finalUrl.startsWith("https://")) {
                    val url = java.net.URL(finalUrl)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                        conn.inputStream.readBytes()
                    } else throw Exception("HTTP Error: ${conn.responseCode}")
                } else {
                    val file = java.io.File(originalUrl)
                    if (file.exists()) file.readBytes() else throw Exception("File not found")
                }
                
                if (bytes == null || bytes.isEmpty()) {
                    throw Exception("Failed to retrieve original image data")
                }
                
                val filename = "vehicle_${vehicleName.trim().replace(Regex("[^a-zA-Z0-9]"), "_")}_original_${System.currentTimeMillis()}.jpg"
                
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                }
                
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream != null) {
                            outputStream.write(bytes)
                            outputStream.flush()
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Saved to Downloads: $filename", android.widget.Toast.LENGTH_LONG).show()
                            }
                            return@withContext
                        }
                    }
                }
                
                // Fallback for older versions
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val destFile = java.io.File(downloadDir, filename)
                java.io.FileOutputStream(destFile).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Saved to Downloads: $filename", android.widget.Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error saving image: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }
}

fun getCoilImageModel(imageUrl: String): Any? {
    if (imageUrl.isBlank()) return null
    if (imageUrl.startsWith("/uploads/")) {
        return "${com.nexus.portal.core.NetworkConfig.backendUrl}$imageUrl"
    }
    if (imageUrl.startsWith("/")) {
        val file = java.io.File(imageUrl)
        if (file.exists()) return file
        return "${com.nexus.portal.core.NetworkConfig.backendUrl}$imageUrl"
    }
    if (imageUrl.startsWith("data:image") || !imageUrl.contains("/")) {
        return try {
            val base64Str = if (imageUrl.contains(",")) imageUrl.substring(imageUrl.indexOf(",") + 1) else imageUrl
            val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            imageUrl
        }
    }
    return imageUrl
}
