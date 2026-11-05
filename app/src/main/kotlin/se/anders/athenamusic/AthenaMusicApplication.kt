package se.anders.athenamusic

import android.app.Application
import se.anders.athenamusic.core.AppContainer

class AthenaMusicApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
