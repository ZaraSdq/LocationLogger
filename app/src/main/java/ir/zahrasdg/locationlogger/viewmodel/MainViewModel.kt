package ir.zahrasdg.locationlogger.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import ir.zahrasdg.locationlogger.model.UserStatus
import ir.zahrasdg.locationlogger.repo.LocationRepository
import ir.zahrasdg.locationlogger.repo.UserStatusRepository
import ir.zahrasdg.locationlogger.util.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.standalone.inject

class MainViewModel(application: Application) : BaseAndroidViewModel(application) {


    private val userStatusRepository by inject<UserStatusRepository>()
    private val locationRepository by inject<LocationRepository>()
    private var pageNumber = MutableLiveData<Int>()
    private var newlyInsertedId = MutableLiveData<Int>()
    val location = locationRepository.location
    var locationPermissionGranted = false
    var locationSettingSatisfied = MutableLiveData<Boolean>()
    var locationRequestStarted = false

    var userStatuses: LiveData<List<UserStatus>> = Transformations.switchMap(pageNumber, ::loadNextPage)
    var newlyInsertedStatus: LiveData<UserStatus> =
        Transformations.switchMap(newlyInsertedId, ::exportNewStatusInserted)

    init {
        pageNumber.value = 1
        checkLocationPermission()
    }

    fun incrementPage() {
        pageNumber.postValue(pageNumber.value?.inc())
    }

    private fun exportNewStatusInserted(newlyInsertedId: Int) =
        userStatusRepository.loadNewlyInsertedStatus(newlyInsertedId)

    private fun loadNextPage(pageNum: Int) = userStatusRepository.loadStatusPage(pageNum)

    fun logStatus(userStatus: UserStatus) {
        val newLocation = Location(LocationManager.GPS_PROVIDER)
        newLocation.latitude = userStatus.latitude
        newLocation.longitude = userStatus.longitude

        location.value?.let {
            if (it.distanceTo(newLocation) < 5f)
                return
        }

        insertStatus(userStatus)
    }

    private fun insertStatus(userStatus: UserStatus) = viewModelScope.launch(Dispatchers.IO) {
        newlyInsertedId.postValue(userStatusRepository.insert(userStatus).toInt())
    }

    fun startLocationUpdates() {
        locationRepository.startLocationUpdates()
        locationRequestStarted = true
    }

    fun stopLocationUpdates() {
        locationRepository.stopLocationUpdates()
        locationRequestStarted = false

    }

    fun getLastKnownLocation() {
        locationRepository.getLastKnownLocation()
    }

    private fun checkLocationPermission() {

        locationPermissionGranted = ContextCompat.checkSelfPermission(
            getApplication<Application>().applicationContext,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun checkLocationSettings() {

        val client: SettingsClient = LocationServices.getSettingsClient(getApplication<Application>())
        val task: Task<LocationSettingsResponse> =
            client.checkLocationSettings(locationRepository.locationSettingRequest)

        task.addOnSuccessListener {
            // All location settings are satisfied. The client can initialize
            // location requests here.
            locationSettingSatisfied.postValue(true)
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                locationSettingSatisfied.postValue(false)
            }
        }
    }

    fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
        locationPermissionGranted = false
        when (requestCode) {
            AppConstants.PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    locationPermissionGranted = true
                    getLastKnownLocation()
                }
            }
        }
    }
}