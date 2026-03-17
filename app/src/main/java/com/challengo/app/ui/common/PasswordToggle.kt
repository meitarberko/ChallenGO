package com.challengo.app.ui.common

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import com.challengo.app.R
import com.google.android.material.textfield.TextInputLayout

fun TextInputLayout.attachPasswordToggle(editText: EditText) {
    setPasswordVisible(editText, false)
    endIconMode = TextInputLayout.END_ICON_CUSTOM
    setEndIconTintList(ColorStateList.valueOf(Color.WHITE))
    setEndIconDrawable(R.drawable.ic_eye)
    endIconContentDescription = "Show password"
    isEndIconVisible = true

    setEndIconOnClickListener {
        val isHidden = editText.transformationMethod is PasswordTransformationMethod
        setPasswordVisible(editText, isHidden)
    }
}

private fun TextInputLayout.setPasswordVisible(editText: EditText, visible: Boolean) {
    val textLength = editText.text?.length ?: 0
    val hadFocus = editText.hasFocus()

    editText.transformationMethod = if (visible) {
        HideReturnsTransformationMethod.getInstance()
    } else {
        PasswordTransformationMethod.getInstance()
    }

    setEndIconDrawable(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye)
    endIconContentDescription = if (visible) "Hide password" else "Show password"
    editText.setSelection(textLength)
    if (hadFocus) {
        editText.requestFocus()
    }
}
