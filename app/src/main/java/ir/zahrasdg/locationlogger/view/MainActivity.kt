package ir.zahrasdg.locationlogger.view

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import ir.zahrasdg.locationlogger.R
import ir.zahrasdg.locationlogger.model.UserStatus
import ir.zahrasdg.locationlogger.util.AppConstants
import ir.zahrasdg.locationlogger.viewmodel.MainViewModel
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


class MainActivity : BaseActivity<MainViewModel>(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    override val layoutId: Int
        get() = R.layout.activity_main

    override fun initViewModel(): MainViewModel {
        return ViewModelProviders.of(this).get(MainViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.title_activity_main)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        (mapFragment as SupportMapFragment).getMapAsync(this)

        val newStatusInsertedObserver = Observer<UserStatus> { userStatus ->
            userStatus?.let {
                addMarker(it)
            }
        }

        viewModel.location.observe(this, Observer { location ->
            location?.let {
                viewModel.logStatus(UserStatus(0, it.latitude, it.longitude, Calendar.getInstance().timeInMillis))
            }
        })

        viewModel.userStatuses.observe(this, androidx.lifecycle.Observer { statuses ->

            statuses?.let {
                if (it.isNotEmpty()) {
                    addMarkers(it)
                    viewModel.incrementPage()
                } else { // end of paging saved data
                    viewModel.newlyInsertedStatus.observe(this, newStatusInsertedObserver)
                    viewModel.userStatuses.removeObservers(this)
                }
            }

        })

        viewModel.locationSettingSatisfied.observe(this, Observer { satisfied ->
            satisfied?.let {
                if (!it) {
                    showLocationSetting()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()

        viewModel.checkLocationSettings()
        viewModel.startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()

        viewModel.stopLocationUpdates()
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        googleMap ?: return

        mMap = googleMap

        if (viewModel.locationPermissionGranted) {
            viewModel.getLastKnownLocation()
        } else {
            requestPermission()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        viewModel.handlePermissionResult(requestCode, grantResults)
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            AppConstants.PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
        )
    }

    private fun showLocationSetting() {
        val i = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        this.startActivityForResult(i, AppConstants.REQUEST_CHECK_SETTINGS)
    }

    private fun addMarkers(statuses: List<UserStatus>) {
        for (status in statuses) {
            addMarker(status)
        }
    }

    private fun addMarker(status: UserStatus) {
        mMap.addMarker(
            MarkerOptions().icon(getBitmapDescriptor(R.drawable.ic_dot))
                .position(LatLng(status.latitude, status.longitude))
        )
    }

    private fun getBitmapDescriptor(id: Int): BitmapDescriptor {
        val vectorDrawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getDrawable(id)
        } else {
            ContextCompat.getDrawable(this, id)
        }
        val bitmap = vectorDrawable?.intrinsicWidth?.let {
            Bitmap.createBitmap(
                it,
                vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888
            )
        }
        val canvas = Canvas(bitmap!!)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
