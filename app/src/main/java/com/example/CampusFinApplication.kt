package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.repository.FinanceRepository
import com.example.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CampusFinApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: FinanceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = FinanceRepository(database)

        NotificationHelper.initNotificationChannels(this)

        // Populate sample initial data on first launch
        CoroutineScope(Dispatchers.IO).launch {
            repository.initializeDefaultDataIfEmpty()
        }
    }
}
