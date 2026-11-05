package es.elpuertobus.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import es.elpuertobus.app.ui.MapScreen
import org.maplibre.android.MapLibre

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(applicationContext)
        enableEdgeToEdge()
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                MapScreen()
            }
        }
    }
}
