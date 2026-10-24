package kr.ac.ssu.ssutoday.application

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers

/** Mockito의 any 매처는 null을 돌려주므로 Kotlin의 non-null 인자에 넘길 때 이 함수를 쓴다. */
@Suppress("UNCHECKED_CAST")
fun <T> anyOf(type: Class<T>): T = ArgumentMatchers.any(type) ?: (null as T)

/** capture()도 null을 돌려주므로 Kotlin의 non-null 인자에 넘길 때 이 함수를 쓴다. */
@Suppress("UNCHECKED_CAST")
fun <T> ArgumentCaptor<T>.captured(): T = capture() ?: (null as T)
