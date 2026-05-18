package com.bizplay.demo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Intentionally minimal — security checks, MgGate fetch,
 * and the LoggedInComponent build all happen inside [com.bizplay.demo.boot.BootCoordinatorImpl]
 * (driven by [com.bizplay.features.boot.BootViewModel]) so that the first frame
 * appears as fast as possible.
 */
@HiltAndroidApp
class BizplayApplication : Application()
