package com.artivatic.classifier_offline

import android.content.res.Resources


fun dpToPx(dp: Int) = (dp * Resources.getSystem().displayMetrics.density).toInt()