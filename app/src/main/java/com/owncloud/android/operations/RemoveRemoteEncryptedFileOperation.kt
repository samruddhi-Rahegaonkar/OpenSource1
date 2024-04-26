/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2017 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-FileCopyrightText: 2017 Nextcloud GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.owncloud.android.operations

import android.content.Context
import com.nextcloud.client.account.User
import com.owncloud.android.datamodel.ArbitraryDataProvider
import com.owncloud.android.datamodel.ArbitraryDataProviderImpl
import com.owncloud.android.datamodel.FileDataStorageManager
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.datamodel.e2e.v1.decrypted.DecryptedFolderMetadataFileV1
import com.owncloud.android.datamodel.e2e.v1.decrypted.DecryptedMetadata
import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.operations.RemoteOperation
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.common.utils.Log_OC
import com.owncloud.android.lib.resources.status.E2EVersion
import com.owncloud.android.utils.EncryptionUtils
import com.owncloud.android.utils.EncryptionUtilsV2
import com.owncloud.android.utils.theme.CapabilityUtils
import org.apache.commons.httpclient.HttpStatus
import org.apache.commons.httpclient.NameValuePair
import org.apache.jackrabbit.webdav.client.methods.DeleteMethod

/**
 * Remote operation performing the removal of a remote encrypted file or folder
 */

/**
 * Constructor
 *
 * @param remotePath   RemotePath of the remote file or folder to remove from the server
 * @param parentFolder parent folder
 */

class RemoveRemoteEncryptedFileOperation internal constructor(
    private val remotePath: String,
    private val user: User,
    private val context: Context,
    private val fileName: String,
    private val parentFolder: OCFile,
    private val isFolder: Boolean
) : RemoteOperation<Void>() {

    /**
     * Performs the remove operation.
     */
    override fun run(client: OwnCloudClient): RemoteOperationResult<Void> {
        val result: RemoteOperationResult<Void>
        var delete: DeleteMethod? = null
        var token: String? = null
        val e2eVersion = CapabilityUtils.getCapability(context).endToEndEncryptionApiVersion
        val isE2EVersionAtLeast2 = e2eVersion >= E2EVersion.V2_0

        try {
            token = EncryptionUtils.lockFolder(parentFolder, client)

            return if (isE2EVersionAtLeast2) {
                val (first, second) = deleteForV2(client, token)
                result = first
                delete = second
                result
            } else {
                deleteForV1(client, token)
            }
        } catch (e: Exception) {
            result = RemoteOperationResult(e)
            Log_OC.e(TAG, "Remove " + remotePath + ": " + result.logMessage, e)
        } finally {
            delete?.releaseConnection()
            token?.let { unlockFile(client, it, isE2EVersionAtLeast2) }
        }

        return result
    }

    private fun unlockFile(client: OwnCloudClient, token: String, isE2EVersionAtLeast2: Boolean) {
        val unlockFileOperationResult = if (isE2EVersionAtLeast2) {
            EncryptionUtils.unlockFolder(parentFolder, client, token)
        } else {
            EncryptionUtils.unlockFolderV1(parentFolder, client, token)
        }

        if (!unlockFileOperationResult.isSuccess) {
            Log_OC.e(TAG, "Failed to unlock " + parentFolder.localId)
        }
    }

    private fun deleteRemoteFile(
        client: OwnCloudClient,
        token: String?
    ): Pair<RemoteOperationResult<Void>, DeleteMethod> {
        val delete = DeleteMethod(client.getFilesDavUri(remotePath)).apply {
            setQueryString(arrayOf(NameValuePair(E2E_TOKEN, token)))
        }

        val status = client.executeMethod(delete, REMOVE_READ_TIMEOUT, REMOVE_CONNECTION_TIMEOUT)
        delete.getResponseBodyAsString() // exhaust the response, although not interesting

        val result = RemoteOperationResult<Void>(delete.succeeded() || status == HttpStatus.SC_NOT_FOUND, delete)
        Log_OC.i(TAG, "Remove " + remotePath + ": " + result.logMessage)

        return Pair(result, delete)
    }

    private fun getMetadataV1(arbitraryDataProvider: ArbitraryDataProvider): DecryptedFolderMetadataFileV1 {
        val publicKey = arbitraryDataProvider.getValue(user.accountName, EncryptionUtils.PUBLIC_KEY)

        val metadata = DecryptedFolderMetadataFileV1().apply {
            metadata = DecryptedMetadata()
            metadata.version = 1.2
            metadata.metadataKeys = HashMap()
        }

        val metadataKey = EncryptionUtils.encodeBytesToBase64String(EncryptionUtils.generateKey())
        val encryptedMetadataKey = EncryptionUtils.encryptStringAsymmetric(metadataKey, publicKey)
        metadata.metadata.metadataKey = encryptedMetadataKey

        return metadata
    }

    private fun deleteForV2(client: OwnCloudClient, token: String?): Pair<RemoteOperationResult<Void>, DeleteMethod> {
        val encryptionUtilsV2 = EncryptionUtilsV2()

        val (metadataExists, metadata) = encryptionUtilsV2.retrieveMetadata(
            parentFolder,
            client,
            user,
            context
        )

        val (result, delete) = deleteRemoteFile(client, token)

        if (isFolder) {
            encryptionUtilsV2.removeFolderFromMetadata(fileName, metadata)
        } else {
            encryptionUtilsV2.removeFileFromMetadata(fileName, metadata)
        }

        encryptionUtilsV2.serializeAndUploadMetadata(
            parentFolder,
            metadata,
            token!!,
            client,
            metadataExists,
            context,
            user,
            FileDataStorageManager(user, context.contentResolver)
        )

        return Pair(result, delete)
    }

    private fun deleteForV1(client: OwnCloudClient, token: String?): RemoteOperationResult<Void> {
        val arbitraryDataProvider: ArbitraryDataProvider = ArbitraryDataProviderImpl(context)
        val metadata = getMetadataV1(arbitraryDataProvider)
        val (first) = deleteRemoteFile(client, token)

        val serializedMetadata: String = if (metadata.metadata.getMetadataKey() != null) {
            EncryptionUtils.serializeJSON(metadata, true)
        } else {
            EncryptionUtils.serializeJSON(metadata)
        }

        EncryptionUtils.uploadMetadata(
            parentFolder,
            serializedMetadata,
            token,
            client,
            true, E2EVersion.V1_2,
            "",
            arbitraryDataProvider,
            user
        )

        return first
    }

    companion object {
        private val TAG = RemoveRemoteEncryptedFileOperation::class.java.getSimpleName()
        private const val REMOVE_READ_TIMEOUT = 30000
        private const val REMOVE_CONNECTION_TIMEOUT = 5000
    }
}
