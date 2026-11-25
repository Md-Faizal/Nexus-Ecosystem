package com.nexus.portal.ui.screens.hub.apps.ridetracker

import androidx.lifecycle.ViewModel
import com.nexus.portal.services.RideTrackerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class RideTrackerViewModel @Inject constructor() : ViewModel() {
    
    // For now, observe the static state from the Service. 
    // In Step 4, we will fully decouple this so it observes from the Repository.
    val trackingState: StateFlow<RideTrackerService.Companion.TrackingState> = RideTrackerService.trackingState

}
