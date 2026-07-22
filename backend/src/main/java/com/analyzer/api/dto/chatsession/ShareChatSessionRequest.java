package com.analyzer.api.dto.chatsession;

import com.analyzer.api.enums.ShareAccessLevel;
import lombok.Data;

@Data
public class ShareChatSessionRequest {
    private ShareAccessLevel accessLevel = ShareAccessLevel.PUBLIC;
}
