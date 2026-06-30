package com.example.mapicomandas.ui.screens.home

import androidx.lifecycle.ViewModel
import com.example.mapicomandas.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val session: SessionManager
) : ViewModel()
