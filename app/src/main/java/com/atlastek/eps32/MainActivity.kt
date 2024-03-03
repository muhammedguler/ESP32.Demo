package com.atlastek.eps32

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.atlastek.eps32.databinding.ActivityMainBinding
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    lateinit var viewDataBinding: ActivityMainBinding




    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.all { it.value }) {
                viewModel.connect()
            } else {
                Toast.makeText(this, "Ayarlardan gerekli izinleri veriniz.", Toast.LENGTH_LONG)
                    .show()
            }

        }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewDataBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        viewDataBinding.lifecycleOwner = this

        viewDataBinding.viewModel = viewModel
        viewModel.ekgCh1Series = LineGraphSeries<DataPoint>()
        viewModel.ekgCh1Series.color = Color.GREEN

        viewModel.ekgCh2Series = LineGraphSeries<DataPoint>()
        viewModel.ekgCh1Series.color = Color.BLACK

        viewModel.ekgCh3Series = LineGraphSeries<DataPoint>()
        viewModel.ekgCh1Series.color = Color.BLUE

        viewDataBinding.graphViewLine.addSeries(viewModel.ekgCh1Series)
        viewDataBinding.graphViewLine.addSeries(viewModel.ekgCh2Series)
        viewDataBinding.graphViewLine.addSeries(viewModel.ekgCh3Series)
        viewDataBinding.graphViewLine.viewport.isXAxisBoundsManual = true
        //viewDataBinding.graphViewLine.viewport.isYAxisBoundsManual = true
        viewDataBinding.graphViewLine.viewport.setMinX(0.0)
        viewDataBinding.graphViewLine.viewport.setMaxX(1.50)
        //viewDataBinding.graphViewLine.viewport.setMinY(0.0)
        //viewDataBinding.graphViewLine.viewport.setMaxY(1.0)

        viewDataBinding.connectButton.setOnClickListener {

            if (permissions.all {
                    (ContextCompat.checkSelfPermission(
                        this,
                        it
                    ) == PackageManager.PERMISSION_GRANTED)
                }) {
                viewModel.connect()
            } else {
                requestPermissionLauncher.launch(permissions)
            }
        }
    }
}