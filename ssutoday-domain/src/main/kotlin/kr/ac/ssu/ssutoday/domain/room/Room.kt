package kr.ac.ssu.ssutoday.domain.room

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
class Room(
    @Id
    @Column(length = 10)
    var no: String,

    @Column(nullable = false, length = 50)
    var name: String,

    @Column(nullable = false, columnDefinition = "json")
    var major: String,

    @Column(nullable = false)
    var capacity: Int,

    @Column(nullable = false, length = 50)
    var location: String,

    @Column(nullable = false, length = 50)
    var tags: String,

    @Column(nullable = false, length = 200)
    var image: String,

    @Column(nullable = false, length = 200)
    var bigImage: String,

    @Column(nullable = false)
    var isAvailable: Int,
)
