package com.cooking.app.activities

import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import androidx.navigation.fragment.findNavController
import com.cooking.app.R
import com.cooking.app.models.*
import com.cooking.app.utils.photoPart

abstract class AccountFragment : PageFragment() {
    protected fun brand(hero: Boolean = false) {
        val logo = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_pot)
            contentDescription = getString(R.string.app_name)
        }
        body.addView(logo, LinearLayout.LayoutParams(dp(55), dp(55)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(12)
        })
        title(getString(R.string.app_name), size = 34).gravity = Gravity.CENTER
        text(getString(R.string.brand_tagline), 14, false, R.color.orange).also {
            it.gravity = Gravity.CENTER
            body.add(it, 4)
        }
        if (hero) image(body, "asset:pho_bo", 176, "Phở bò Việt Nam")
    }
}

class LoginFragment : AccountFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.isVisible = false
        brand(hero = true)
        val handle = findNavController().currentBackStackEntry?.savedStateHandle
        if (handle?.remove<Boolean>("resetDone") == true) success("Đã đặt lại mật khẩu")
        title("Đăng nhập", size = 26)
        empty("Chào mừng bạn trở lại với Bếp Nhà.")
        val identifier = field(body, "Email hoặc số điện thoại", limit = 254)
        val password = field(body, "Mật khẩu", type = PASSWORD)
        val error = lineError()
        body.add(action("Đăng nhập", orange = true) { button ->
            if (identifier.value().isBlank() || password.value().isEmpty()) {
                error.showError("Vui lòng nhập email hoặc số điện thoại và mật khẩu")
                return@action
            }
            hideKeyboard()
            error.showError("")
            work(button, error = { error.showError(it) }) {
                repo.token = api.login(Login(identifier.value(), password.value())).token
                app.enter()
            }
        }, 12)
        body.add(link("Quên mật khẩu?") { go(R.id.reset) }, 4)
        body.add(link("Chưa có tài khoản? Đăng ký") { go(R.id.register) }, 18)
    }
}

class RegisterFragment : AccountFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header("", back = true)
        brand()
        title("Đăng ký", size = 26)
        empty("Tạo tài khoản để lưu công thức và khám phá món ngon mỗi ngày.")
        val name = field(body, "Họ và tên", limit = 120)
        val email = field(body, "Email", type = EMAIL, limit = 254)
        val phone = field(body, "Số điện thoại", type = InputType.TYPE_CLASS_PHONE)
        val password = field(body, "Mật khẩu", type = PASSWORD)
        val confirm = field(body, "Xác nhận mật khẩu", type = PASSWORD)
        val error = lineError()
        val create = action("Tạo tài khoản", orange = true) { button ->
            hideKeyboard()
            error.showError("")
            work(button, error = { error.showError(it) }) {
                val result = api.register(Register(name.value(), email.value(), phone.value(), password.value(), confirm.value()))
                success(result.message)
                button.isEnabled = false
                button.isVisible = false
                listOf(name, email, phone, password, confirm).forEach { it.isEnabled = false }
            }
        }
        body.add(create, 10)
        body.add(link("Đã có tài khoản? Đăng nhập") { findNavController().navigateUp() }, 8)
    }
}

class ResetFragment : AccountFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header("Quên mật khẩu")
        title("Đặt lại mật khẩu")
        paragraph("Nhập email đăng ký. Sau khi nhận thư, sao chép mã và nhập mật khẩu mới ở bên dưới.")
        val email = field(body, "Email đăng ký", type = EMAIL, limit = 254)
        val sendError = lineError()
        body.add(action("Gửi mã qua email", orange = true) { button ->
            sendError.showError("")
            hideKeyboard()
            work(button, error = { sendError.showError(it) }) { success(api.forgot(Forgot(email.value())).message) }
        }, 8)
        val token = field(body, "Mã trong email", lines = 2, limit = 2048)
        val password = field(body, "Mật khẩu mới", type = PASSWORD)
        val confirm = field(body, "Nhập lại mật khẩu mới", type = PASSWORD)
        val resetError = lineError()
        body.add(action("Đặt lại mật khẩu") { button ->
            resetError.showError("")
            hideKeyboard()
            work(button, error = { resetError.showError(it) }) {
                success(api.reset(Reset(token.value(), password.value(), confirm.value())).message)
                findNavController().previousBackStackEntry?.savedStateHandle?.set("resetDone", true)
                findNavController().navigateUp()
            }
        }, 8)
    }
}

