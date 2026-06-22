package kr.ac.ssu.ssutoday.api.article

import jakarta.validation.Valid
import kr.ac.ssu.ssutoday.api.article.dto.ArticleIdRequest
import kr.ac.ssu.ssutoday.api.article.dto.ArticleListRequest
import kr.ac.ssu.ssutoday.api.article.dto.ArticleListResponse
import kr.ac.ssu.ssutoday.api.article.dto.ArticleResponse
import kr.ac.ssu.ssutoday.api.common.SsuResponse
import kr.ac.ssu.ssutoday.api.config.AuthenticatedStudent
import kr.ac.ssu.ssutoday.application.article.ArticleApplicationService
import kr.ac.ssu.ssutoday.application.article.dto.ArticleQuery
import kr.ac.ssu.ssutoday.core.status.SsuStatus
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
    @SsuResponse(SsuStatus.SSU2060)
    fun list(
        @AuthenticatedStudent student: StudentView,
        @Valid @RequestBody request: ArticleListRequest,
    ): ArticleListResponse {
        val result = articleApplicationService.list(
            ArticleQuery(
                request.page,
                request.orderBy == "ASC",
                request.search,
                request.provider.map { provider -> if (provider == "major") student.major else provider },
            ),
        )
        return ArticleListResponse(result.articles, result.totalPages)
    }

    @PostMapping("/get")
    @SsuResponse(SsuStatus.SSU2080)
    fun get(@Valid @RequestBody request: ArticleIdRequest) =
        ArticleResponse(articleApplicationService.get(request.idx))
}
