package net.wildfyre.client.views

import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.annotation.IdRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.core.view.doOnLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.transaction
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.navigation.NavigationView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.main_app_bar.*
import kotlinx.android.synthetic.main.main_nav_header.*
import net.wildfyre.client.AppGlide
import net.wildfyre.client.Application
import net.wildfyre.client.Constants
import net.wildfyre.client.R
import net.wildfyre.client.databinding.MainNavHeaderBinding
import net.wildfyre.client.viewmodels.MainActivityViewModel
import java.io.ByteArrayInputStream

class MainActivity : FailureHandlingActivity(), NavigationView.OnNavigationItemSelectedListener {
    override lateinit var viewModel: MainActivityViewModel
    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        viewModel = ViewModelProviders.of(this).get(MainActivityViewModel::class.java)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = ActivityCompat.getColor(this, R.color.navigation)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

            if (nightMode == Configuration.UI_MODE_NIGHT_NO) {
                drawer_layout.systemUiVisibility =
                    drawer_layout.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }

        val binding = MainNavHeaderBinding.bind(navigation_drawer.getHeaderView(0))
        binding.lifecycleOwner = this
        binding.model = viewModel

        setSupportActionBar(toolbar)
        actionBarDrawerToggle = ActionBarDrawerToggle(
            this,
            drawer_layout,
            toolbar,
            R.string.main_drawer_open,
            R.string.main_drawer_close
        )

