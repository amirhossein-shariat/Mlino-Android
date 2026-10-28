package app.mlino

import android.app.Application
import app.mlino.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MlinoApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repo(this)
        appScope.launch { repo.seedCategories() }
    }
}
