package com.example.mapicomandas.ui.screens.home

import androidx.lifecycle.ViewModel
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.db.JdbcDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val session: SessionManager,
    db: JdbcDataSource
) : ViewModel() {
    val conectado: StateFlow<Boolean> = db.conectado
}