class ProfileFragment : PageFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header(getString(R.string.app_name), back = false, brand = true)
        loadOrRetry {
            body.removeAllViews()
            title("Cá nhân", size = 27)
            val identity = column().apply {
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = box(R.color.paper, 18)
            }
            val avatar = ImageView(requireContext()).apply {
                background = box(R.color.moss, 42)
                clipToOutline = true
                contentDescription = "Ảnh đại diện"
            }
            val profile = api.profile()
            loadPhoto(avatar, profile.avatarUrl)
            identity.addView(avatar, LinearLayout.LayoutParams(dp(80), dp(80)).apply { gravity = Gravity.CENTER })
            profile.bio?.let { text(it).also { bio -> bio.gravity = Gravity.CENTER; identity.add(bio, 8) } }
            val name = row()
            name.weighted(text(profile.fullName, 22, true, R.color.forest))
            name.addView(icon(R.drawable.ic_edit, "Chỉnh sửa hồ sơ") { go(R.id.edit_profile) })
            identity.add(name, 8)
            empty(profile.email, identity)
            val stats = row()
            listOf("${profile.favorites}\nYêu thích", "${profile.cooked}\nĐã nấu", "${profile.posts}\nBài viết").forEach { count ->
                stats.weighted(text(count, 15, true).apply { gravity = Gravity.CENTER }, 3)
            }
            identity.add(stats, 18)
            body.add(identity, 10)
            body.add(link("Đổi mật khẩu") { go(R.id.password) }, 16)
            body.add(link("Đăng xuất") {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Đăng xuất?")
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Đăng xuất") { _, _ -> app.logOut() }.show()
            }, 6)
        }
    }
}

class EditProfileFragment : PageFragment() {
    private var photo: Uri? = null
    private var preview: ImageView? = null
    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { selected ->
        if (selected != null) {
            photo = selected
            preview?.let { Glide.with(it).load(selected).centerCrop().into(it) }
        }
    }

    override fun onDestroyView() {
        preview = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header("Chỉnh sửa thông tin cá nhân")
        loadOrRetry {
            val profile = api.profile()
            body.removeAllViews()
            preview = ImageView(requireContext()).apply {
                setImageResource(R.drawable.ic_person)
                contentDescription = "Ảnh đại diện"
                background = box(R.color.moss, 50)
                clipToOutline = true
            }.also { image ->
                body.addView(image, LinearLayout.LayoutParams(dp(100), dp(100)).apply { gravity = Gravity.CENTER })
                val picked = photo
                if (picked == null) loadPhoto(image, profile.avatarUrl)
                else Glide.with(image).load(picked).centerCrop().into(image)
            }
            body.add(link("Chọn ảnh đại diện") {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, 9)
            val name = field(body, "Họ và tên", profile.fullName, limit = 120)
            val email = field(body, "Email", profile.email, EMAIL, limit = 254)
            val phone = field(body, "Số điện thoại", profile.phone.orEmpty(), InputType.TYPE_CLASS_PHONE)
            val bio = field(body, "Tiểu sử", profile.bio.orEmpty(), lines = 3, limit = 160)
            (bio.parent.parent as? com.google.android.material.textfield.TextInputLayout)?.apply {
                isCounterEnabled = true
                counterMaxLength = 160
            }
            val error = lineError()
            body.add(action("Lưu thay đổi") { button ->
                hideKeyboard()
                error.showError("")
                work(button, error = { error.showError(it) }) {
                    val selected = photo
                    val avatarUrl = if (selected == null) profile.avatarUrl.orEmpty()
                        else api.upload(photoPart(requireContext().contentResolver, selected)).url
                    api.updateProfile(ProfileUpdate(name.value(), email.value(), phone.value(), bio.value(), avatarUrl))
                    findNavController().navigateUp()
                }
            }, 12)
        }
    }
}

class PasswordFragment : PageFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header("Đổi mật khẩu")
        paragraph("Dùng ít nhất 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.")
        val current = field(body, "Mật khẩu hiện tại", type = PASSWORD)
        val password = field(body, "Mật khẩu mới", type = PASSWORD)
        val confirm = field(body, "Nhập lại mật khẩu mới", type = PASSWORD)
        val error = lineError()
        body.add(action("Xác nhận") { button ->
            hideKeyboard()
            error.showError("")
            work(button, error = { error.showError(it) }) {
                repo.token = api.changePassword(ChangePassword(current.value(), password.value(), confirm.value())).token
                findNavController().navigateUp()
            }
        }, 12)
    }
}
