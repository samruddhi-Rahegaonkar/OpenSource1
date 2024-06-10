/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2020 Chris Narkiewicz <hello@ezaquarii.com>
 * SPDX-FileCopyrightText: 2020 Nextcloud GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.nextcloud.client.mixins

import android.accounts.Account
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.nextcloud.client.account.User
import com.nextcloud.client.account.UserAccountManager
import com.owncloud.android.lib.resources.status.OCCapability
import com.owncloud.android.utils.theme.CapabilityUtils
import java.util.Optional

/**
 * Session mixin collects all account / user handling logic currently
 * spread over various activities.
 *
 * It is an intermediary step facilitating comprehensive rework of
 * account handling logic.
 */
class SessionMixin(
    private val activity: Activity,
    private val accountManager: UserAccountManager
) : ActivityMixin {
    var currentAccount: Account? = null
        private set

    val capabilities: OCCapability?
        get() = getUser()
            .map { CapabilityUtils.getCapability(it, activity) }
            .orElse(null)

    fun setAccount(account: Account?) {
        val validAccount = (account != null && accountManager.setCurrentOwnCloudAccount(account.name))

        currentAccount = if (validAccount) {
            account
        } else {
            getDefaultAccount()
        }
    }

    fun setUser(user: User) {
        setAccount(user.toPlatformAccount())
    }

    fun getUser(): Optional<User> = when (val it = this.currentAccount) {
        null -> Optional.empty()
        else -> accountManager.getUser(it.name)
    }

    /**
     * Tries to swap the current ownCloud [Account] for other valid and existing.
     *
     * If no valid ownCloud [Account] exists, then the user is requested
     * to create a new ownCloud [Account].
     */
    private fun getDefaultAccount(): Account? {
        // default to the most recently used account
        val newAccount = accountManager.currentAccount
        if (newAccount == null) {
            // no account available: force account creation
            startAccountCreation()
        }

        return newAccount
    }

    /**
     * Launches the account creation activity.
     */
    fun startAccountCreation() {
        accountManager.startAccountCreation(activity)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val current = accountManager.currentAccount
        val currentAccount = this.currentAccount
        if (current != null && currentAccount != null && !currentAccount.name.equals(current.name)) {
            this.currentAccount = current
        }
    }

    /**
     *  Since ownCloud {@link Account} can be managed from the system setting menu, the existence of the {@link
     *  Account} associated to the instance must be checked every time it is restarted.
     */
    override fun onRestart() {
        super.onRestart()
        val validAccount = currentAccount != null && accountManager.exists(currentAccount)
        if (!validAccount) {
            getDefaultAccount()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val account = accountManager.currentAccount
        setAccount(account)
    }

    override fun onResume() {
        super.onResume()
        if (currentAccount == null) {
            getDefaultAccount()
        }
    }
}