        val startingNightMode = Application.preferences
            .getInt(Constants.Preferences.UI_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val themeSelector = navigation_drawer.menu.findItem(R.id.theme_selector).actionView as Spinner?
        themeSelector?.run {
            adapter = ArrayAdapter<String>(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                viewModel.themes.map { pair -> getString(pair.first) })
            setSelection(viewModel.themes.indexOf(viewModel.themes.find { pair -> pair.second == startingNightMode }))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val theme = viewModel.themes[position].second
                    Application.preferences
                        .edit { putInt(Constants.Preferences.UI_THEME, theme) }
                    AppCompatDelegate.setDefaultNightMode(theme)
                    delegate.applyDayNight()
                }
            }
        }

        viewModel.userAvatar.observe(this, Observer {
            AppGlide.with(this)
                .load(it)
                .transform(RoundedCorners(resources.getDimension(R.dimen.nav_header_user_picture_rounding).toInt()))
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.userPicture)
        })
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawer_layout.addDrawerListener(actionBarDrawerToggle)
        actionBarDrawerToggle.syncState()
        navigation_drawer.setNavigationItemSelectedListener(this)
        navigation_drawer.doOnLayout { edit.setOnClickListener { editProfile() } }
        tryNavigateTo(
            savedInstanceState?.getInt(
                Constants.Save.ACTIVITY_NAVIGATION,
                R.id.fragment_home
            ) ?: R.id.fragment_home
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        navigation_drawer.checkedItem?.run { outState.putInt(Constants.Save.ACTIVITY_NAVIGATION, itemId) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        actionBarDrawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onAttachFragment(fragment: Fragment) {
        super.onAttachFragment(fragment)

        if (fragment is LoginFragment) {
            viewModel.authToken.observe(fragment, Observer {
                if (it.isNotEmpty()) {
                    viewModel.updateProfile()
                    navigateTo(R.id.fragment_home)
                }
            })
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val isLoginStep = isAtLogin()
        drawer_layout.setDrawerLockMode(if (isLoginStep) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED)
        actionBarDrawerToggle.isDrawerIndicatorEnabled = !isLoginStep
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return actionBarDrawerToggle.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when {
            viewModel.navigationLinks.containsKey(item.itemId) -> startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(viewModel.navigationLinks[item.itemId])
                )
            )
            item.isChecked -> return false
        }

        var closeDrawer = true
        val newFragment = when (item.itemId) {
            R.id.fragment_login,
            R.id.logout -> LoginFragment().also {
                if (item.itemId == R.id.logout) {
                    viewModel.clearAuthToken()
                    navigation_drawer.setCheckedItem(R.id.fragment_login)
                }
            }
            R.id.fragment_home -> HomeFragment()
            R.id.fragment_notifications -> NotificationsFragment()
            R.id.fragment_posts -> PostsFragment()
            else -> {
                closeDrawer = false
                null
            }
        }

        newFragment?.let {
            supportFragmentManager.transaction(true, true) {
                if (supportFragmentManager.fragments.size > 0) {
                    val isLoginStep = isAtLogin()
                    setCustomAnimations(
                        if (isLoginStep) R.anim.slide_in_right else R.anim.fade_in,
                        if (isLoginStep) R.anim.slide_out_left else R.anim.fade_out
                    )
                }

                replace(R.id.fragment_container, it)
            }
        }

        if (closeDrawer) {
            drawer_layout.closeDrawer(GravityCompat.START)
        }

        return closeDrawer
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || data == null) {
            return
        }

        if (requestCode == REQUEST_AVATAR) {
            lateinit var fileName: String
            lateinit var mimeType: String

            contentResolver.query(
                data.data!!,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE),
                null,
                null,
                null
            ).use {
                if (it!!.moveToFirst()) {
                    fileName = it.getString(it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME))
                    mimeType = it.getString(it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE))
                }
            }

            contentResolver.openInputStream(data.data!!).use {
                val bytes = it!!.readBytes()

                if (bytes.size < MAX_AVATAR_IMAGE_SIZE) {
                    viewModel.setPendingProfileAvatar(fileName, mimeType, bytes)
                } else {
                    Toast.makeText(this, R.string.failure_avatar_size, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun tryNavigateTo(@IdRes id: Int) {
        if (supportFragmentManager.fragments.size > 0) {
            return
        }

        val token = viewModel.authToken.value ?: ""
        navigateTo(if (token.isEmpty()) R.id.fragment_login else id)
    }

    private fun navigateTo(@IdRes id: Int) {
        onNavigationItemSelected(navigation_drawer.menu.findItem(id))
        navigation_drawer.setCheckedItem(id)
    }

    private fun editProfile() {
        lateinit var dialog: AlertDialog
        lateinit var avatarDataObserver: Observer<ByteArray>

        dialog = AlertDialog.Builder(this)
            .setView(R.layout.main_profile_editor)
            .setNegativeButton(android.R.string.cancel) { _: DialogInterface, _: Int ->
                viewModel.userAvatarNewData.removeObserver(avatarDataObserver)
                viewModel.resetPendingProfileAvatar()
            }
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                viewModel.userAvatarNewData.removeObserver(avatarDataObserver)
                viewModel.setProfile(dialog.findViewById<TextView>(R.id.user_bio)!!.text.toString())
                viewModel.resetPendingProfileAvatar()
            }
            .create()
            .apply { show() }

        val avatar = dialog.findViewById<ImageView>(R.id.user_picture)!!
        val rounding = RoundedCorners(resources.getDimension(R.dimen.dialog_user_picture_rounding).toInt())
        val transition = DrawableTransitionOptions.withCrossFade()

        AppGlide.with(this)
            .load(viewModel.userAvatar.value)
            .transform(rounding)
            .transition(transition)
            .into(avatar)

        avatarDataObserver = Observer {
            it?.run {
                val input = ByteArrayInputStream(this)
                AppGlide.with(this@MainActivity)
                    .load(Drawable.createFromStream(input, "avatar"))
                    .transform(rounding)
                    .transition(transition)
                    .into(avatar)
            }
        }

        viewModel.userAvatarNewData.observe(this, avatarDataObserver)

        avatar.setOnClickListener {
            startActivityForResult(
                Intent.createChooser(
                    Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" },
                    getString(R.string.main_profile_editor_avatar_chooser)
                ),
                REQUEST_AVATAR
            )
        }

        viewModel.userBio.value?.let {
            val bioEdit = dialog.findViewById<EditText>(R.id.user_bio)!!
            bioEdit.setText(it)

            if (it.isNotEmpty()) {
                bioEdit.minLines = 0
            }
        }
    }

    private fun isAtLogin() = supportFragmentManager.fragments.count { it is LoginFragment } > 0

    companion object {
        private const val REQUEST_AVATAR = 0
        private const val MAX_AVATAR_IMAGE_SIZE = 512 * 1024
    }
}