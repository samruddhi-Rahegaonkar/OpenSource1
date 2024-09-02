/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Alper Ozturk <alper.ozturk@nextcloud.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.database.typeAdapter

import com.google.gson.*
import com.nextcloud.model.OfflineOperationRawType
import com.nextcloud.model.OfflineOperationType

import java.lang.reflect.Type

class OfflineOperationTypeAdapter : JsonSerializer<OfflineOperationType>, JsonDeserializer<OfflineOperationType> {

    override fun serialize(
        src: OfflineOperationType?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        val jsonObject = JsonObject()
        jsonObject.addProperty("type", src?.javaClass?.simpleName)
        when (src) {
            is OfflineOperationType.CreateFolder -> {
                jsonObject.addProperty("type", src.type)
                jsonObject.addProperty("path", src.path)
            }

            is OfflineOperationType.CreateFile -> {
                jsonObject.addProperty("type", src.type)
                jsonObject.addProperty("localPath", src.localPath)
                jsonObject.addProperty("remotePath", src.remotePath)
                jsonObject.addProperty("mimeType", src.mimeType)
            }

            null -> Unit
        }
        return jsonObject
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): OfflineOperationType? {
        val jsonObject = json?.asJsonObject ?: return null
        val type = jsonObject.get("type")?.asString
        return when (type) {
            OfflineOperationRawType.CreateFolder.name -> OfflineOperationType.CreateFolder(
                jsonObject.get("type").asString,
                jsonObject.get("path").asString
            )

            OfflineOperationRawType.CreateFile.name -> OfflineOperationType.CreateFile(
                jsonObject.get("type").asString,
                jsonObject.get("localPath").asString,
                jsonObject.get("remotePath").asString,
                jsonObject.get("mimeType").asString,
            )

            else -> null
        }
    }
}
