package org.secourse.hzau.campus.web;

import jakarta.validation.Valid;
import org.secourse.hzau.campus.common.ApiResponse;
import org.secourse.hzau.campus.dto.InteractionDtos;
import org.secourse.hzau.campus.service.GovernanceService;
import org.secourse.hzau.campus.service.InteractionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 互动与协同验证接口（F4 / F5）：评论、点赞、证实/证伪、举报、认领。
 */
@RestController
@RequestMapping("/api")
public class InteractionController {

    private final InteractionService interactionService;
    private final GovernanceService governanceService;

    public InteractionController(InteractionService interactionService, GovernanceService governanceService) {
        this.interactionService = interactionService;
        this.governanceService = governanceService;
    }

    // ------------------------------------------------------------- 评论

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<List<InteractionDtos.CommentView>> comments(@PathVariable Long postId) {
        return ApiResponse.ok(interactionService.comments(postId));
    }

    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<InteractionDtos.CommentView> comment(@PathVariable Long postId,
                                                            @Valid @RequestBody InteractionDtos.CommentRequest request) {
        return ApiResponse.ok(interactionService.addComment(postId, request.content()));
    }

    // ------------------------------------------------------------- 点赞

    @PostMapping("/posts/{postId}/like")
    public ApiResponse<InteractionDtos.InteractionResult> like(@PathVariable Long postId) {
        return ApiResponse.ok(interactionService.like(postId));
    }

    @DeleteMapping("/posts/{postId}/like")
    public ApiResponse<InteractionDtos.InteractionResult> unlike(@PathVariable Long postId) {
        return ApiResponse.ok(interactionService.unlike(postId));
    }

    // ------------------------------------------------------------- 协同验证

    @GetMapping("/posts/{postId}/verifications")
    public ApiResponse<InteractionDtos.VerificationSummary> verificationSummary(@PathVariable Long postId) {
        return ApiResponse.ok(interactionService.verificationSummary(postId));
    }

    @PostMapping("/posts/{postId}/verify")
    public ApiResponse<InteractionDtos.VerificationSummary> verify(@PathVariable Long postId,
                                                                    @Valid @RequestBody InteractionDtos.VerifyRequest request) {
        return ApiResponse.ok(interactionService.verify(postId, request));
    }

    // ------------------------------------------------------------- 举报

    @PostMapping("/posts/{postId}/reports")
    public ApiResponse<Void> report(@PathVariable Long postId,
                                    @Valid @RequestBody InteractionDtos.ReportRequest request) {
        governanceService.report(postId, request);
        return ApiResponse.ok();
    }

    // ------------------------------------------------------------- 失物招领认领

    @PostMapping("/posts/{postId}/claims")
    public ApiResponse<InteractionDtos.ClaimView> claim(@PathVariable Long postId,
                                                        @Valid @RequestBody InteractionDtos.ClaimRequest request) {
        return ApiResponse.ok(interactionService.claim(postId, request));
    }

    @GetMapping("/posts/{postId}/claims")
    public ApiResponse<List<InteractionDtos.ClaimView>> claims(@PathVariable Long postId) {
        return ApiResponse.ok(interactionService.claims(postId));
    }

    @PostMapping("/claims/{claimId}/handle")
    public ApiResponse<InteractionDtos.ClaimView> handleClaim(@PathVariable Long claimId,
                                                              @Valid @RequestBody InteractionDtos.ClaimHandleRequest request) {
        return ApiResponse.ok(interactionService.handleClaim(claimId, request));
    }
}
