/*
 * Copyright (C) 2022 FlamingoOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flamingo.updater.data.update

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.FileUtils
import android.system.OsConstants
import android.util.Log

import com.flamingo.updater.data.util.checkRWX
import com.flamingo.updater.data.util.setPermissions

import java.io.File

class OTAFileManager(private val context: Context) {

    private val otaPackageDir = File(Environment.getDataDirectory(), OTA_DIR)

    val otaFile = File(otaPackageDir, UPDATE_FILE)
    val otaFileUri: Uri = Uri.fromFile(otaFile)

    init {
        if (!otaPackageDir.isDirectory) {
            throw RuntimeException("OTA package dir ${otaPackageDir.absolutePath} does not exist")
        }
        if (!checkRWX(otaPackageDir)) {
            throw RuntimeException("No rwx permission for ${otaPackageDir.absolutePath}")
        }
    }

    /**
     * Copy contents to [otaPackageDir]. Should not be
     * called from main thread.
     *
     * @param uri the [Uri] of the file.
     * @return true if copying was successful, false if not.
     */
    fun copyToOTAPackageDir(uri: Uri): Result<Unit> {
        if (!wipe()) {
            return Result.failure(Throwable("Failed to wipe working directory"))
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { inStream ->
                otaFile.outputStream().use { outStream ->
                    FileUtils.copy(inStream, outStream)
                    val errno: Int = setPermissions(
                        otaFile,
                        OsConstants.S_IRWXU or OsConstants.S_IRWXG,
                    )
                    if (errno != 0) {
                        Log.e(TAG, "setPermissions failed with errno $errno")
                        throw Throwable("Setting permissions failed")
                    }
                }
            } ?: throw Throwable("Failed to open input stream from uri")
        }
    }

    /**
     * Deletes all files inside the ota package directory.
     *
     * @return true if all files were deleted, false if failed for some or all.
     */
    fun wipe(): Boolean {
        var success = true
        otaPackageDir.listFiles()?.forEach {
            if (!it.delete()) {
                Log.e(TAG, "Deleting ${it.absolutePath} failed")
                success = false
            }
        }
        return success
    }

    companion object {
        private const val TAG = "OTAFileManager"
        private const val OTA_DIR = "flamingo_ota"
        private const val UPDATE_FILE = "update.zip"
    }
}