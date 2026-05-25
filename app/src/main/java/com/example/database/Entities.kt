package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey

@Entity(
    tableName = "workers"
)
data class Worker(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fullName: String,
    val dailySalary: Double,
    val overtimeHourRate: Double,
    val phone: String = "",
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val photoUri: String? = null
)

@Entity(tableName = "sites")
data class Site(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val location: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attendance",
    foreignKeys = [
        ForeignKey(
            entity = Worker::class,
            parentColumns = ["id"],
            childColumns = ["workerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["workerId", "date"], unique = true)
    ]
)
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val workerId: Int,
    val siteId: Int,
    val date: String, // String representation format "YYYY-MM-DD"
    val status: String, // "present" or "absent"
    val overtimeHours: Double = 0.0,
    val advanceAmount: Double = 0.0,
    val deductionAmount: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payroll_history",
    foreignKeys = [
        ForeignKey(
            entity = Worker::class,
            parentColumns = ["id"],
            childColumns = ["workerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["workerId"])
    ]
)
data class PayrollSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val workerId: Int,
    val startDate: String,
    val endDate: String,
    val attendanceDays: Int,
    val overtimeTotal: Double,
    val advancesTotal: Double,
    val deductionsTotal: Double,
    val netSalary: Double,
    val generatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class AppSetting(
    @PrimaryKey val id: Int = 1,
    val pinEnabled: Boolean = false,
    val pinCode: String = "",
    val darkMode: Boolean = false,
    val autoBackup: Boolean = false,
    val fontScale: Float = 1.0f
)
