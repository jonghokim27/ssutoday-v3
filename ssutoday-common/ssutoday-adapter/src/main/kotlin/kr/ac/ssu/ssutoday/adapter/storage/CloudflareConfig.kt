package kr.ac.ssu.ssutoday.adapter.storage

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CloudflareConfig {
    @Bean
    fun cloudflareR2Bucket(
        @Value("\${cloud.aws.s3.endpoint}") endpoint: String,
        @Value("\${cloud.aws.s3.region}") region: String,
        @Value("\${cloud.aws.credentials.access-key}") accessKey: String,
        @Value("\${cloud.aws.credentials.secret-key}") secretKey: String,
    ): AmazonS3 = AmazonS3ClientBuilder.standard()
        .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(endpoint, region))
        .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
        .build()
}
