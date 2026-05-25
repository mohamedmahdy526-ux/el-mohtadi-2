package com.example.ui

import android.widget.Toast
import android.provider.ContactsContract
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.AppSetting
import com.example.database.Attendance
import com.example.database.PayrollSnapshot
import com.example.database.Site
import com.example.database.Worker
import com.example.ui.theme.*
import com.example.viewmodel.DateStats
import com.example.viewmodel.LaborViewModel
import com.example.viewmodel.WorkerAttendanceState
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    Splash,
    PinLock,
    Dashboard,
    Attendance,
    Workers,
    Reports,
    Sites,
    Settings
}

@Composable
fun MainAppContainer(viewModel: LaborViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.Splash) }
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Contact picker launcher registered unconditionally
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        if (contactUri != null) {
            try {
                val contentResolver = context.contentResolver
                var impName = ""
                var impPhone = ""
                var impPhoto: String? = null

                val cursor = contentResolver.query(contactUri, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val idCol = c.getColumnIndex(ContactsContract.Contacts._ID)
                        val nameCol = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val photoCol = c.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)

                        val contactId = if (idCol >= 0) c.getString(idCol) else ""
                        impName = if (nameCol >= 0) c.getString(nameCol) else ""
                        impPhoto = if (photoCol >= 0) c.getString(photoCol) else null

                        if (contactId.isNotEmpty()) {
                            val phoneCursor = contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                arrayOf(contactId),
                                null
                            )
                            phoneCursor?.use { pc ->
                                if (pc.moveToFirst()) {
                                    val phoneNumCol = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    impPhone = if (phoneNumCol >= 0) pc.getString(phoneNumCol) else ""
                                }
                            }
                        }
                    }
                }

                if (impName.isNotEmpty()) viewModel.importedName.value = impName
                if (impPhone.isNotEmpty()) {
                    viewModel.importedPhone.value = impPhone.replace("\\s".toRegex(), "").replace("-", "")
                }
                if (impPhoto != null) {
                    viewModel.importedPhoto.value = impPhoto
                }
                Toast.makeText(context, "تم استيراد جهة الاتصال: ${impName}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ في قراءة جهة الاتصال: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Permission requester launcher registered unconditionally
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        contactPickerLauncher.launch(null)
    }

    var showMultiContactImportDialog by remember { mutableStateOf(false) }

    val multiContactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadPhoneContacts(context.contentResolver)
            showMultiContactImportDialog = true
        } else {
            Toast.makeText(context, "الرجاء منح صلاحية الوصول لجهات الاتصال لاستيراد العمال", Toast.LENGTH_LONG).show()
        }
    }

    val onMultiImportClick = {
        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        )
        if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            viewModel.loadPhoneContacts(context.contentResolver)
            showMultiContactImportDialog = true
        } else {
            multiContactPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
        }
    }

    // Navigation state helper
    var previousScreen by remember { mutableStateOf(AppScreen.Dashboard) }

    // Worker detail viewing helper
    var selectedWorkerIdForDetails by remember { mutableStateOf<Int?>(null) }

    // Dialog state controllers
    var showAddWorkerDialog by remember { mutableStateOf(false) }
    var showAddSiteDialog by remember { mutableStateOf(false) }
    var showAddAdvanceDialogForWorker by remember { mutableStateOf<Worker?>(null) }
    var showAddOvertimeDialogForWorker by remember { mutableStateOf<Worker?>(null) }

    // Splash Timeout
    LaunchedEffect(Unit) {
        delay(1200) // Fast minimalist splash feel
        currentScreen = if (settings.pinEnabled) AppScreen.PinLock else AppScreen.Dashboard
    }

    Scaffold(
        topBar = {
            if (currentScreen != AppScreen.Splash && currentScreen != AppScreen.PinLock) {
                if (currentScreen == AppScreen.Workers && selectedWorkerIdForDetails == null) {
                    val constructionBlue = Color(0xFF0F2C59)
                    Surface(
                        color = if (settings.darkMode) MaterialTheme.colorScheme.surface else constructionBlue,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { 
                                    currentScreen = AppScreen.Dashboard
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = Color.White
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "قائمة العمال 👷",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                var showHeaderDropdown by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = {
                                        Toast.makeText(context, "الرجاء استخدام فلتر المواقع في لوحة إحصائيات الصفحة للتحكم الدقيق بالفرز.", Toast.LENGTH_LONG).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = "Filter",
                                        tint = Color.White
                                    )
                                }
                                
                                Box {
                                    IconButton(onClick = { showHeaderDropdown = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More",
                                            tint = Color.White
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showHeaderDropdown,
                                        onDismissRequest = { showHeaderDropdown = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("تبديل مظهر الليل والنهار") },
                                            onClick = {
                                                viewModel.updateDarkModeSetting(!settings.darkMode)
                                                showHeaderDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("إعدادات خيارات الأمان") },
                                            onClick = {
                                                currentScreen = AppScreen.Settings
                                                showHeaderDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Logo",
                                    tint = ConstructionSafetyYellow,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "مدير رواتب عمال البناء",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "نظام ذكي لإدارة الحضور وحسابات الأجور",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    viewModel.updateDarkModeSetting(!settings.darkMode)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Toggle Theme",
                                    tint = if (settings.darkMode) ConstructionSafetyYellow else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (currentScreen != AppScreen.Splash && currentScreen != AppScreen.PinLock) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val items = listOf(
                        Triple(AppScreen.Dashboard, Icons.Default.Home, "لوحة"),
                        Triple(AppScreen.Attendance, Icons.Default.CheckCircle, "التحضير"),
                        Triple(AppScreen.Workers, Icons.Default.Person, "العمال"),
                        Triple(AppScreen.Sites, Icons.Default.LocationOn, "المواقع"),
                        Triple(AppScreen.Reports, Icons.Default.List, "التقارير"),
                        Triple(AppScreen.Settings, Icons.Default.Settings, "الإعدادات")
                    )

                    items.forEach { (screen, icon, label) ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = {
                                selectedWorkerIdForDetails = null
                                currentScreen = screen
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = ConstructionSafetyYellow.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentScreen == AppScreen.Dashboard || currentScreen == AppScreen.Workers) {
                FloatingActionButton(
                    onClick = { showAddWorkerDialog = true },
                    containerColor = ConstructionSafetyYellow,
                    contentColor = Color.Black
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Worker",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = currentScreen,
                animationSpec = tween(durationMillis = 350),
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    AppScreen.Splash -> SplashScreen()
                    AppScreen.PinLock -> PinLockScreen(
                        correctPin = settings.pinCode,
                        onUnlocked = { currentScreen = AppScreen.Dashboard }
                    )
                    AppScreen.Dashboard -> DashboardView(
                        viewModel = viewModel,
                        onNavigateToAttendance = { currentScreen = AppScreen.Attendance },
                        onNavigateToWorkers = { currentScreen = AppScreen.Workers },
                        onAddSiteClick = { showAddSiteDialog = true }
                    )
                    AppScreen.Attendance -> AttendanceView(
                        viewModel = viewModel,
                        onWorkerDetailClick = { id ->
                            selectedWorkerIdForDetails = id
                            previousScreen = AppScreen.Attendance
                            currentScreen = AppScreen.Workers // Workers view contains detail nested panel
                        },
                        onQuickAdvanceClick = { worker -> showAddAdvanceDialogForWorker = worker },
                        onQuickOvertimeClick = { worker -> showAddOvertimeDialogForWorker = worker }
                    )
                    AppScreen.Workers -> WorkersView(
                        viewModel = viewModel,
                        selectedWorkerId = selectedWorkerIdForDetails,
                        onBackToWorkersList = { selectedWorkerIdForDetails = null },
                        onImportClick = {
                            permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                        },
                        onMultiImportClick = onMultiImportClick
                    )
                    AppScreen.Sites -> SitesView(
                        viewModel = viewModel,
                        onAddSiteClick = { showAddSiteDialog = true }
                    )
                    AppScreen.Reports -> ReportsView(viewModel = viewModel)
                    AppScreen.Settings -> SettingsView(viewModel = viewModel)
                }
            }
        }
    }

    // --- Core Action Modals (Shared across screens) ---

    if (showAddWorkerDialog) {
        AddWorkerDialog(
            viewModel = viewModel,
            onDismiss = { showAddWorkerDialog = false },
            onSave = { name, salary, rate, phone, notes, photoUri ->
                viewModel.addWorker(name, salary, rate, phone, notes, photoUri)
                showAddWorkerDialog = false
                Toast.makeText(context, "تم حفظ العامل بنجاح!", Toast.LENGTH_SHORT).show()
            },
            onImportClick = {
                permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            }
        )
    }

    if (showAddSiteDialog) {
        AddSiteDialog(
            onDismiss = { showAddSiteDialog = false },
            onSave = { name, location, notes ->
                viewModel.addSite(name, location, notes)
                showAddSiteDialog = false
                Toast.makeText(context, "تم حفظ موقع العمل بنجاح!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    showAddAdvanceDialogForWorker?.let { worker ->
        QuickAdvanceDialog(
            workerName = worker.fullName,
            onDismiss = { showAddAdvanceDialogForWorker = null },
            onSave = { amount ->
                viewModel.setAdvanceAmount(worker.id, amount)
                showAddAdvanceDialogForWorker = null
                Toast.makeText(context, "تم تسجيل السلفة بنجاح!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    showAddOvertimeDialogForWorker?.let { worker ->
        QuickOvertimeDialog(
            workerName = worker.fullName,
            onDismiss = { showAddOvertimeDialogForWorker = null },
            onSave = { hours ->
                viewModel.updateOvertimeHours(worker.id, hours - (viewModel.attendanceListState.value.find { it.worker.id == worker.id }?.attendance?.overtimeHours ?: 0.0))
                showAddOvertimeDialogForWorker = null
                Toast.makeText(context, "تم تحديث إضافي العامل!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showMultiContactImportDialog) {
        MultiContactImportDialog(
            viewModel = viewModel,
            onDismiss = { showMultiContactImportDialog = false },
            onImportCompleted = { count ->
                showMultiContactImportDialog = false
                Toast.makeText(context, "تم استيراد عدد $count من العمال بنجاح بأجر وإضافي موحد!", Toast.LENGTH_LONG).show()
            }
        )
    }
}

// Simple delay function helper
private suspend fun delay(timeMillis: Long) {
    kotlinx.coroutines.delay(timeMillis)
}

// --- SCREEN 1: SPLASH SCREEN ---
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        Color(0xFF0F172A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Elegant glowing outer circle for icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.07f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(
                            color = ConstructionSafetyYellow.copy(alpha = 0.15f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Construction Icon",
                        tint = ConstructionSafetyYellow,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "مـديري الرواتب والعمال",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "نظام ذكي متكامل للمقاولات وإدارة الأجور",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(40.dp))
            CircularProgressIndicator(
                color = ConstructionSafetyYellow,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// --- SCREEN 2: PIN LOCK SCREEN ---
@Composable
fun PinLockScreen(correctPin: String, onUnlocked: () -> Unit) {
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Elegant key shield icon with a glowing background
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "رمز حماية الحسابات",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "يرجى إدخال الرمز المكون من 4 أرقام للمتابعة",
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // PIN circles indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                for (i in 1..4) {
                    val active = enteredPin.length >= i
                    val color = if (pinError) {
                        MaterialTheme.colorScheme.error
                    } else if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    }
                    val size = if (active) 16.dp else 14.dp
                    
                    Box(
                        modifier = Modifier
                            .size(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(size)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Custom large numeric grid
            val buttons = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "مسح", "0", "تأكيد")
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (rowIndex in 0..3) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        for (colIndex in 0..2) {
                            val buttonText = buttons[rowIndex * 3 + colIndex]
                            val isSpecial = buttonText == "تأكيد" || buttonText == "مسح"
                            val containerColor = if (buttonText == "تأكيد") {
                                ConstructionSafetyYellow
                            } else if (buttonText == "مسح") {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                            val contentColor = if (buttonText == "تأكيد") {
                                Color.Black
                            } else if (buttonText == "مسح") {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }

                            Button(
                                onClick = {
                                    pinError = false
                                    when (buttonText) {
                                        "مسح" -> if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                        "تأكيد" -> {
                                            if (enteredPin == correctPin) {
                                                onUnlocked()
                                            } else {
                                                enteredPin = ""
                                                pinError = true
                                            }
                                        }
                                        else -> {
                                            if (enteredPin.length < 4) {
                                                enteredPin += buttonText
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                border = if (!isSpecial) BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)) else null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = containerColor,
                                    contentColor = contentColor
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = buttonText,
                                    fontSize = if (isSpecial) 14.sp else 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SCREEN 3: DASHBOARD VIEW ---
@Composable
fun DashboardView(
    viewModel: LaborViewModel,
    onNavigateToAttendance: () -> Unit,
    onNavigateToWorkers: () -> Unit,
    onAddSiteClick: () -> Unit
) {
    val date by viewModel.selectedDate.collectAsStateWithLifecycle()
    val stats by viewModel.dateStats.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Sleek Top Header Greeting
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Construction Crane Icon",
                tint = ConstructionSafetyYellow,
                modifier = Modifier.size(28.dp)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "مرحباً بك يا بشمهندس 👋",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "لوحة إدارة العمليات الإنشائية والأجور",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        // 2. Active Date Selection Card (Deep slate to blue gradient)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                Color(0xFF1E293B)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val cal = Calendar.getInstance()
                        cal.time = format.parse(date) ?: Date()
                        cal.add(Calendar.DAY_OF_YEAR, -1)
                        viewModel.selectedDate.value = format.format(cal.time)
                    },
                    modifier = Modifier
                        .background(color = Color.White.copy(alpha = 0.1f), shape = CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Previous Day",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                val dayName = remember(date) {
                    try {
                        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val parsedDate = format.parse(date) ?: Date()
                        val dayFormat = SimpleDateFormat("EEEE", Locale("ar"))
                        dayFormat.format(parsedDate)
                    } catch (e: Exception) {
                        ""
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "التاريخ واليوم النشط حالياً",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (dayName.isNotEmpty()) "$dayName، $date" else date,
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                IconButton(
                    onClick = {
                        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val cal = Calendar.getInstance()
                        cal.time = format.parse(date) ?: Date()
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                        viewModel.selectedDate.value = format.format(cal.time)
                    },
                    modifier = Modifier
                        .background(color = Color.White.copy(alpha = 0.1f), shape = CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Next Day",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Large high-visibility Stats counter grid
        Text(
            text = "إحصائيات اليوم وتحديثات الحضور والأجور",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardStatsCard(
                title = "الحاضرين اليوم",
                value = stats.presentCount.toString(),
                subtitle = "إجمالي عمال الموقع اليوم",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.surface,
                icon = Icons.Default.Person,
                iconColor = MaterialTheme.colorScheme.primary
            )
            DashboardStatsCard(
                title = "صافي الرواتب اليومية",
                value = "${stats.netTotal} ج.م",
                subtitle = "شامل الساعات الإضافية والسلف",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.surface,
                icon = Icons.Default.Check,
                iconColor = AccentTeal
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardStatsCard(
                title = "السلف اليومية",
                value = "${stats.totalAdvances} ج.م",
                subtitle = "سحب نقدي مؤقت مسجل اليوم",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.surface,
                icon = Icons.Default.Warning,
                iconColor = AbsentRed
            )
            DashboardStatsCard(
                title = "قيمة العمل الإضافي",
                value = "${stats.totalOvertimePay} ج.م",
                subtitle = "مستحقات إضافي العمال المعتمدة",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.surface,
                icon = Icons.Default.Star,
                iconColor = ConstructionSafetyYellow
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Actions panel (super easy one-hand entry!)
        Text(
            text = "لوحة التحكم السريعة وإجراءات المقاول",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Button(
            onClick = {
                viewModel.autoFillFromPreviousLoggedDay()
                Toast.makeText(context, "تم توليد تحضير اليوم بناءاً على حضور الأمس!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Autofill",
                    tint = ConstructionSafetyYellow
                )
                Text(
                    text = "نسخ تفصيلية لحضور الأمس تلقائياً (ملء ذكي)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onNavigateToAttendance,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ConstructionSafetyYellow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Add attendance", tint = Color.Black)
                    Text(
                        text = "رصد حضور العمال اليومي",
                        fontSize = 12.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Button(
                onClick = onAddSiteClick,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Add site", tint = Color.White)
                    Text(
                        text = "تأسيس موقع عمل جديد",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Guide Banner with zero typing notice
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFFEF08A)), // Subtle yellow border
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF9C3)) // Soft yellow background
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Tip",
                    tint = ConstructionSafetyYellow,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "نصيحة للمقاول: للتحضير فائق السرعة، انتقل لصفحة 'التحضير اليومي' واضغط ضغطة واحدة على اسم العامل لتأكيد حضوره وغيابه الفوري دون الحاجة للكتابة!",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = Color(0xFF854D0E), // Soft dark gold text
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DashboardStatsCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier.height(125.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    textAlign = TextAlign.Start
                )
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                color = iconColor.copy(alpha = 0.1f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor,
                    lineHeight = 26.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}


// --- SCREEN 4: DAILY ATTENDANCE SCREEN (MOST IMPORTANT VIEW) ---
@Composable
fun AttendanceView(
    viewModel: LaborViewModel,
    onWorkerDetailClick: (Int) -> Unit,
    onQuickAdvanceClick: (Worker) -> Unit,
    onQuickOvertimeClick: (Worker) -> Unit
) {
    val date by viewModel.selectedDate.collectAsStateWithLifecycle()
    val search by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeSiteId by viewModel.selectedSiteId.collectAsStateWithLifecycle()
    val sites by viewModel.allSites.collectAsStateWithLifecycle()
    val stateList by viewModel.attendanceListState.collectAsStateWithLifecycle()

    var showSiteFilterDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Attendance Date Banner + Live Indicator Row (Centered matching Image 13.html)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AccentTeal)
                )
                Text(
                    text = "حفظ فوري آمن",
                    fontSize = 11.sp,
                    color = AccentTeal,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = "دفتر الحضور والأجور اليومي",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "المسجلين اليوم: ${stateList.count { it.attendance != null }} من إجمالي ${stateList.size} عمال",
                fontSize = 13.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search + Site Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("البحث باسم عامل بناء...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon", tint = Color.Gray) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
            )

            // Site Filter drop-trigger
            Box {
                Button(
                    onClick = { showSiteFilterDropdown = true },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.secondary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                ) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Site filter")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (activeSiteId == null) "كل المواقع" else (sites.find { it.id == activeSiteId }?.name ?: "موقع"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                DropdownMenu(
                    expanded = showSiteFilterDropdown,
                    onDismissRequest = { showSiteFilterDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("جميع مواقع البناء والعمل", fontWeight = FontWeight.Bold) },
                        onClick = {
                            viewModel.selectedSiteId.value = null
                            showSiteFilterDropdown = false
                        }
                    )
                    sites.forEach { site ->
                        DropdownMenuItem(
                            text = { Text(site.name) },
                            onClick = {
                                viewModel.selectedSiteId.value = site.id
                                showSiteFilterDropdown = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Attendance Worker LazyList
        if (stateList.isEmpty()) {
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
                        tint = Color.LightGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "لا يوجد عمال متاحين للموقع المحدد.\nأضف عمالاً في تبويب العمال أولاً!",
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(stateList, key = { it.worker.id }) { state ->
                    AttendanceWorkerItemCard(
                        state = state,
                        onToggleStatus = { viewModel.toggleAttendanceStatus(state.worker.id) },
                        onOvertimeDelta = { delta -> viewModel.updateOvertimeHours(state.worker.id, delta) },
                        onQuickAdvanceClick = { onQuickAdvanceClick(state.worker) },
                        onQuickOvertimeClick = { onQuickOvertimeClick(state.worker) },
                        onWorkerDetailClick = { onWorkerDetailClick(state.worker.id) },
                        onAddNoteVal = { text ->
                            val currentAtt = state.attendance
                            if (currentAtt != null) {
                                viewModel.setAttendanceStatus(state.worker.id, currentAtt.status)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AttendanceWorkerItemCard(
    state: WorkerAttendanceState,
    onToggleStatus: () -> Unit,
    onOvertimeDelta: (Double) -> Unit,
    onQuickAdvanceClick: () -> Unit,
    onQuickOvertimeClick: () -> Unit,
    onWorkerDetailClick: () -> Unit,
    onAddNoteVal: (String) -> Unit
) {
    val att = state.attendance
    val isPresent = att?.status == "present"
    val logged = att != null

    // Real-time wage calculator for previewing
    val overHours = att?.overtimeHours ?: 0.0
    val advance = att?.advanceAmount ?: 0.0
    val deduction = att?.deductionAmount ?: 0.0
    val baseWage = if (isPresent) state.worker.dailySalary else 0.0
    val overWage = overHours * state.worker.overtimeHourRate
    val netWage = (baseWage + overWage) - advance - deduction

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPresent) AccentTeal.copy(alpha = 0.04f)
            else if (logged && att.status == "absent") AbsentRed.copy(alpha = 0.04f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isPresent) AccentTeal.copy(alpha = 0.25f)
            else if (logged && att.status == "absent") AbsentRed.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Worker core credentials row (RTL order matching Image 13.html)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // STATUS TOGGLE BUTTON (Large Touch Target on the Right in RTL)
                Button(
                    onClick = onToggleStatus,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPresent) AccentTeal
                        else if (logged && att.status == "absent") AbsentRed
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        contentColor = if (isPresent || (logged && att.status == "absent")) Color.White else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .widthIn(min = 115.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val statusText = if (isPresent) "حاضر" else if (logged && att.status == "absent") "غائب" else "غير مسجل"
                        val dotColor = if (isPresent) Color(0xFF10B981) else if (logged && att.status == "absent") Color(0xFFEF4444) else Color.Gray

                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isPresent || (logged && att.status == "absent")) Color.White else dotColor)
                        )
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Name & phone details (Left aligned on the Left in RTL)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onWorkerDetailClick() },
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = state.worker.fullName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "السعر اليومي: ${state.worker.dailySalary.toInt()} ج.م",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "سعر الإضافي: ${state.worker.overtimeHourRate.toInt()} ج.م",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Overtime & Financials Panel (renders ONLY if present with smooth AnimatedVisibility!)
            AnimatedVisibility(
                visible = isPresent,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Column 1: Draft Overtime
                        Button(
                            onClick = onQuickOvertimeClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                contentColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "مسودة إضافي", fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 13.sp)
                                if (overWage > 0.0) {
                                    Text(text = "$overWage ج.م", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        // Column 2: Record Advance
                        Button(
                            onClick = onQuickAdvanceClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ConstructionSafetyYellow.copy(alpha = 0.15f),
                                contentColor = Color(0xFFD97706)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ConstructionSafetyYellow.copy(alpha = 0.3f)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "سجل سلفة", fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 13.sp)
                                if (advance > 0.0) {
                                    Text(text = "$advance ج.م", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        // Column 3: Overtime Controls (- / +)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text(
                                text = "ساعات الإضافي اليوم", 
                                fontSize = 9.sp, 
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = { onOvertimeDelta(-0.5) },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                            shape = CircleShape
                                        )
                                ) {
                                    Text("-", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                }

                                Text(
                                    text = "$overHours س",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )

                                IconButton(
                                    onClick = { onOvertimeDelta(0.5) },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                            shape = CircleShape
                                        )
                                ) {
                                    Text("+", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Live Balance calculation preview bar!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "صافي اليومية: $netWage ج.م",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentTeal
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.clickable { onWorkerDetailClick() }
                        ) {
                            Text(
                                text = "تفاصيل الحساب اليومي",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


// --- SCREEN 5: WORKERS SCREEN (LIST + INTERNALS DETAIL) ---
@Composable
fun WorkersView(
    viewModel: LaborViewModel,
    selectedWorkerId: Int?,
    onBackToWorkersList: () -> Unit,
    onImportClick: () -> Unit,
    onMultiImportClick: () -> Unit
) {
    val workers by viewModel.allWorkers.collectAsStateWithLifecycle()
    val attendanceLogs by viewModel.allAttendance.collectAsStateWithLifecycle()
    val snapshots by viewModel.allPayrollSnapshots.collectAsStateWithLifecycle()
    val sites by viewModel.allSites.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var searchName by remember { mutableStateOf("") }
    var activeFilterSelectedOnly by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("name") } // "name", "salary", "overtime", "active"
    var selectedSiteFilter: Site? by remember { mutableStateOf(null) }

    // Dialog state for edit/delete worker
    var workerToEdit by remember { mutableStateOf<Worker?>(null) }
    var detailWorkerId by remember(selectedWorkerId) { mutableStateOf(selectedWorkerId) }
    var showAddDialogLocal by remember { mutableStateOf(false) }

    // If viewing single worker details
    detailWorkerId?.let { id ->
        val selectedWorker = workers.find { it.id == id }
        if (selectedWorker != null) {
            WorkerDetailSubScreen(
                worker = selectedWorker,
                attendanceHistory = attendanceLogs.filter { it.workerId == selectedWorker.id },
                payrollSnapshots = snapshots.filter { it.workerId == selectedWorker.id },
                onBack = {
                    detailWorkerId = null
                    onBackToWorkersList()
                },
                onGenerateSnapshotRange = { start, end ->
                    viewModel.generatePayrollSnapshot(selectedWorker.id, start, end)
                    Toast.makeText(context, "تم توليد قيد تاريخي للراتب وحفظه بالارشيف!", Toast.LENGTH_SHORT).show()
                }
            )
            return
        }
    }

    // Default: Multi-Workers list viewport
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FB)) // Clean modern off-white background matching the screenshot
    ) {
        // --- 1. Top Horizon Stats Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stats Card 1: Total Workers
            Card(
                modifier = Modifier
                    .width(135.dp)
                    .height(95.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "إجمالي العمال", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color(0xFF1B98E0), modifier = Modifier.size(16.dp))
                    }
                    Text(text = "${workers.size}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F2C59))
                    Text(text = "عامل مسجل", fontSize = 10.sp, color = Color.Gray)
                }
            }

            // Stats Card 2: Active Workers
            Card(
                modifier = Modifier
                    .width(135.dp)
                    .height(95.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "نشطين", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                    }
                    Text(text = "${workers.count { it.isActive }}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                    Text(text = "عامل", fontSize = 10.sp, color = Color.LightGray)
                }
            }

            // Stats Card 3: Inactive Workers
            Card(
                modifier = Modifier
                    .width(135.dp)
                    .height(95.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "غير نشطين", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                    }
                    Text(text = "${workers.count { !it.isActive }}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF9800))
                    Text(text = "عامل متوقف", fontSize = 10.sp, color = Color.LightGray)
                }
            }

            // Stats Card 4: Location/Project Filter Dropdown
            var showSiteDropdownFilter by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .width(160.dp)
                    .height(95.dp)
                    .clickable { showSiteDropdownFilter = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "الموقع الحالي", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = selectedSiteFilter?.name ?: "جميع المواقع",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F2C59),
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                        }
                        
                        Text(text = "انقر لتصفية العمال", fontSize = 9.sp, color = Color.Gray)
                    }

                    DropdownMenu(
                        expanded = showSiteDropdownFilter,
                        onDismissRequest = { showSiteDropdownFilter = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("جميع عمال المواقع") },
                            onClick = {
                                selectedSiteFilter = null
                                showSiteDropdownFilter = false
                            }
                        )
                        sites.forEach { site ->
                            DropdownMenuItem(
                                text = { Text(site.name) },
                                onClick = {
                                    selectedSiteFilter = site
                                    showSiteDropdownFilter = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- 2. Search, Sort, and Add Row ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Input Field
            OutlinedTextField(
                value = searchName,
                onValueChange = { searchName = it },
                placeholder = { Text("ابحث عن عامل بالإسم أو الهاتف...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                ),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) }
            )

            // Sorting Selection Dropdown
            var showSortDropdown by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { showSortDropdown = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = "Sort", modifier = Modifier.size(16.dp))
                        Text(text = "ترتيب", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                DropdownMenu(
                    expanded = showSortDropdown,
                    onDismissRequest = { showSortDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("الترتيب حسب الاسم") },
                        onClick = {
                            sortBy = "name"
                            showSortDropdown = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("الترتيب حسب اليومية الأكبر") },
                        onClick = {
                            sortBy = "salary_desc"
                            showSortDropdown = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("الترتيب حسب الإضافي الأكبر") },
                        onClick = {
                            sortBy = "overtime"
                            showSortDropdown = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("الترتيب حسب حالة النشاط أولاً") },
                        onClick = {
                            sortBy = "active"
                            showSortDropdown = false
                        }
                    )
                }
            }

            // Quick Add Worker Button
            Button(
                onClick = { showAddDialogLocal = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(56.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "إضافة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Quick Import Multiple Workers Button
            Button(
                onClick = onMultiImportClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ConstructionSafetyYellow,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(56.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(imageVector = Icons.Default.Phone, contentDescription = "Import Multiple Contacts", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "استيراد 📱", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 3. Filter and Sort logic ---
        val processedWorkers = remember(workers, attendanceLogs, searchName, selectedSiteFilter, sortBy) {
            var result = workers.filter {
                it.fullName.contains(searchName, ignoreCase = true) || 
                it.phone.contains(searchName)
            }

            // Filter by selected site if specified
            selectedSiteFilter?.let { filterSite ->
                val workerIdsAtSite = attendanceLogs
                    .filter { it.siteId == filterSite.id }
                    .map { it.workerId }
                    .toSet()
                result = result.filter { it.id in workerIdsAtSite }
            }

            // Apply sort orders
            when (sortBy) {
                "name" -> result.sortedBy { it.fullName }
                "salary_desc" -> result.sortedByDescending { it.dailySalary }
                "overtime" -> result.sortedByDescending { it.overtimeHourRate }
                "active" -> result.sortedByDescending { it.isActive }
                else -> result.sortedBy { it.fullName }
            }
        }

        // --- 4. Workers List ---
        if (processedWorkers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📝", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "لا توجد نتائج بحث مطابقة أو عمال بهذا المشروع.", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 76.dp)
            ) {
                items(processedWorkers, key = { it.id }) { worker ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { detailWorkerId = worker.id },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            
                            // Left Section: Menu Toggle, and Pill Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Dropdown menu trigger
                                Box {
                                    var isRowMenuExpanded by remember { mutableStateOf(false) }
                                    IconButton(onClick = { isRowMenuExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Options",
                                            tint = Color.Gray
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = isRowMenuExpanded,
                                        onDismissRequest = { isRowMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("عرض السجل المالي واليوميات") },
                                            onClick = {
                                                detailWorkerId = worker.id
                                                isRowMenuExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("تعديل بيانات العامل") },
                                            onClick = {
                                                workerToEdit = worker
                                                isRowMenuExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (worker.isActive) "تعطيل الملف (إيقاف)" else "تنشيط الملف") },
                                            onClick = {
                                                viewModel.updateWorker(worker.copy(isActive = !worker.isActive))
                                                isRowMenuExpanded = false
                                                Toast.makeText(context, "تم تغيير حالة العمل للعامل بنجاح", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("حذف العامل نهائياً", color = Color.Red) },
                                            onClick = {
                                                viewModel.deleteWorker(worker)
                                                isRowMenuExpanded = false
                                                Toast.makeText(context, "تم حذف العامل!", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }

                                // Status Pill Badge
                                Surface(
                                    shape = CircleShape,
                                    color = if (worker.isActive) AccentTeal.copy(alpha = 0.1f) else Color(0xFFFFECE0),
                                    border = BorderStroke(1.dp, if (worker.isActive) AccentTeal.copy(alpha = 0.3f) else Color(0xFFFFB74D))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (worker.isActive) Icons.Default.Check else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (worker.isActive) AccentTeal else Color(0xFFEF6C00),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = if (worker.isActive) "نشط" else "متوقف",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (worker.isActive) AccentTeal else Color(0xFFEF6C00)
                                        )
                                    }
                                }
                            }

                            // Middle Section: Wage stats columns matching screenshot layout
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "اليومية", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text(text = "${worker.dailySalary.toInt()}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = ConstructionAccent)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "أوفر تايم/ساعة", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text(text = "${worker.overtimeHourRate.toInt()}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF455A64))
                                }
                            }

                            // Right Section: Avatar and Name/Phone info
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = worker.fullName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = worker.phone.ifEmpty { "غير مسجل" },
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }

                                // Profile Picture Avatar with Status Indicator Dot
                                Box(modifier = Modifier.size(54.dp)) {
                                    val colors = listOf(Color(0xFFFEF08A), Color(0xFFBAE6FD), Color(0xFFFED7AA), Color(0xFFA7F3D0), Color(0xFFE9D5FF))
                                    val bg = colors[(worker.id and 0x7FFFFFFF) % colors.size]

                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(bg)
                                            .align(Alignment.Center),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!worker.photoUri.isNullOrEmpty()) {
                                            Image(
                                                painter = rememberAsyncImagePainter(model = Uri.parse(worker.photoUri)),
                                                contentDescription = worker.fullName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            val emoji = when ((worker.id and 0x7FFFFFFF) % 4) {
                                                0 -> "👷"
                                                1 -> "👷‍♂️"
                                                2 -> "👷‍♀️"
                                                else -> "🧑‍🏭"
                                            }
                                            Text(text = emoji, fontSize = 22.sp)
                                        }
                                    }

                                    // Status color dot matching the screenshot exactly
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .align(Alignment.BottomEnd)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(2.dp)
                                                .clip(CircleShape)
                                                .background(if (worker.isActive) AccentTeal else ConstructionSafetyYellow)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialogs inside screen
    if (showAddDialogLocal) {
        AddWorkerDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialogLocal = false },
            onSave = { name, salary, rate, phone, notes, photoUri ->
                viewModel.addWorker(name, salary, rate, phone, notes, photoUri)
                showAddDialogLocal = false
                Toast.makeText(context, "تم حفظ العامل بنجاح!", Toast.LENGTH_SHORT).show()
            },
            onImportClick = onImportClick
        )
    }

    workerToEdit?.let { worker ->
        EditWorkerDialog(
            worker = worker,
            onDismiss = { workerToEdit = null },
            onSave = { updated ->
                viewModel.updateWorker(updated)
                workerToEdit = null
                Toast.makeText(context, "تم تحديث بيانات العامل بنجاح!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// --- SUB SCREEN: WORKER PROFILE HISTORY & SNAPSHOT GEN ---
data class WorkerLiveStats(
    val days: Int,
    val hours: Double,
    val advances: Double,
    val deductions: Double,
    val base: Double,
    val ot: Double,
    val net: Double
)

@Composable
fun WorkerDetailSubScreen(
    worker: Worker,
    attendanceHistory: List<Attendance>,
    payrollSnapshots: List<PayrollSnapshot>,
    onBack: () -> Unit,
    onGenerateSnapshotRange: (String, String) -> Unit
) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    // Find the latest snapshot's end date to start the next one from tomorrow
    val lastSnapshot = remember(payrollSnapshots) {
        payrollSnapshots.maxByOrNull { it.endDate }
    }
    
    val initialStartDate = remember(lastSnapshot) {
        if (lastSnapshot != null) {
            // Add 1 day to the previous end date to prevent overlapping
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val cal = Calendar.getInstance()
                cal.time = sdf.parse(lastSnapshot.endDate) ?: Date()
                cal.add(Calendar.DAY_OF_YEAR, 1)
                sdf.format(cal.time)
            } catch (e: Exception) {
                "2026-05-01"
            }
        } else {
            // Default to 7 days ago
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -7)
                sdf.format(cal.time)
            } catch (e: Exception) {
                "2026-05-01"
            }
        }
    }

    var startDateRange by remember(initialStartDate) { mutableStateOf(initialStartDate) }
    var endDateRange by remember(today) { mutableStateOf(today) }
    val context = LocalContext.current

    // Helper for showing date pickers
    fun showDatePicker(currentDateStr: String, onDateSelected: (String) -> Unit) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsedDate = sdf.parse(currentDateStr) ?: Date()
            val cal = Calendar.getInstance().apply { time = parsedDate }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val day = cal.get(Calendar.DAY_OF_MONTH)

            android.app.DatePickerDialog(
                context,
                { _, sYear, sMonth, sDay ->
                    val selectedCal = Calendar.getInstance()
                    selectedCal.set(sYear, sMonth, sDay)
                    onDateSelected(sdf.format(selectedCal.time))
                },
                year, month, day
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "خطأ في فتح اختيار التاريخ", Toast.LENGTH_SHORT).show()
        }
    }

    // Live calculation based on selected range
    val liveStats = remember(attendanceHistory, startDateRange, endDateRange) {
        val rangeAtts = attendanceHistory.filter { it.date in startDateRange..endDateRange }
        val presentDays = rangeAtts.count { it.status == "present" }
        val totalOvertime = rangeAtts.sumOf { it.overtimeHours }
        val totalAdvances = rangeAtts.sumOf { it.advanceAmount }
        val totalDeductions = rangeAtts.sumOf { it.deductionAmount }
        
        val basePay = presentDays * worker.dailySalary
        val overtimePay = totalOvertime * worker.overtimeHourRate
        val netPay = (basePay + overtimePay) - totalAdvances - totalDeductions
        
        WorkerLiveStats(
            days = presentDays,
            hours = totalOvertime,
            advances = totalAdvances,
            deductions = totalDeductions,
            base = basePay,
            ot = overtimePay,
            net = netPay
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        item {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Profile header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = worker.fullName,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "جوال: ${worker.phone.ifEmpty { "غير متوفر" }}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "يومية العمل الأصليّة: ${worker.dailySalary} ج.م", color = Color.White, fontSize = 12.sp)
                        Text(text = "تعرفة الإضافي: ${worker.overtimeHourRate} ج.م", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Performance indicators
            Text(
                text = "ملخص سجل حضور وغياب العامل",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardStatsCard(
                    title = "أيام الحضور",
                    value = attendanceHistory.count { it.status == "present" }.toString(),
                    subtitle = "يوم عمل حقيقي",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.surface
                )

                DashboardStatsCard(
                    title = "إجمالي السلف",
                    value = "${attendanceHistory.sumOf { it.advanceAmount }} ج.م",
                    subtitle = "سحوبات مالية",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.surface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Soft and feature-rich flexible payment/clearance interface
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ConstructionSafetyYellow,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "تسوية وصرف الرواتب المرنة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "يدعم البرنامج الصرف الأسبوعي، أو كل أسبوعين، أو أي فترة مخصصة مرنة لتراكم الرواتب المتأخرة وتسويتها دفعة واحدة.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Preset periods
                    Text(
                        text = "اختر فترة الصرف السريعة:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            Pair("أسبوع (٧ أيام)", 6),
                            Pair("أسبوعين (١٤ يوم)", 13),
                            Pair("٣ أسابيع (٢١ يوم)", 20),
                            Pair("شهر (٣٠ يوم)", 29)
                        )
                        presets.forEach { (label, offset) ->
                            val isSelected = try {
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val startCal = Calendar.getInstance().apply { time = sdf.parse(startDateRange) ?: Date() }
                                val endCal = Calendar.getInstance().apply { time = sdf.parse(endDateRange) ?: Date() }
                                val diffInMillis = endCal.timeInMillis - startCal.timeInMillis
                                val diffInDays = (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
                                diffInDays == offset
                            } catch (e: Exception) {
                                false
                            }
                            
                            SuggestionChip(
                                onClick = {
                                    try {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                        val cal = Calendar.getInstance()
                                        cal.time = sdf.parse(startDateRange) ?: Date()
                                        cal.add(Calendar.DAY_OF_YEAR, offset)
                                        endDateRange = sdf.format(cal.time)
                                    } catch (e: Exception) {
                                        // ignore
                                    }
                                },
                                label = { Text(text = label, fontSize = 11.sp) },
                                border = BorderStroke(1.dp, if (isSelected) ConstructionSafetyYellow else Color.LightGray.copy(alpha = 0.5f)),
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isSelected) ConstructionSafetyYellow.copy(alpha = 0.15f) else Color.Transparent
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Date fields with Picker trigger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedCard(
                            onClick = { showDatePicker(startDateRange) { startDateRange = it } },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("تاريخ البدء", fontSize = 10.sp, color = Color.Gray)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(startDateRange, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Icon(imageVector = Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        
                        OutlinedCard(
                            onClick = { showDatePicker(endDateRange) { endDateRange = it } },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("تاريخ الانتهاء", fontSize = 10.sp, color = Color.Gray)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(endDateRange, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Icon(imageVector = Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Receipt preview (Breakdown card)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "🧾 بيان وحساب الدفعة المستحقة للفترة المختارة",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                            
                            // 1. Worked Days
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("أيام الحضور والعمل:", fontSize = 12.sp, color = Color.Gray)
                                Text("${liveStats.days} أيام  ←  ${liveStats.base} ج.م", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // 2. Overtime
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("العمل الإضافي المعوض:", fontSize = 12.sp, color = Color.Gray)
                                Text("${liveStats.hours} ساعة  ←  + ${liveStats.ot} ج.م", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // 3. Advances
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("إجمالي السحب المباشر وسُلفات الفترة:", fontSize = 12.sp, color = Color.Gray)
                                Text("- ${liveStats.advances} ج.م", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // 4. Other Deductions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("خصميات وعقوبات فترة العمل:", fontSize = 12.sp, color = Color.Gray)
                                Text("- ${liveStats.deductions} ج.م", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                            
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                            
                            // 5. Grand Net Payable
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "صافي المبلغ المتبقي للصرف الفوري:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${liveStats.net} ج.م",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (liveStats.net >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Final clear/approve payment button
                    Button(
                        onClick = {
                            if (liveStats.days == 0 && liveStats.hours == 0.0 && liveStats.advances == 0.0 && liveStats.deductions == 0.0) {
                                Toast.makeText(context, "الرجاء اختيار فترة تحتوي على حضور أو سُلف ليتم صرفها!", Toast.LENGTH_LONG).show()
                            } else {
                                onGenerateSnapshotRange(startDateRange, endDateRange)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ConstructionSafetyYellow,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(text = "اعتماد وتصفية وصرف الراتب للأرشيف (تسوية الفترة)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "الأرشيف التاريخي للرواتب المقفلة",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (payrollSnapshots.isEmpty()) {
            item {
                Text(text = "لا توجد أي رواتب مؤرشفة لهذا العامل بعد.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            }
        } else {
            items(payrollSnapshots) { snap ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "الفترة: ${snap.startDate} - ${snap.endDate}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(text = "مجمد بـ: " + SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(snap.generatedAt)), fontSize = 11.sp, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "أيام الحضور: ${snap.attendanceDays}", fontSize = 12.sp)
                            Text(text = "صافي المدفوع: " + snap.netSalary + " ج.م", fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}


// --- SCREEN 6: SITES VIEW (CONSTRUCTION SITES) ---
@Composable
fun SitesView(viewModel: LaborViewModel, onAddSiteClick: () -> Unit) {
    val sites by viewModel.allSites.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddSiteClick, 
                colors = ButtonDefaults.buttonColors(
                    containerColor = ConstructionSafetyYellow,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = "+ موقع جديد", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "مواقع البناء والعمل المفتوحة",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "عدد مواقع البناء النشطة: ${sites.size}",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (sites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "لم يتم تسجيل أي مواقع عمل بعد.\nاضغط على موقع جديد لتوزيع البناء والعمالة!",
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sites, key = { site -> site.id }) { site ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { viewModel.deleteSite(site.id) }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = AbsentRed)
                                }
                                Text(
                                    text = site.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = site.location.ifEmpty { "غير محدد" }, fontSize = 13.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "الموقع:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = site.notes.ifEmpty { "بلا ملاحظات" }, fontSize = 13.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "ملاحظات:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}


// --- SCREEN 7: REPORTS VIEW (PDF/EXCEL EXPORTS AND DRILLDOWN TABLE) ---
@Composable
fun ReportsView(viewModel: LaborViewModel) {
    val workers by viewModel.allWorkers.collectAsStateWithLifecycle()
    val allAttendance by viewModel.allAttendance.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Calculation outputs
    val totalWorkers = workers.size
    val totalSalaryToPay = allAttendance.filter { it.status == "present" }.sumOf { att ->
        val wk = workers.find { it.id == att.workerId }
        val daily = wk?.dailySalary ?: 0.0
        val over = att.overtimeHours * (wk?.overtimeHourRate ?: 0.0)
        daily + over - att.advanceAmount - att.deductionAmount
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "التقارير المالية وإحصائيات العمل",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "لوحة تقدير المصروفات التراكمية وسحب الرواتب للورش والمواقع",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }

        // Beautiful Gradient Metrics Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "عدد العمال المقيدين: $totalWorkers",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = "إجمالي مستحقات العمال التراكمية",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$totalSalaryToPay ج.م",
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        color = ConstructionSafetyYellow
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "شامل الأجر اليومي والعمل الإضافي مخصوماً منه السلفيات المدفوعة مسبقاً",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Direct File Export Triggers
        Text(
            text = "تصدير وطباعة المستندات المعتمدة",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val onSharePdfText = {
                exportReportsToPdf(context, workers, allAttendance)
            }

            val onShareExcelCsv = {
                val sb = StringBuilder()
                sb.append("اسم العامل,رقم الهاتف,أيام الحضور,أجر اليومية,إجمالي الإضافي,إجمالي السلف,إجمالي الخصومات,الصافي المستحق\n")
                
                workers.forEach { worker ->
                    val workerAtts = allAttendance.filter { it.workerId == worker.id }
                    val presentAtts = workerAtts.filter { it.status == "present" }
                    
                    val daysCount = presentAtts.size
                    val totalBase = presentAtts.size * worker.dailySalary
                    val totalOvertimePay = presentAtts.sumOf { it.overtimeHours * worker.overtimeHourRate }
                    val totalAdvance = workerAtts.sumOf { it.advanceAmount }
                    val totalDeduction = workerAtts.sumOf { it.deductionAmount }
                    val net = totalBase + totalOvertimePay - totalAdvance - totalDeduction
                    
                    sb.append("\"${worker.fullName}\",")
                    sb.append("\"${worker.phone}\",")
                    sb.append("$daysCount,")
                    sb.append("${worker.dailySalary},")
                    sb.append("$totalOvertimePay,")
                    sb.append("$totalAdvance,")
                    sb.append("$totalDeduction,")
                    sb.append("$net\n")
                }
                
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "جدول رواتب العمال إكسل")
                        putExtra(android.content.Intent.EXTRA_TEXT, sb.toString())
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val chooser = android.content.Intent.createChooser(intent, "تصدير الجدول إلى Excel / Google Sheets").apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    Toast.makeText(context, "فشل تصدير التقرير: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            Button(
                onClick = onSharePdfText,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.secondary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "PDF")
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "مشاركة تقرير 📝", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onShareExcelCsv,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentTeal.copy(alpha = 0.1f),
                    contentColor = AccentTeal
                ),
                border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.2f))
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "Excel")
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "شيت Excel 📊", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "معاينة الجدول الرقمي السريع للرواتب والأجر",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Table Header (Navy/Slate Background)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "صافي", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text(text = "الأيام", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                    Text(text = "اسم العامل", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1.5f), textAlign = TextAlign.Start)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(workers) { worker ->
                        val stats = allAttendance.filter { it.workerId == worker.id }
                        val presentDays = stats.count { it.status == "present" }
                        val advances = stats.sumOf { it.advanceAmount }
                        val otPay = stats.sumOf { it.overtimeHours * worker.overtimeHourRate }
                        val base = presentDays * worker.dailySalary
                        val net = base + otPay - advances

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "$net ج.م", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            Text(text = "$presentDays أيام", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                            Text(text = worker.fullName, fontSize = 13.sp, maxLines = 1, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1.5f), textAlign = TextAlign.Start)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), thickness = 1.dp)
                    }
                }
            }
        }
    }
}


// --- SCREEN 8: SETTINGS VIEW ---
@Composable
fun SettingsView(viewModel: LaborViewModel) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editPinValue by remember { mutableStateOf(settings.pinCode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "إعدادات النظام والخصوصية",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "تخصيص مستويات الأمان ومظهر البناء والخطوط والتحكم بالنظام",
            fontSize = 12.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Security Options (Lock App PIN)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "حماية التطبيق برمز PIN أمني",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "تفعيل قفل التطبيق بـ 4 أرقام لحماية سجلات الأجور والرواتب والعمال من المتطفلين أو الفقدان أثناء العمل بالمواقع والإنشاءات.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = settings.pinEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updatePinSetting(enabled, editPinValue)
                        }
                    )
                    Text(
                        text = "تمكين القفل الأمني PIN:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                }

                if (settings.pinEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editPinValue,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                editPinValue = it
                                viewModel.updatePinSetting(true, it)
                            }
                        },
                        label = { Text("أدخل الأرقام السرية الـ 4") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Appearance config (Light/Dark themes)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = settings.darkMode,
                    onCheckedChange = { viewModel.updateDarkModeSetting(it) }
                )
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "الوضع الداكن (مظهر الليل)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "تحويل ألوان التطبيق لدرجات داكنة ومريحة للعين في ظروف الإضاءة الضعيفة والمواقع المغلقة.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.End,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Font size custom scale configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "تعديل وملاءمة حجم خط نصوص التطبيق",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "اختر الحجم الأنسب لقراءة وتدقيق قائمة حضور عمال المقاولة والأجور بوضوح تام.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val scales = listOf(
                        1.0f to "عادي",
                        1.15f to "متوسط",
                        1.3f to "كبير",
                        1.45f to "ضخم جداً"
                    )
                    scales.forEach { (scale, name) ->
                        val isSelected = Math.abs(settings.fontScale - scale) < 0.05f
                        Button(
                            onClick = { viewModel.updateFontScaleSetting(scale) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.secondary
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(text = name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Backup and Restore (Offline Instant Sync)
        var showImportDialog by remember { mutableStateOf(false) }
        var pasteBackupText by remember { mutableStateOf("") }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "النسخ الاحتياطي واستعادة البيانات",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "بما أن التطبيق يعمل بالكامل دون إنترنت، يمكنك سحب نسخة احتياطية مشفرة نصياً وحفظها بأمان، أو استعادتها في أي وقت.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.exportBackup { jsonStr ->
                                if (jsonStr != null) {
                                    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clipData = android.content.ClipData.newPlainText("LaborBackup", jsonStr)
                                    clipboardManager.setPrimaryClip(clipData)
                                    Toast.makeText(context, "تم توليد النسخة الاحتياطية ونسخها إلى الحافظة بنجاح! يمكنك حفظها في الملاحظات أو واتساب.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "فشل توليد النسخة الاحتياطية!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "تصدير للحافظة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showImportDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.secondary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                    ) {
                        Text(text = "استيراد نسخة سابقة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.exportBackup { jsonStr ->
                            if (jsonStr != null) {
                                exportBackupToGoogleDrive(context, jsonStr)
                            } else {
                                Toast.makeText(context, "فشل تجهيز المزامنة!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ConstructionSafetyYellow,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Google Drive Sync", modifier = Modifier.size(18.dp), tint = Color.Black)
                        Text(text = "مزامنة ورفع احتياطي على Google Drive 💾", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showImportDialog) {
            Dialog(onDismissRequest = { showImportDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "استعادة البيانات", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "قم بلصق نص النسخة الاحتياطية الذي قمت بنسخه مسبقاً هنا لاستعادة كافة عمال ومواقع وحضور الورشة.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = pasteBackupText,
                            onValueChange = { pasteBackupText = it },
                            placeholder = { Text("الصق النص هنا...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            maxLines = 10,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (pasteBackupText.trim().isNotEmpty()) {
                                        viewModel.importBackup(pasteBackupText) { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            if (success) {
                                                showImportDialog = false
                                                pasteBackupText = ""
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "الرجاء لصق نص النسخة الاحتياطية أولاً!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("استعادة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    showImportDialog = false
                                    pasteBackupText = ""
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("إلغاء", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}


// --- SUPPORT DIALOGS ---

@Composable
fun AddWorkerDialog(
    viewModel: LaborViewModel,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, String, String, String?) -> Unit,
    onImportClick: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var salary by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // Observe imported data safely from the global state/ViewModel
    val impName by viewModel.importedName.collectAsStateWithLifecycle()
    val impPhone by viewModel.importedPhone.collectAsStateWithLifecycle()
    val impPhoto by viewModel.importedPhoto.collectAsStateWithLifecycle()

    LaunchedEffect(impName, impPhone, impPhoto) {
        if (impName.isNotEmpty()) {
            name = impName
        }
        if (impPhone.isNotEmpty()) {
            phone = impPhone
        }
        if (impPhoto != null) {
            photoUri = impPhoto
        }
        if (impName.isNotEmpty() || impPhone.isNotEmpty() || impPhoto != null) {
            viewModel.clearImportedContact()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "إضافة عامل جديد", fontWeight = FontWeight.Bold)
                
                // Beautiful Import Contact button (as requested)
                Button(
                    onClick = {
                        onImportClick()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Phone, contentDescription = "import", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "استيراد 📱", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                
                // Show imported profile photo snapshot if available
                if (photoUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(modifier = Modifier.size(64.dp)) {
                            Image(
                                painter = rememberAsyncImagePainter(model = Uri.parse(photoUri)),
                                contentDescription = "Imported Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            IconButton(
                                onClick = { photoUri = null },
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color.Red, CircleShape)
                                    .align(Alignment.TopEnd)
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("الاسم الكامل للعامل") }, 
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = salary, 
                    onValueChange = { salary = it }, 
                    label = { Text("يومية العمل الأصليّة (ج.م)") }, 
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rate, 
                    onValueChange = { rate = it }, 
                    label = { Text("سعر ساعة الإضافي (ج.م)") }, 
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone, 
                    onValueChange = { phone = it }, 
                    label = { Text("رقم الهاتف المحمول") }, 
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), 
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes, 
                    onValueChange = { notes = it }, 
                    label = { Text("ملاحظات إضافية") }, 
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty() && salary.isNotEmpty() && rate.isNotEmpty()) {
                        val parsedSalary = salary.toDoubleOrNull() ?: 100.0
                        val parsedRate = rate.toDoubleOrNull() ?: 15.0
                        onSave(
                            name, 
                            kotlin.math.abs(parsedSalary), 
                            kotlin.math.abs(parsedRate), 
                            phone, 
                            notes, 
                            photoUri
                        )
                    }
                }
            ) {
                Text("حـفـظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun EditWorkerDialog(
    worker: Worker,
    onDismiss: () -> Unit,
    onSave: (Worker) -> Unit
) {
    var name by remember { mutableStateOf(worker.fullName) }
    var salary by remember { mutableStateOf(worker.dailySalary.toString()) }
    var rate by remember { mutableStateOf(worker.overtimeHourRate.toString()) }
    var phone by remember { mutableStateOf(worker.phone) }
    var notes by remember { mutableStateOf(worker.notes) }
    var isActive by remember { mutableStateOf(worker.isActive) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "تعديل بيانات الملف للعامل", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم الكامل") }, singleLine = true)
                OutlinedTextField(value = salary, onValueChange = { salary = it }, label = { Text("يومية العمل الأصلية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("سعر ساعة الإضافي") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("رقم الهاتف") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") }, singleLine = true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "العامل نشط بالعمل حالياً:")
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty() && salary.isNotEmpty() && rate.isNotEmpty()) {
                        onSave(
                            worker.copy(
                                fullName = name,
                                dailySalary = kotlin.math.abs(salary.toDoubleOrNull() ?: worker.dailySalary),
                                overtimeHourRate = kotlin.math.abs(rate.toDoubleOrNull() ?: worker.overtimeHourRate),
                                phone = phone,
                                notes = notes,
                                isActive = isActive
                            )
                        )
                    }
                }
            ) {
                Text("تـعـديل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun AddSiteDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "بناء موقع عمل أو مشروع جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المشروع أو الورشة") }, singleLine = true)
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("الموقع الجغرافي أو العنوان") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات العمل الخاصة") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty()) {
                        onSave(name, location, notes)
                    }
                }
            ) {
                Text("بـنـاء")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun QuickAdvanceDialog(
    workerName: String,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "تسجيل سحب سُلفة لـ: $workerName", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(text = "أدخل قيمة المبلغ المسحوب نقداً اليوم للمساعد أو الفني من خزينة المقاول.", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("المبلغ التراكمي (ج.م)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (amount.isNotEmpty()) {
                        onSave(kotlin.math.abs(amount.toDoubleOrNull() ?: 0.0))
                    }
                }
            ) {
                Text("تسجيل سُلفة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun QuickOvertimeDialog(
    workerName: String,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var hours by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "رصد إضافي مخصص لـ: $workerName", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(text = "أدخل عدد ساعات العمل الكلية المنجزة خارج الدوام الأصلي.", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it },
                    label = { Text("إجمالي الساعات (مثال: 2.5)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (hours.isNotEmpty()) {
                        onSave(kotlin.math.abs(hours.toDoubleOrNull() ?: 0.0).coerceAtMost(24.0))
                    }
                }
            ) {
                Text("تحديث")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun MultiContactImportDialog(
    viewModel: LaborViewModel,
    onDismiss: () -> Unit,
    onImportCompleted: (count: Int) -> Unit
) {
    val contacts by viewModel.phoneContactsState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContacts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var unifiedSalary by remember { mutableStateOf("150") }
    var unifiedRate by remember { mutableStateOf("20") }
    var searchQuery by remember { mutableStateOf("") }

    val selectedPhones = remember { mutableStateMapOf<String, Boolean>() }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.trim().isEmpty()) {
            contacts
        } else {
            contacts.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.phone.contains(searchQuery)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "استيراد عمال متعددين 📱",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Text(
                    text = "اختر العمال من جهات الاتصال وحدد الأجر الموحد",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = unifiedRate,
                        onValueChange = { unifiedRate = it },
                        label = { Text("ساعة الإضافي الموحد") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    OutlinedTextField(
                        value = unifiedSalary,
                        onValueChange = { unifiedSalary = it },
                        label = { Text("اليومية الموحدة (ج.م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث في الأسماء أو الأرقام...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Contacts") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            filteredContacts.forEach {
                                selectedPhones[it.phone] = false
                            }
                        }
                    ) {
                        Text("إلغاء تحديد الكل", color = AbsentRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = {
                            filteredContacts.forEach {
                                selectedPhones[it.phone] = true
                            }
                        }
                    ) {
                        Text("تحديد الكل المصفى", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ConstructionSafetyYellow)
                        }
                    } else if (contacts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "لم يتم العثور على جهات اتصال.\nتأكد من وجود جهات اتصال بالهاتف.",
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    } else if (filteredContacts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "لا توجد نتائج بحث مطابقة",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredContacts) { contact ->
                                val isSelected = selectedPhones[contact.phone] ?: false
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            selectedPhones[contact.phone] = !isSelected
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { selectedPhones[contact.phone] = it }
                                    )
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = contact.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = contact.phone,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val selectedCount = selectedPhones.values.count { it }
            Button(
                onClick = {
                    val salary = unifiedSalary.toDoubleOrNull() ?: 150.0
                    val rate = unifiedRate.toDoubleOrNull() ?: 20.0
                    val toImport = contacts.filter { selectedPhones[it.phone] == true }
                    if (toImport.isNotEmpty()) {
                        viewModel.importMultipleWorkers(toImport, salary, rate)
                        onImportCompleted(toImport.size)
                    } else {
                        Toast.makeText(context, "يرجى تحديد جهة اتصال واحدة على الأقل للاستيراد", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ConstructionSafetyYellow,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "استيراد المحددين ($selectedCount)",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray)
            }
        }
    )
}

fun exportReportsToPdf(
    context: android.content.Context,
    workers: List<Worker>,
    allAttendance: List<Attendance>
) {
    try {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        
        val paint = android.graphics.Paint()
        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 18f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val subTitlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 10f
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 10f
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val boldTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 10f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val headerTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 11f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        
        // Deep Blue Header Banner
        paint.color = android.graphics.Color.parseColor("#0F2C59")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 110f, paint)
        
        // Accent Golden Line
        paint.color = android.graphics.Color.parseColor("#F59E0B")
        canvas.drawRect(0f, 110f, pageWidth.toFloat(), 114f, paint)
        
        // Title (Arabic)
        canvas.drawText("مؤسسة المهتدي للمقاولات العامة - El-MOHTADI", pageWidth - 30f, 45f, titlePaint)
        
        val dateString = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US).format(java.util.Date())
        canvas.drawText("تقرير مستحقات ورواتب العمال المالي التراكمي", pageWidth - 30f, 70f, subTitlePaint)
        canvas.drawText("تاريخ التصدير: $dateString", pageWidth - 30f, 90f, subTitlePaint)
        
        // Totals Summary Box
        val totalWorkers = workers.size
        val totalSalaryToPay = allAttendance.filter { it.status == "present" }.sumOf { att ->
            val wk = workers.find { it.id == att.workerId }
            val daily = wk?.dailySalary ?: 0.0
            val over = att.overtimeHours * (wk?.overtimeHourRate ?: 0.0)
            daily + over - att.advanceAmount - att.deductionAmount
        }
        
        paint.color = android.graphics.Color.parseColor("#F1F5F9")
        canvas.drawRoundRect(30f, 135f, pageWidth - 30f, 185f, 12f, 12f, paint)
        
        paint.color = android.graphics.Color.parseColor("#E2E8F0")
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(30f, 135f, pageWidth - 30f, 185f, 12f, 12f, paint)
        
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("إجمالي المقيدين: $totalWorkers عمال", pageWidth - 50f, 165f, boldTextPaint)
        
        val salaryPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#2E7D32")
            textSize = 13f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.LEFT
        }
        canvas.drawText("صافي المستحقات التراكمية: $totalSalaryToPay ج.م", 50f, 165f, salaryPaint)
        
        // Table Header
        paint.color = android.graphics.Color.parseColor("#0F2C59")
        canvas.drawRect(30f, 210f, pageWidth - 30f, 240f, paint)
        
        val colWidths = floatArrayOf(120f, 50f, 85f, 85f, 85f, 110f)
        val colHeaders = arrayOf("الصافي المستحق", "أوفرتايم", "إجمالي السلف", "أجر اليوميات", "الحضور", "اسم العامل")
        
        var currentX = 30f
        for (i in colHeaders.indices) {
            val align = if (i == colHeaders.size - 1) android.graphics.Paint.Align.RIGHT else if (i == 0) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.CENTER
            headerTextPaint.textAlign = align
            
            val textX = if (i == colHeaders.size - 1) currentX + colWidths[i] - 10f else if (i == 0) currentX + 10f else currentX + (colWidths[i] / 2)
            canvas.drawText(colHeaders[i], textX, 229f, headerTextPaint)
            currentX += colWidths[i]
        }
        
        // Table Rows
        var currentY = 240f
        paint.strokeWidth = 0.5f
        
        workers.forEachIndexed { index, worker ->
            val workerAtts = allAttendance.filter { it.workerId == worker.id }
            val presentAtts = workerAtts.filter { it.status == "present" }
            val daysCount = presentAtts.size
            val totalBase = presentAtts.size * worker.dailySalary
            val totalOvertimePay = presentAtts.sumOf { it.overtimeHours * worker.overtimeHourRate }
            val totalAdvance = workerAtts.sumOf { it.advanceAmount }
            val totalDeduction = workerAtts.sumOf { it.deductionAmount }
            val net = totalBase + totalOvertimePay - totalAdvance - totalDeduction
            
            paint.color = if (index % 2 == 0) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#F8FAFC")
            canvas.drawRect(30f, currentY, pageWidth - 30f, currentY + 30f, paint)
            
            paint.color = android.graphics.Color.parseColor("#E2E8F0")
            canvas.drawLine(30f, currentY + 30f, pageWidth - 30f, currentY + 30f, paint)
            
            val rowValues = arrayOf(
                "$net ج.م",
                "${presentAtts.sumOf { it.overtimeHours }} س",
                "$totalAdvance ج.م",
                "$totalBase  ج.م",
                "$daysCount أيام",
                worker.fullName
            )
            
            currentX = 30f
            for (i in rowValues.indices) {
                val align = if (i == rowValues.size - 1) android.graphics.Paint.Align.RIGHT else if (i == 0) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.CENTER
                val cellPaint = if (i == 0) boldTextPaint.apply { color = android.graphics.Color.parseColor("#0F2C59") } else textPaint.apply { color = android.graphics.Color.BLACK }
                cellPaint.textAlign = align
                
                val textX = if (i == rowValues.size - 1) currentX + colWidths[i] - 10f else if (i == 0) currentX + 10f else currentX + (colWidths[i] / 2)
                canvas.drawText(rowValues[i], textX, currentY + 18f, cellPaint)
                currentX += colWidths[i]
            }
            
            currentY += 30f
        }
        
        // Footer
        val footerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#64748B")
            textSize = 9f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText("شعارنا الأمان والإتقان - تم توليد التقرير آلياً بواسطة تطبيق El-MOHTADI", pageWidth / 2f, pageHeight - 30f, footerPaint)
        
        pdfDocument.finishPage(page)
        
        val pdfFile = java.io.File(context.cacheDir, "El-Mohtadi-Payroll-Report.pdf")
        val outputStream = java.io.FileOutputStream(pdfFile)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        outputStream.close()
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            pdfFile
        )
        
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(intent, "تصدير ومشاركة التقرير المالي كـ PDF").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        android.widget.Toast.makeText(context, "تم توليد وتصدير ملف PDF بنجاح!", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "فشل تصدير ملف PDF: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

fun exportBackupToGoogleDrive(context: android.content.Context, jsonBackupString: String) {
    try {
        val backupFile = java.io.File(context.cacheDir, "El-Mohtadi-Backup.json")
        val writer = java.io.FileWriter(backupFile)
        writer.write(jsonBackupString)
        writer.close()
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            backupFile
        )
        
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(intent, "حفظ النسخة الاحتياطية بمزامنة Google Drive").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        android.widget.Toast.makeText(context, "تم تجهيز النسخة الاحتياطية للمزامنة!", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "فشل تجهيز المزامنة: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

