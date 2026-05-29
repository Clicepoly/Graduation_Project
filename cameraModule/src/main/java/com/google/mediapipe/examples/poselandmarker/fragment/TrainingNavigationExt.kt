package com.google.mediapipe.examples.poselandmarker.fragment

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.mediapipe.examples.poselandmarker.MainActivity
import com.google.mediapipe.examples.poselandmarker.R

internal fun Fragment.returnFromTraining(useNavigateUp: Boolean = false) {
    val launchedFromAppFlow = activity?.intent?.hasExtra(MainActivity.EXTRA_TARGET_FRAGMENT) == true

    if (launchedFromAppFlow) {
        activity?.setResult(Activity.RESULT_OK)
        activity?.finish()
        activity?.overridePendingTransition(0, 0)
    } else if (useNavigateUp) {
        findNavController().navigateUp()
    } else {
        findNavController().navigate(R.id.home_fragment)
    }
}