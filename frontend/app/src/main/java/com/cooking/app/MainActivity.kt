package com.cooking.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.cooking.app.repositories.CookingRepository
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    val repository by lazy { CookingRepository(applicationContext) }
    private lateinit var nav: NavController
    private val tabs = setOf(R.id.discover, R.id.favorites, R.id.shopping, R.id.community, R.id.profile)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val root = findViewById<View>(R.id.app_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }
        val host = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        nav = host.navController
        val bottom = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottom.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        nav.addOnDestinationChangedListener { _, destination, _ ->
            bottom.isVisible = destination.id in tabs
            if (destination.id in tabs) bottom.menu.findItem(destination.id).isChecked = true
        }
        if (repository.token.isNullOrBlank() && nav.currentDestination?.id !in setOf(R.id.login, R.id.register, R.id.reset)) {
            logOut()
        } else if (repository.token != null && nav.currentDestination?.id == R.id.login) enter()
    }

    fun enter() {
        nav.navigate(R.id.discover, null, NavOptions.Builder().setPopUpTo(R.id.main_nav, true).build())
    }

    fun logOut() {
        repository.token = null
        if (nav.currentDestination?.id != R.id.login) {
            nav.navigate(R.id.login, null, NavOptions.Builder().setPopUpTo(R.id.main_nav, true).build())
        }
    }

    fun showTab(id: Int, selectedGroup: Long? = null) {
        if (nav.currentDestination?.id == id) return
        val options = NavOptions.Builder().setPopUpTo(R.id.discover, false).setLaunchSingleTop(true).build()
        nav.navigate(id, selectedGroup?.let { bundleOf("group" to it) }, options)
    }
}
