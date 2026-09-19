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

/**
 * 저장된 결과물을 갤러리 앱으로 연다 (planning 3-6 "갤러리에 저장해 개인 소장").
 *
 * 저장은 만들 때 이미 끝나 있다(#27) — 이건 "정말 갤러리에 있다"를 사용자가 직접 보게 하는 길이다.
 */
fun openInGalleryIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, MIME_TYPE)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/** 열 수 있는 앱이 없으면 아무 일도 하지 않는다 — 저장 자체는 이미 끝났으므로 실패가 아니다. */
fun Context.openInGallery(uri: Uri): Boolean =
    runCatching { startActivity(openInGalleryIntent(uri)) }.isSuccess

private const val MIME_TYPE = "video/mp4"
