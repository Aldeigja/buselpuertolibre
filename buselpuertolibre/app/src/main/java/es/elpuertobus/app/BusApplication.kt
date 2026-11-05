package es.elpuertobus.app

import android.app.Application
import es.elpuertobus.app.locale.AppLocale

class BusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLocale.applyStored()
    }
}
