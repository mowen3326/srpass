package app.srpass.kykfz01.ui.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 相册读取权限的系统差异封装。
 *
 * API 33+ 用细粒度的 READ_MEDIA_IMAGES（API 34 增加部分授权），而 API 30~32
 * 上该权限不存在，必须走 READ_EXTERNAL_STORAGE。两套权限名由这里统一给出，
 * 调用方不需要关心系统版本。
 */
fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

/** 已获得任意一种可用的相册读取授权（完整或部分）。 */
fun hasMediaAccess(context: Context): Boolean =
    mediaPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
