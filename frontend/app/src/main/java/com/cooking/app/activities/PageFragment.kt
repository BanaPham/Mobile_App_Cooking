package com.cooking.app.activities

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.cooking.app.BuildConfig
import com.cooking.app.MainActivity
import com.cooking.app.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

abstract class PageFragment : Fragment(R.layout.screen_page) {
    protected lateinit var body: LinearLayout
    protected lateinit var toolbar: MaterialToolbar
    protected val app get() = requireActivity() as MainActivity
    protected val repo get() = app.repository
    protected val api get() = repo.api
    protected val itemId get() = requireArguments().getLong("id")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        body = view.findViewById(R.id.body)
        toolbar = view.findViewById(R.id.toolbar)
    }

    protected fun header(title: String, back: Boolean = true, brand: Boolean = false) {
        toolbar.title = title
        if (back) {
            toolbar.setNavigationIcon(R.drawable.ic_back)
            toolbar.setNavigationContentDescription(R.string.back)
            toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        } else if (brand) toolbar.setLogo(R.drawable.ic_pot)
    }

    protected fun go(dest: Int, vararg args: Pair<String, Any?>) {
        if (isAdded && view != null && !parentFragmentManager.isStateSaved) findNavController().navigate(dest, bundleOf(*args))
    }

    protected fun info(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG).show() }
    }

    protected fun success(message: String) {
        val root = view as? ViewGroup ?: return
        val toast = text(message, 15, true, R.color.paper).apply {
            background = box(R.color.leaf, 12)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        root.addView(toast, 0)
        toast.announceForAccessibility(message)
        toast.postDelayed({ root.removeView(toast) }, 4200)
    }

    protected fun work(control: View? = null, progress: Boolean = true, error: ((String) -> Unit)? = null,
                       block: suspend () -> Unit): Job = viewLifecycleOwner.lifecycleScope.launch {
        val token = repo.token
        val indicator = view?.findViewById<View>(R.id.loading)
        if (progress) indicator?.isVisible = true
        control?.isEnabled = false
        try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val http = exception as? HttpException
            val message = when (exception) {
                is HttpException -> runCatching {
                    JSONObject(exception.response()?.errorBody()?.string().orEmpty()).getString("message")
                }.getOrNull() ?: "Yêu cầu chưa thành công (HTTP ${exception.code()})"
                is IOException -> "Không kết nối được máy chủ. Kiểm tra mạng và địa chỉ API."
                is IllegalArgumentException -> exception.message ?: "Dữ liệu không hợp lệ"
                else -> "Có lỗi xảy ra. Vui lòng thử lại."
            }
            if (http?.code() == 401 && token != null && repo.token == token) {
                Toast.makeText(requireContext(), "Phiên đã hết hạn. Vui lòng đăng nhập lại.", Toast.LENGTH_LONG).show()
                app.logOut()
            } else if (error != null) error(message) else info(message)
        } finally {
            if (progress) indicator?.isVisible = false
            control?.isEnabled = true
        }
    }

    protected fun dp(value: Int) = (resources.displayMetrics.density * value).roundToInt()
    protected fun color(@ColorRes value: Int) = ContextCompat.getColor(requireContext(), value)
    protected fun text(value: String, size: Int = 15, bold: Boolean = false, @ColorRes tint: Int = R.color.ink) =
        TextView(requireContext()).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color(tint))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setLineSpacing(dp(2).toFloat(), 1f)
        }

    protected fun title(value: String, parent: LinearLayout = body, size: Int = 22) =
        text(value, size, true, R.color.forest).also { parent.add(it, 12) }

    protected fun paragraph(value: String, parent: LinearLayout = body) =
        text(value).also { parent.add(it, 8) }

    protected fun empty(value: String, parent: LinearLayout = body) =
        text(value, 15, false, R.color.muted).also { parent.add(it, 16) }

    protected fun column() = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
    protected fun row() = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }

    protected fun LinearLayout.add(child: View, top: Int = 0) {
        addView(child, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(top) })
    }

    protected fun LinearLayout.weighted(child: View, left: Int = 0) {
        addView(child, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = dp(left) })
    }

    protected fun action(label: String, orange: Boolean = false, onClick: (View) -> Unit): MaterialButton =
        (layoutInflater.inflate(R.layout.widget_action, body, false) as MaterialButton).apply {
            text = label
            if (orange) backgroundTintList = ColorStateList.valueOf(color(R.color.orange))
            setOnClickListener(onClick)
        }

    protected fun link(label: String, onClick: (View) -> Unit): MaterialButton =
        MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            minHeight = dp(46)
            setOnClickListener(onClick)
        }

    protected fun icon(res: Int, label: String, action: (View) -> Unit) = ImageButton(requireContext()).apply {
        setImageResource(res)
        contentDescription = label
        background = box(R.color.moss, 24)
        setPadding(dp(11), dp(11), dp(11), dp(11))
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) }
        setOnClickListener(action)
    }

    protected fun field(parent: LinearLayout, label: String, initial: String = "", type: Int = InputType.TYPE_CLASS_TEXT,
                        lines: Int = 1, limit: Int? = null): TextInputEditText {
        val wrapper = layoutInflater.inflate(R.layout.widget_field, parent, false) as TextInputLayout
        wrapper.hint = label
        val edit = wrapper.editText as TextInputEditText
        edit.inputType = if (lines > 1) type or InputType.TYPE_TEXT_FLAG_MULTI_LINE else type
        edit.minLines = lines
        edit.setText(initial)
        edit.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        if (type == PASSWORD) {
            edit.transformationMethod = PasswordTransformationMethod.getInstance()
            wrapper.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        if (limit != null) edit.filters = arrayOf(android.text.InputFilter.LengthFilter(limit))
        parent.add(wrapper, 10)
        return edit
    }

    protected fun lineError() = text("", 14, false, R.color.error).also { body.add(it, 6); it.isVisible = false }
    protected fun TextView.showError(value: String) { text = value; isVisible = value.isNotBlank() }

    protected fun image(parent: LinearLayout, url: String?, height: Int, description: String): ImageView {
        val image = ImageView(requireContext()).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = description
            background = box(R.color.moss, 16)
            clipToOutline = true
        }
        parent.addView(image, LinearLayout.LayoutParams(-1, dp(height)).apply { topMargin = dp(8) })
        loadPhoto(image, url)
        return image
    }

    protected fun box(@ColorRes fill: Int, radius: Int) = GradientDrawable().apply {
        setColor(color(fill))
        cornerRadius = dp(radius).toFloat()
    }

    protected fun hideKeyboard() {
        val current = activity?.currentFocus ?: return
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(current.windowToken, 0)
        current.clearFocus()
    }

    protected fun loadOrRetry(block: suspend () -> Unit): Job = work(error = { message ->
        val error = empty(message)
        body.add(link("Thử lại") {
            body.removeView(error)
            body.removeView(it)
            loadOrRetry(block)
        }, 8)
    }, block = block)

    companion object {
        const val PASSWORD = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        const val EMAIL = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        const val NUMBER = InputType.TYPE_CLASS_NUMBER
    }
}

internal fun TextView.value() = text.toString()
internal fun num(value: Double): String = DecimalFormat("0.##", DecimalFormatSymbols(Locale.forLanguageTag("vi-VN"))).format(value)
internal fun loadPhoto(target: ImageView, url: String?) {
    val model = when {
        url.isNullOrBlank() -> null
        url.startsWith("asset:") -> "file:///android_asset/recipes/" + url.removePrefix("asset:") + ".jpg"
        url.startsWith("/api/media/") -> BuildConfig.API_URL.trimEnd('/') + url
        else -> url
    }
    Glide.with(target).load(model).centerCrop().placeholder(R.drawable.bg_image_placeholder)
        .fallback(R.drawable.bg_image_placeholder).error(R.drawable.bg_image_placeholder).into(target)
}
