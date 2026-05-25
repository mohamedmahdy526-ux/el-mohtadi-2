package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class LaborViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LaborRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = LaborRepository(database.laborDao())
        
        // Initialize settings if empty
        viewModelScope.launch {
            if (repository.getSettings() == null) {
                repository.saveSettings(AppSetting(id = 1, pinEnabled = false, pinCode = "", darkMode = false, autoBackup = false, fontScale = 1.0f))
            }
        }
    }

    // --- State Variables ---
    val selectedDate = MutableStateFlow(getCurrentDateString())
    val selectedSiteId = MutableStateFlow<Int?>(null)
    val searchQuery = MutableStateFlow("")

    // Temporary variables for contact import
    val importedName = MutableStateFlow("")
    val importedPhone = MutableStateFlow("")
    val importedPhoto = MutableStateFlow<String?>(null)

    fun clearImportedContact() {
        importedName.value = ""
        importedPhone.value = ""
        importedPhoto.value = null
    }

    // Raw flows from Database
    val allWorkers: StateFlow<List<Worker>> = repository.allWorkers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeWorkers: StateFlow<List<Worker>> = repository.activeWorkers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSites: StateFlow<List<Site>> = repository.allSites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAttendance: StateFlow<List<Attendance>> = repository.allAttendance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPayrollSnapshots: StateFlow<List<PayrollSnapshot>> = repository.allPayrollSnapshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appSettings: StateFlow<AppSetting> = repository.appSettings
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSetting(id = 1))

    // Combined Flow for Attendance Screen
    val attendanceListState: StateFlow<List<WorkerAttendanceState>> = combine(
        allWorkers,
        selectedDate,
        allAttendance,
        selectedSiteId,
        searchQuery
    ) { workers, date, attendances, siteId, query ->
        val filteredWorkers = workers.filter { worker ->
            val matchesQuery = worker.fullName.contains(query, ignoreCase = true)
            // If filtering by site is selected, we filter workers who have attendance assigned to that site on this day
            val matchesSite = if (siteId != null) {
                val att = attendances.find { it.workerId == worker.id && it.date == date }
                att?.siteId == siteId
            } else {
                true
            }
            matchesQuery && matchesSite
        }

        filteredWorkers.map { worker ->
            val att = attendances.find { it.workerId == worker.id && it.date == date }
            WorkerAttendanceState(worker = worker, attendance = att)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Stats Calculations for current selectedDate ---
    val dateStats: StateFlow<DateStats> = combine(
        attendanceListState,
        allWorkers
    ) { attList, workers ->
        var presentCount = 0
        var absentCount = 0
        var totalPayroll = 0.0
        var totalAdvances = 0.0
        var totalOvertimePay = 0.0
        var totalDeductions = 0.0

        attList.forEach { state ->
            val att = state.attendance
            if (att != null) {
                if (att.status == "present") {
                    presentCount++
                    // Daily base salary
                    totalPayroll += state.worker.dailySalary
                    // Overtime pay
                    totalOvertimePay += (att.overtimeHours * state.worker.overtimeHourRate)
                } else {
                    absentCount++
                }
                totalAdvances += att.advanceAmount
                totalDeductions += att.deductionAmount
            }
        }

        val netTotalToday = (totalPayroll + totalOvertimePay) - totalAdvances - totalDeductions

        DateStats(
            presentCount = presentCount,
            absentCount = absentCount,
            totalSalaryBase = totalPayroll,
            totalOvertimePay = totalOvertimePay,
            totalAdvances = totalAdvances,
            totalDeductions = totalDeductions,
            netTotal = netTotalToday
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DateStats())

    // --- Helper to get Date string ---
    fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    // --- Worker Actions ---
    fun addWorker(name: String, salary: Double, rate: Double, phone: String, notes: String, photoUri: String? = null) {
        viewModelScope.launch {
            repository.insertWorker(
                Worker(
                    fullName = name,
                    dailySalary = salary,
                    overtimeHourRate = rate,
                    phone = phone,
                    notes = notes,
                    isActive = true,
                    photoUri = photoUri
                )
            )
        }
    }

    fun updateWorker(worker: Worker) {
        viewModelScope.launch {
            repository.updateWorker(worker)
        }
    }

    fun deleteWorker(worker: Worker) {
        viewModelScope.launch {
            repository.deleteWorker(worker)
        }
    }

    // --- Site Actions ---
    fun addSite(name: String, location: String, notes: String) {
        viewModelScope.launch {
            repository.insertSite(
                Site(
                    name = name,
                    location = location,
                    notes = notes
                )
            )
        }
    }

    fun deleteSite(id: Int) {
        viewModelScope.launch {
            repository.deleteSiteById(id)
        }
    }

    // --- Attendance Quick Actions (Fast Auto-Save!) ---
    fun toggleAttendanceStatus(workerId: Int) {
        viewModelScope.launch {
            val date = selectedDate.value
            val siteId = selectedSiteId.value ?: 1 // Default to general site (1) if none selected
            val existing = attendanceListState.value.find { it.worker.id == workerId }?.attendance
            
            if (existing == null) {
                // Not logged -> Mark as Present
                repository.insertAttendance(
                    Attendance(
                        workerId = workerId,
                        siteId = siteId,
                        date = date,
                        status = "present"
                    )
                )
            } else {
                // Logged -> Toggle status
                val newStatus = if (existing.status == "present") "absent" else "present"
                repository.insertAttendance(
                    existing.copy(status = newStatus)
                )
            }
        }
    }

    fun setAttendanceStatus(workerId: Int, status: String) {
        viewModelScope.launch {
            val date = selectedDate.value
            val siteId = selectedSiteId.value ?: 1
            val existing = attendanceListState.value.find { it.worker.id == workerId }?.attendance

            if (existing == null) {
                repository.insertAttendance(
                    Attendance(
                        workerId = workerId,
                        siteId = siteId,
                        date = date,
                        status = status
                    )
                )
            } else {
                repository.insertAttendance(
                    existing.copy(status = status)
                )
            }
        }
    }

    fun updateOvertimeHours(workerId: Int, delta: Double) {
        viewModelScope.launch {
            val date = selectedDate.value
            val siteId = selectedSiteId.value ?: 1
            val existing = attendanceListState.value.find { it.worker.id == workerId }?.attendance

            if (existing == null) {
                // If marking overtime without status, default to "present"
                val newHours = (0.0 + delta).coerceAtLeast(0.0)
                repository.insertAttendance(
                    Attendance(
                        workerId = workerId,
                        siteId = siteId,
                        date = date,
                        status = "present",
                        overtimeHours = newHours
                    )
                )
            } else {
                val newHours = (existing.overtimeHours + delta).coerceAtLeast(0.0)
                repository.insertAttendance(
                    existing.copy(overtimeHours = newHours)
                )
            }
        }
    }

    fun setAdvanceAmount(workerId: Int, amount: Double) {
        viewModelScope.launch {
            val date = selectedDate.value
            val siteId = selectedSiteId.value ?: 1
            val existing = attendanceListState.value.find { it.worker.id == workerId }?.attendance

            if (existing == null) {
                repository.insertAttendance(
                    Attendance(
                        workerId = workerId,
                        siteId = siteId,
                        date = date,
                        status = "present",
                        advanceAmount = amount
                    )
                )
            } else {
                repository.insertAttendance(
                    existing.copy(advanceAmount = amount)
                )
            }
        }
    }

    fun setDeductionAmount(workerId: Int, amount: Double) {
        viewModelScope.launch {
            val date = selectedDate.value
            val siteId = selectedSiteId.value ?: 1
            val existing = attendanceListState.value.find { it.worker.id == workerId }?.attendance

            if (existing == null) {
                repository.insertAttendance(
                    Attendance(
                        workerId = workerId,
                        siteId = siteId,
                        date = date,
                        status = "present",
                        deductionAmount = amount
                    )
                )
            } else {
                repository.insertAttendance(
                    existing.copy(deductionAmount = amount)
                )
            }
        }
    }

    // --- Auto Fill from Yesterday ---
    fun autoFillFromPreviousLoggedDay() {
        viewModelScope.launch {
            val currentDate = selectedDate.value
            val currentLogged = repository.getAttendanceForDateDirect(currentDate)
            if (currentLogged.isNotEmpty()) return@launch // Prevent overwriting if logged today

            val allAtts = repository.allAttendance.firstOrNull() ?: return@launch
            val previousDates = allAtts.filter { it.date < currentDate }.map { it.date }.distinct().sortedDescending()
            if (previousDates.isNotEmpty()) {
                val previousDate = previousDates.first()
                val previousRecords = repository.getAttendanceForDateDirect(previousDate)
                val newRecords = previousRecords.map {
                    Attendance(
                        workerId = it.workerId,
                        siteId = it.siteId,
                        date = currentDate,
                        status = it.status,
                        overtimeHours = it.overtimeHours,
                        advanceAmount = 0.0, // Clear advancements
                        deductionAmount = 0.0, // Clear deductions
                        notes = "Auto-filled from $previousDate"
                    )
                }
                repository.insertAttendances(newRecords)
            }
        }
    }

    // --- Payroll Snapshot Generator (Preserves historical data!) ---
    fun generatePayrollSnapshot(workerId: Int, startDate: String, endDate: String) {
        viewModelScope.launch {
            val worker = repository.getWorkerByIdDirect(workerId) ?: return@launch
            val allAtts = repository.allAttendance.firstOrNull() ?: return@launch
            val workerAttsInRange = allAtts.filter { 
                it.workerId == workerId && it.date >= startDate && it.date <= endDate 
            }

            val presentDays = workerAttsInRange.count { it.status == "present" }
            val totalOvertime = workerAttsInRange.sumOf { it.overtimeHours }
            val totalAdvances = workerAttsInRange.sumOf { it.advanceAmount }
            val totalDeductions = workerAttsInRange.sumOf { it.deductionAmount }

            val totalBasePay = presentDays * worker.dailySalary
            val totalOvertimePay = totalOvertime * worker.overtimeHourRate
            val netSalary = (totalBasePay + totalOvertimePay) - totalAdvances - totalDeductions

            repository.insertPayrollSnapshot(
                PayrollSnapshot(
                    workerId = workerId,
                    startDate = startDate,
                    endDate = endDate,
                    attendanceDays = presentDays,
                    overtimeTotal = totalOvertime,
                    advancesTotal = totalAdvances,
                    deductionsTotal = totalDeductions,
                    netSalary = netSalary,
                    generatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // --- AppSettings Actions ---
    fun updateDarkModeSetting(enabled: Boolean) {
        viewModelScope.launch {
            val current = appSettings.value
            repository.saveSettings(current.copy(darkMode = enabled))
        }
    }

    fun updatePinSetting(enabled: Boolean, pin: String) {
        viewModelScope.launch {
            val current = appSettings.value
            repository.saveSettings(current.copy(pinEnabled = enabled, pinCode = pin))
        }
    }

    fun updateFontScaleSetting(scale: Float) {
        viewModelScope.launch {
            val current = appSettings.value
            repository.saveSettings(current.copy(fontScale = scale))
        }
    }

    // --- Robust Backup & Restore Actions ---
    fun exportBackup(onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val workers = allWorkers.value
                val sites = allSites.value
                val attendance = allAttendance.value
                val snapshots = allPayrollSnapshots.value
                val settings = appSettings.value

                val json = org.json.JSONObject()
                
                // Workers
                val workersArray = org.json.JSONArray()
                workers.forEach {
                    val w = org.json.JSONObject()
                    w.put("id", it.id)
                    w.put("fullName", it.fullName)
                    w.put("dailySalary", it.dailySalary)
                    w.put("overtimeHourRate", it.overtimeHourRate)
                    w.put("phone", it.phone)
                    w.put("notes", it.notes)
                    w.put("isActive", it.isActive)
                    w.put("createdAt", it.createdAt)
                    w.put("photoUri", it.photoUri ?: "")
                    workersArray.put(w)
                }
                json.put("workers", workersArray)

                // Sites
                val sitesArray = org.json.JSONArray()
                sites.forEach {
                    val s = org.json.JSONObject()
                    s.put("id", it.id)
                    s.put("name", it.name)
                    s.put("location", it.location)
                    s.put("notes", it.notes)
                    s.put("createdAt", it.createdAt)
                    sitesArray.put(s)
                }
                json.put("sites", sitesArray)

                // Attendance
                val attArray = org.json.JSONArray()
                attendance.forEach {
                    val a = org.json.JSONObject()
                    a.put("id", it.id)
                    a.put("workerId", it.workerId)
                    a.put("siteId", it.siteId)
                    a.put("date", it.date)
                    a.put("status", it.status)
                    a.put("overtimeHours", it.overtimeHours)
                    a.put("advanceAmount", it.advanceAmount)
                    a.put("deductionAmount", it.deductionAmount)
                    a.put("notes", it.notes)
                    a.put("createdAt", it.createdAt)
                    attArray.put(a)
                }
                json.put("attendance", attArray)

                // Snapshots
                val snapArray = org.json.JSONArray()
                snapshots.forEach {
                    val sn = org.json.JSONObject()
                    sn.put("id", it.id)
                    sn.put("workerId", it.workerId)
                    sn.put("startDate", it.startDate)
                    sn.put("endDate", it.endDate)
                    sn.put("attendanceDays", it.attendanceDays)
                    sn.put("overtimeTotal", it.overtimeTotal)
                    sn.put("advancesTotal", it.advancesTotal)
                    sn.put("deductionsTotal", it.deductionsTotal)
                    sn.put("netSalary", it.netSalary)
                    sn.put("generatedAt", it.generatedAt)
                    snapArray.put(sn)
                }
                json.put("snapshots", snapArray)

                // Settings
                val setObj = org.json.JSONObject()
                setObj.put("pinEnabled", settings.pinEnabled)
                setObj.put("pinCode", settings.pinCode)
                setObj.put("darkMode", settings.darkMode)
                setObj.put("autoBackup", settings.autoBackup)
                setObj.put("fontScale", settings.fontScale)
                json.put("settings", setObj)

                onComplete(json.toString(4))
            } catch (e: Exception) {
                onComplete(null)
            }
        }
    }

    fun importBackup(jsonString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = org.json.JSONObject(jsonString)
                
                val db = AppDatabase.getDatabase(getApplication())

                // Parse Workers
                val workersArray = json.optJSONArray("workers")
                val workerList = mutableListOf<Worker>()
                if (workersArray != null) {
                    for (i in 0 until workersArray.length()) {
                        val w = workersArray.getJSONObject(i)
                        workerList.add(
                            Worker(
                                id = w.optInt("id", 0),
                                fullName = w.getString("fullName"),
                                dailySalary = w.getDouble("dailySalary"),
                                overtimeHourRate = w.getDouble("overtimeHourRate"),
                                phone = w.optString("phone", ""),
                                notes = w.optString("notes", ""),
                                isActive = w.optBoolean("isActive", true),
                                createdAt = w.optLong("createdAt", System.currentTimeMillis()),
                                photoUri = w.optString("photoUri", "").takeIf { it.isNotEmpty() }
                            )
                        )
                    }
                }

                // Parse Sites
                val sitesArray = json.optJSONArray("sites")
                val siteList = mutableListOf<Site>()
                if (sitesArray != null) {
                    for (i in 0 until sitesArray.length()) {
                        val s = sitesArray.getJSONObject(i)
                        siteList.add(
                            Site(
                                id = s.optInt("id", 0),
                                name = s.getString("name"),
                                location = s.optString("location", ""),
                                notes = s.optString("notes", ""),
                                createdAt = s.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                }

                // Parse Attendance
                val attArray = json.optJSONArray("attendance")
                val attList = mutableListOf<Attendance>()
                if (attArray != null) {
                    for (i in 0 until attArray.length()) {
                        val a = attArray.getJSONObject(i)
                        attList.add(
                            Attendance(
                                id = a.optInt("id", 0),
                                workerId = a.getInt("workerId"),
                                siteId = a.getInt("siteId"),
                                date = a.getString("date"),
                                status = a.getString("status"),
                                overtimeHours = a.optDouble("overtimeHours", 0.0),
                                advanceAmount = a.optDouble("advanceAmount", 0.0),
                                deductionAmount = a.optDouble("deductionAmount", 0.0),
                                notes = a.optString("notes", ""),
                                createdAt = a.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                }

                // Parse Snapshots
                val snapArray = json.optJSONArray("snapshots")
                val snapList = mutableListOf<PayrollSnapshot>()
                if (snapArray != null) {
                    for (i in 0 until snapArray.length()) {
                        val sn = snapArray.getJSONObject(i)
                        snapList.add(
                            PayrollSnapshot(
                                id = sn.optInt("id", 0),
                                workerId = sn.getInt("workerId"),
                                startDate = sn.getString("startDate"),
                                endDate = sn.getString("endDate"),
                                attendanceDays = sn.getInt("attendanceDays"),
                                overtimeTotal = sn.optDouble("overtimeTotal", 0.0),
                                advancesTotal = sn.optDouble("advancesTotal", 0.0),
                                deductionsTotal = sn.optDouble("deductionsTotal", 0.0),
                                netSalary = sn.getDouble("netSalary"),
                                generatedAt = sn.optLong("generatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                }

                // Parse Settings
                val setObj = json.optJSONObject("settings")
                val setting = if (setObj != null) {
                    AppSetting(
                        id = 1,
                        pinEnabled = setObj.optBoolean("pinEnabled", false),
                        pinCode = setObj.optString("pinCode", ""),
                        darkMode = setObj.optBoolean("darkMode", false),
                        autoBackup = setObj.optBoolean("autoBackup", false),
                        fontScale = setObj.optDouble("fontScale", 1.0).toFloat()
                    )
                } else {
                    AppSetting(id = 1)
                }

                // Save to DB
                db.clearAllTables() // Clears all tables
                
                workerList.forEach { repository.insertWorker(it) }
                siteList.forEach { repository.insertSite(it) }
                attList.forEach { repository.insertAttendance(it) }
                snapList.forEach { repository.insertPayrollSnapshot(it) }
                repository.saveSettings(setting)

                onResult(true, "تم استعادة النسخة الاحتياطية وإعادة تهيئة البيانات بنجاح!")
            } catch (e: Exception) {
                onResult(false, "خطأ في قراءة ملف الاستعادة: ${e.message}")
            }
        }
    }
}

// Domain Models & State Wrappers
data class WorkerAttendanceState(
    val worker: Worker,
    val attendance: Attendance?
)

data class DateStats(
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val totalSalaryBase: Double = 0.0,
    val totalOvertimePay: Double = 0.0,
    val totalAdvances: Double = 0.0,
    val totalDeductions: Double = 0.0,
    val netTotal: Double = 0.0
)
