package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.util.AttributeSet
import android.widget.Checkable
import androidx.appcompat.widget.AppCompatImageButton
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.simplecityapps.shuttle.R
import timber.log.Timber

class FavoriteButton
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    private var rating: Int = 0

    init {
        Timber.i("initing button with rating = $rating")
        this.setImage()
    }

    fun rating(): Int {
        return rating
    }

    fun setRating(newRating: Int) {
        Timber.i("setRating to ${newRating}")
        rating = newRating
        this.setImage()
    }

    fun clicked() {
        Timber.w("Clicking. now ${rating}")

        rating += 1
        if(rating == 6) {
            rating = 0
        }

        setImage()
    }

    private fun setImage() {
        Timber.i("Setting image...")
        setImageDrawable(when(rating) {
            1 -> AnimatedVectorDrawableCompat.create(context, R.drawable.heart1)!!
            2 -> AnimatedVectorDrawableCompat.create(context, R.drawable.heart2)!!
            3 -> AnimatedVectorDrawableCompat.create(context, R.drawable.heart3)!!
            4 -> AnimatedVectorDrawableCompat.create(context, R.drawable.heart4)!!
            5 -> AnimatedVectorDrawableCompat.create(context, R.drawable.heart5)!!
            else -> AnimatedVectorDrawableCompat.create(context, R.drawable.avd_heart)!!
        })
    }
}
