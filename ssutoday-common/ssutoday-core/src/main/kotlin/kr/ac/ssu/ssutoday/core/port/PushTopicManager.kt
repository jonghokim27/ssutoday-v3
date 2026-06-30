package kr.ac.ssu.ssutoday.core.port

interface PushTopicManager {
    fun subscribe(token: String, topics: List<String>)
    fun unsubscribe(token: String, topics: List<String>)
}
