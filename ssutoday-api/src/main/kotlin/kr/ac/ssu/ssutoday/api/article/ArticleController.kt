package kr.ac.ssu.ssutoday.api.article

import jakarta.validation.Valid
import kr.ac.ssu.ssutoday.api.article.dto.ArticleIdRequest
import kr.ac.ssu.ssutoday.api.article.dto.ArticleListRequest
import kr.ac.ssu.ssutoday.api.article.dto.ArticleListResponse
import kr.ac.ssu.ssutoday.api.article.dto.ArticleResponse
import kr.ac.ssu.ssutoday.api.common.ResponseStatus
import kr.ac.ssu.ssutoday.api.config.LoginStudent
import kr.ac.ssu.ssutoday.application.article.ArticleApplicationService
import kr.ac.ssu.ssutoday.application.article.dto.ArticleQuery
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/article")
class ArticleController(
    private val articleApplicationService: ArticleApplicationService,
) {
    @PostMapping("/list")
    @ResponseStatus(StatusCode.SSU2060)
    fun listArticles(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: ArticleListRequest,
    ): ArticleListResponse {
        val result =
            articleApplicationService.listArticles(
                ArticleQuery(
                    student.id,
                    request.page,
                    request.orderBy == "ASC",
                    request.search,
                    request.provider.map { provider -> if (provider == "major") student.major else provider },
                    request.starredOnly,
                ),
            )
        return ArticleListResponse(result.articles, result.totalPages)
    }

    @PostMapping("/get")
    @ResponseStatus(StatusCode.SSU2080)
    fun getArticle(
        @Valid @RequestBody request: ArticleIdRequest,
    ) = ArticleResponse(articleApplicationService.getArticle(request.idx))

    @PostMapping("/star")
    @ResponseStatus(StatusCode.SSU2000)
    fun starArticle(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: ArticleIdRequest,
    ) {
        articleApplicationService.starArticle(student.id, request.idx)
    }

    @PostMapping("/unstar")
    @ResponseStatus(StatusCode.SSU2000)
    fun unstarArticle(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: ArticleIdRequest,
    ) {
        articleApplicationService.unstarArticle(student.id, request.idx)
    }
}
