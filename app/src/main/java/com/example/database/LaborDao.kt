package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LaborDao {

    // --- Workers ---
    @Query("SELECT * FROM workers ORDER BY fullName ASC")
    fun getAllWorkersFlow(): Flow<List<Worker>>

    @Query("SELECT * FROM workers WHERE isActive = 1 ORDER BY fullName ASC")
    fun getActiveWorkersFlow(): Flow<List<Worker>>

    @Query("SELECT * FROM workers WHERE id = :id")
    fun getWorkerByIdFlow(id: Int): Flow<Worker?>

    @Query("SELECT * FROM workers WHERE id = :id")
    suspend fun getWorkerById(id: Int): Worker?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorker(worker: Worker): Long

    @Update
    suspend fun updateWorker(worker: Worker)

    @Delete
    suspend fun deleteWorker(worker: Worker)


    // --- Sites ---
    @Query("SELECT * FROM sites ORDER BY name ASC")
    fun getAllSitesFlow(): Flow<List<Site>>

    @Query("SELECT * FROM sites WHERE id = :id")
    suspend fun getSiteById(id: Int): Site?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSite(site: Site): Long

    @Query("DELETE FROM sites WHERE id = :id")
    suspend fun deleteSiteById(id: Int)


    // --- Attendance ---
    @Query("SELECT * FROM attendance ORDER BY date DESC, createdAt DESC")
    fun getAllAttendanceFlow(): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE date = :date")
    fun getAttendanceForDateFlow(date: String): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE date = :date")
    suspend fun getAttendanceForDate(date: String): List<Attendance>

    @Query("SELECT * FROM attendance WHERE workerId = :workerId")
    fun getAttendanceForWorkerFlow(workerId: Int): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE workerId = :workerId")
    suspend fun getAttendanceForWorker(workerId: Int): List<Attendance>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: Attendance): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendances(attendances: List<Attendance>)

    @Query("DELETE FROM attendance WHERE id = :id")
    suspend fun deleteAttendanceById(id: Int)

    @Query("DELETE FROM attendance WHERE date = :date AND workerId = :workerId")
    suspend fun deleteAttendanceForWorkerAndDate(workerId: Int, date: String)


    // --- Payroll Snapshot History ---
    @Query("SELECT * FROM payroll_history ORDER BY generatedAt DESC")
    fun getAllPayrollSnapshotsFlow(): Flow<List<PayrollSnapshot>>

    @Query("SELECT * FROM payroll_history WHERE workerId = :workerId ORDER BY startDate DESC")
    fun getPayrollSnapshotsForWorkerFlow(workerId: Int): Flow<List<PayrollSnapshot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayrollSnapshot(snapshot: PayrollSnapshot): Long


    // --- App Settings ---
    @Query("SELECT * FROM settings WHERE id = 1")
    fun getSettingsFlow(): Flow<AppSetting?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettings(): AppSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(setting: AppSetting): Long
}
