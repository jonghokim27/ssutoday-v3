package kr.ac.ssu.ssutoday.adapter.storage

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import kr.ac.ssu.ssutoday.core.port.FileStoragePort
import org.springframework.stereotype.Component
import java.io.InputStream

@Component
class CloudflareR2StorageAdapter(
    private val cloudflareR2Bucket: AmazonS3,
) : FileStoragePort {
    override fun upload(
        bucket: String,
        key: String,
        contentType: String?,
        size: Long,
        input: InputStream,
    ): String {
        val metadata =
            ObjectMetadata().apply {
                this.contentType = contentType
                contentLength = size
            }
        cloudflareR2Bucket.putObject(bucket, key, input, metadata)
        return cloudflareR2Bucket.getUrl(bucket, key).toString()
    }
}
