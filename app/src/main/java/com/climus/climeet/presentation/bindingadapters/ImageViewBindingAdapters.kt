package com.climus.climeet.presentation.bindingadapters

import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.databinding.BindingAdapter
import androidx.lifecycle.LiveData
import com.bumptech.glide.Glide
import com.climus.climeet.R

@BindingAdapter("profileImgUrl")
fun bindProfileImg(
    imageView: ImageView,
    url: String?,
) {
    imageView.setClimberProfileImage(url)
}

@BindingAdapter("gymProfileImgUrl")
fun bindGymProfileImg(
    imageView: ImageView,
    url: String?,
) {
    imageView.setGymProfileImage(url)
}

fun ImageView.setClimberProfileImage(url: String?) {
    setProfileImage(url, R.drawable.img_profile)
}

fun ImageView.setGymProfileImage(url: String?) {
    setProfileImage(url, R.drawable.img_crag)
}

private fun ImageView.setProfileImage(
    url: String?,
    @DrawableRes defaultImage: Int,
) {
    Glide.with(context)
        .load(url?.takeIf(String::isNotBlank))
        .placeholder(defaultImage)
        .fallback(defaultImage)
        .error(defaultImage)
        .into(this)
}

@BindingAdapter("imgUrl")
fun bindImgUrl(
    imageView: ImageView,
    url: String?,
) {
    url?.let {
        Glide.with(imageView.context)
            .load(it)
            .into(imageView)
    }
}

@BindingAdapter("formCheck")
fun bindFormCheck(
    iv: ImageView,
    data: String,
) {
    if (data.isBlank()) {
        iv.setImageResource(R.drawable.ic_check_off)
    } else {
        iv.setImageResource(R.drawable.ic_check_on)
    }
}

@BindingAdapter("completeState")
fun bindCompleteStaet(
    iv: ImageView,
    state: Boolean,
) {
    if (state) {
        iv.setImageResource(R.drawable.ic_check_main)
    } else {
        iv.setImageResource(R.drawable.ic_check_grey)
    }
}

@BindingAdapter("animatedAlpha")
fun ImageView.setAnimatedParams(alpha: LiveData<Float>) {
    this.alpha = alpha.value ?: 1f
}

@BindingAdapter("isVisible")
fun bindIsVisible(
    view: View,
    isVisible: Boolean,
) {
    view.visibility = if (isVisible) View.VISIBLE else View.GONE
}

@BindingAdapter("backgroundImgUri")
fun bindBackgroundImg(
    imageView: ImageView,
    imageUrl: String?,
) {
    if (!imageUrl.isNullOrEmpty()) {
        Glide.with(imageView.context)
            .load(imageUrl)
            .error(R.drawable.img_gym_background)
            .into(imageView)
    }
}
