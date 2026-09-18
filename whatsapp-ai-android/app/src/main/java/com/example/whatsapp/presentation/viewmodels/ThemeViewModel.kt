package com.example.whatsapp.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    private val _isDarkMode = MutableStateFlow(sharedPreferences.getBoolean("dark_mode", false))
    val isDarkMode = _isDarkMode.asStateFlow()
    
    fun isDarkMode(): Boolean {
        return _isDarkMode.value
    }
    
    fun setDarkMode(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("dark_mode", enabled).apply()
        _isDarkMode.update { enabled }
    }
    
    init {
        _isDarkMode.value = sharedPreferences.getBoolean("dark_mode", false)
    }
}

