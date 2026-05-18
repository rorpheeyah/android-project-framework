package com.bizplay.core.session

/**
 * Read-only access to the active [Session] for ViewModels that live above
 * LoggedInComponent (in the demo, every post-login screen).
 *
 * The implementation in :app pulls the Session out of LoggedInComponentManager.
 * If no session is active, [current] throws — the navigation graph should make
 * this impossible by routing pre-login screens elsewhere.
 */
interface SessionHolder {
    val current: Session
    val currentOrNull: Session?
}
