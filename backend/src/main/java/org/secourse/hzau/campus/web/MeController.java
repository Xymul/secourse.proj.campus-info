package org.secourse.hzau.campus.web;

import org.secourse.hzau.campus.common.ApiResponse;
import org.secourse.hzau.campus.dto.InteractionDtos;
import org.secourse.hzau.campus.security.CurrentUserContext;
import org.secourse.hzau.campus.service.InteractionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心接口：我的发布、我的点赞、我的验证、我的认领。
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final InteractionService interactionService;

    public MeController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @GetMapping("/interactions")
    public ApiResponse<InteractionDtos.MyInteractions> interactions() {
        return ApiResponse.ok(interactionService.myInteractions(CurrentUserContext.requireUserId()));
    }
}
