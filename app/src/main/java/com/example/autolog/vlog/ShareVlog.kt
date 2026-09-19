package com.example.autolog.vlog

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 완성된 브이로그를 다른 앱으로 넘긴다 (planning 3-6).
 *
 * 넘기는 것은 파일이 아니라 MediaStore의 content URI다 — 받는 앱이 우리 저장소를 뒤지지 않고
 * 그 주소로만 읽게 하고, 읽기 권한도 이 인텐트가 사는 동안에만 준다.
 */
fun shareVlogIntent(uri: Uri): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // 어디로 보낼지는 사용자가 고른다 — 인스타그램만 아는 앱이 아니다 (planning 3-6).
    return Intent.createChooser(send, null)
}

fun Context.shareVlog(uri: Uri) = startActivity(shareVlogIntent(uri))

private const val MIME_TYPE = "video/mp4"
