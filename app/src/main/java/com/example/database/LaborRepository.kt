package com.example.database

import kotlinx.coroutines.flow.Flow

class LaborRepository(private val laborDao: LaborDao) {

    val allWorkers: Flow<List<Worker>> = laborDao.getAllWorkersFlow()
    val activeWorkers: Flow<List<Worker>> = laborDao.getActiveWorkersFlow()
    val allSites: Flow<List<Site>> = laborDao.getAllSitesFlow()
    val allAttendance: Flow<List<Attendance>> = laborDao.getAllAttendanceFlow()
    val allPayrollSnapshots: Flow<List<PayrollSnapshot>> = laborDao.getAllPayrollSnapshotsFlow()
    val appSettings: Flow<AppSetting?> = laborDao.getSettingsFlow()

    fun getWorkerById(id: Int): Flow<Worker?> = laborDao.getWorkerByIdFlow(id)
    
    suspend fun getWorkerByIdDirect(id: Int): Worker? = laborDao.getWorkerById(id)
    
    suspend fun insertWorker(worker: Worker): Long = laborDao.insertWorker(worker)
    
    suspend fun updateWorker(worker: Worker) = laborDao.updateWorker(worker)
    
    suspend fun deleteWorker(worker: Worker) = laborDao.deleteWorker(worker)

    suspend fun getSiteById(id: Int): Site? = laborDao.getSiteById(id)

    suspend fun insertSite(site: Site): Long = laborDao.insertSite(site)
    
    suspend fun deleteSiteById(id: Int) = laborDao.deleteSiteById(id)

    fun getAttendanceForDate(date: String): Flow<List<Attendance>> = laborDao.getAttendanceForDateFlow(date)
    
    suspend fun getAttendanceForDateDirect(date: String): List<Attendance> = laborDao.getAttendanceForDate(date)
    
    fun getAttendanceForWorker(workerId: Int): Flow<List<Attendance>> = laborDao.getAttendanceForWorkerFlow(workerId)
    
    suspend fun getAttendanceForWorkerDirect(workerId: Int): List<Attendance> = laborDao.getAttendanceForWorker(workerId)
    
    suspend fun insertAttendance(attendance: Attendance): Long = laborDao.insertAttendance(attendance)
    
    suspend fun insertAttendances(attendances: List<Attendance>) = laborDao.insertAttendances(attendances)
    
    suspend fun deleteAttendanceById(id: Int) = laborDao.deleteAttendanceById(id)
    
    suspend fun deleteAttendanceForWorkerAndDate(workerId: Int, date: String) = laborDao.deleteAttendanceForWorkerAndDate(workerId, date)

    fun getPayrollSnapshotsForWorker(workerId: Int): Flow<List<PayrollSnapshot>> = laborDao.getPayrollSnapshotsForWorkerFlow(workerId)
    
    suspend fun insertPayrollSnapshot(snapshot: PayrollSnapshot): Long = laborDao.insertPayrollSnapshot(snapshot)

    suspend fun getSettings(): AppSetting? = laborDao.getSettings()
    
    suspend fun saveSettings(setting: AppSetting) = laborDao.insertSettings(setting)
}
