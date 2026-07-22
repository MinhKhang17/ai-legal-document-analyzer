package com.analyzer.api.dto.policy;

import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDateTime;

public final class PolicyAcceptanceDtos {
    private PolicyAcceptanceDtos() {}
    public record AcceptRequest(@AssertTrue(message = "Both current policies must be accepted") boolean accepted) {}
    public record StatusResponse(String termsVersion, String privacyPolicyVersion, boolean accepted,
                                 LocalDateTime acceptedAt) {}
}
