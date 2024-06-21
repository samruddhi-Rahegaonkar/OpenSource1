/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2020-2023 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-FileCopyrightText: 2019 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2015 ownCloud Inc.
 * SPDX-FileCopyrightText: 2016 David A. Velasco <dvelasco@solidgear.es>
 * SPDX-FileCopyrightText: 2014 María Asensio Valverde <masensio@solidgear.es>
 * SPDX-License-Identifier: GPL-2.0-only AND (AGPL-3.0-or-later OR GPL-2.0-only)
 */
package com.owncloud.android.operations;

import com.nextcloud.common.NextcloudClient;
import com.owncloud.android.datamodel.FileDataStorageManager;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.FileUtils;
import com.owncloud.android.lib.resources.shares.CreateShareRemoteOperation;
import com.owncloud.android.lib.resources.shares.OCShare;
import com.owncloud.android.lib.resources.shares.ShareType;
import com.owncloud.android.operations.common.SyncOperation;

import java.util.List;

/**
 * Creates a new public share for a given file
 */
public class CreateShareViaLinkOperation extends SyncOperation {

    private String path;
    private String password;
    private int permissions = OCShare.NO_PERMISSION;

    public CreateShareViaLinkOperation(String path, String password, FileDataStorageManager storageManager) {
        super(storageManager);

        this.path = path;
        this.password = password;
    }

    public CreateShareViaLinkOperation(String path, FileDataStorageManager storageManager, int permissions) {
        this(path, null, storageManager);
        this.permissions = permissions;
    }

    @Override
    public RemoteOperationResult<List<OCShare>> run(NextcloudClient client) {
        CreateShareRemoteOperation createOp = new CreateShareRemoteOperation(path,
                                                                             ShareType.PUBLIC_LINK,
                                                                             "",
                                                                             false,
                                                                             password,
                                                                             permissions);
        createOp.setGetShareDetails(true);
        RemoteOperationResult<List<OCShare>> result = createOp.execute(client);

        if (result.isSuccess()) {
            if (result.getResultData().isEmpty()) {
                result = new RemoteOperationResult<>(RemoteOperationResult.ResultCode.SHARE_NOT_FOUND);
            } else {
                OCShare item = result.getResultData().get(0);
                if (item != null) {
                    updateData(item);
                } else {
                    List<OCShare> data = result.getResultData();
                    result = new RemoteOperationResult<>(RemoteOperationResult.ResultCode.SHARE_NOT_FOUND);
                    result.setResultData(data);
                }
            }
        }

        return result;
    }

    private void updateData(OCShare share) {
        // Update DB with the response
        share.setPath(path);
        if (path.endsWith(FileUtils.PATH_SEPARATOR)) {
            share.setFolder(true);
        } else {
            share.setFolder(false);
        }

        getStorageManager().saveShare(share);

        // Update OCFile with data from share: ShareByLink  and publicLink
        OCFile file = getStorageManager().getFileByEncryptedRemotePath(path);
        if (file != null) {
            file.setSharedViaLink(true);
            getStorageManager().saveFile(file);
        }
    }

    public String getPath() {
        return this.path;
    }

    public String getPassword() {
        return this.password;
    }
}
