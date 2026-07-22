package com.analyzer.api.dto.chatsession;

import com.analyzer.api.enums.ShareAccessLevel;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShareChatSessionRequest {
    @NotNull(message = "accessLevel khong duoc de trong")
    private ShareAccessLevel accessLevel = ShareAccessLevel.PUBLIC;
}
