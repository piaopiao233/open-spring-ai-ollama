package org.forest.chatollama.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.forest.chatollama.dto.PageResult;
import org.forest.chatollama.dto.Result;
import org.forest.chatollama.model.ChatSession;
import org.forest.chatollama.service.IChatSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/ChatSession")
@Validated
@Tag(name = "会话管理")
public class ChatSessionController {

    @Autowired
    private IChatSessionService chatSessionService;

    @GetMapping(value = "/list")
    public Result<PageResult<ChatSession>> list(@RequestParam(defaultValue = "1") Long pageNum,
                                                @RequestParam(defaultValue = "20") Long pageSize) {
        PageResult<ChatSession> sessions = chatSessionService.pageSessions(pageNum, pageSize);
        return Result.succ(sessions);
    }

    @GetMapping(value = "/delete")
    public Result<Void> delete(@RequestParam Long id) {
        chatSessionService.deleteSession(id);
        return Result.succ();
    }

    @PostMapping(value = "/update")
    public Result<Void> update(@RequestBody @Validated ChatSession chatSession) {
        chatSessionService.updateSessionTitle(chatSession.getId(), chatSession.getTitle());
        return Result.succ();
    }
}
