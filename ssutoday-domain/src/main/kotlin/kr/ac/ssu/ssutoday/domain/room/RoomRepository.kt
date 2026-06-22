package kr.ac.ssu.ssutoday.domain.room

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RoomRepository : JpaRepository<Room, String> {
    @Query("select r from Room r where r.no = :roomNo and r.major like concat('%\"', :major, '\"%')")
    fun findAccessible(roomNo: String, major: String): Room?

    @Query("select r from Room r where r.major like concat('%\"', :major, '\"%')")
    fun findAllAccessible(major: String): List<Room>
}
