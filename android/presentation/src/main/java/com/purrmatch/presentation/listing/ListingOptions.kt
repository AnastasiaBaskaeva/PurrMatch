package com.purrmatch.presentation.listing

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import com.purrmatch.presentation.R

internal val breeds: List<String>
    @Composable get() = stringArrayResource(R.array.breeds).toList()

internal val cities: List<String>
    @Composable get() = stringArrayResource(R.array.cities).toList()
