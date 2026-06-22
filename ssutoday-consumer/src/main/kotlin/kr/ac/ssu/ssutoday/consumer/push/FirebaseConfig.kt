package kr.ac.ssu.ssutoday.consumer.push

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream

@Configuration
class FirebaseConfig {
    @Bean
    fun firebaseApp(@Value("\${ssutoday.firebase.credentials}") credentialsPath: String): FirebaseApp {
        FirebaseApp.getApps().firstOrNull()?.let { return it }
        FileInputStream(credentialsPath).use {
            return FirebaseApp.initializeApp(
                FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(it)).build(),
            )
        }
    }

    @Bean
    fun firebaseMessaging(app: FirebaseApp): FirebaseMessaging = FirebaseMessaging.getInstance(app)
}
