package kr.ac.ssu.ssutoday.core.port

import java.io.InputStream

interface FileStoragePort {
    fun upload(bucket: String, key: String, contentType: String?, size: Long, input: InputStream): String
}
