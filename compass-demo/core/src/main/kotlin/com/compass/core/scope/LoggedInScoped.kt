package com.compass.core.scope

import javax.inject.Scope

/**
 * Hilt scope tied to a logged-in session. Instances live until logout drops
 * the LoggedInComponent.
 */
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class LoggedInScoped
