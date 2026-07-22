package com.analyzer.api.controller.lawyer;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.exception.GlobalExceptionHandler;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.security.JwtTokenProvider;
import com.analyzer.api.security.UserDetailsServiceImpl;
import com.analyzer.api.service.revenue.ExpertRevenueService;
import com.analyzer.api.service.revenue.CommissionPolicyManagementService;
import com.analyzer.api.service.revenue.EarlyPayoutService;
import com.analyzer.api.service.revenue.RevenuePayrollService;
import com.analyzer.api.service.revenue.RevenueWorkbookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpertRevenueController.class)
@Import({GlobalExceptionHandler.class, ExpertRevenueControllerContractTest.MethodSecurity.class})
class ExpertRevenueControllerContractTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ExpertRevenueService revenueService;
    @MockitoBean RevenuePayrollService payrollService;
    @MockitoBean CommissionPolicyManagementService commissionService;
    @MockitoBean EarlyPayoutService earlyPayoutService;
    @MockitoBean RevenueWorkbookService workbookService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean UserDetailsServiceImpl userDetailsService;

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {}

    @Test
    void expertCanAccessAllFourCollectionEndpoints() throws Exception {
        PageResponse<?> emptyPage = PageResponse.builder().items(List.of()).page(0).size(20).totalItems(0).totalPages(0).build();
        when(payrollService.expertStatements(42L, 0, 20)).thenReturn((PageResponse) emptyPage);
        when(commissionService.expertNotifications(42L)).thenReturn(List.of());
        when(commissionService.listPolicies()).thenReturn(List.of());
        when(earlyPayoutService.expertList(42L, 0, 20)).thenReturn((PageResponse) emptyPage);

        for (String path : List.of(
                "/api/v1/expert/revenue/periods",
                "/api/v1/expert/revenue/commission-notifications",
                "/api/v1/expert/revenue/commission-policies",
                "/api/v1/expert/revenue/early-payouts")) {
            mockMvc.perform(get(path).with(authentication(expertAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Test
    void unauthenticatedRequestGets401() throws Exception {
        mockMvc.perform(get("/api/v1/expert/revenue/periods"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerRoleGets403() throws Exception {
        mockMvc.perform(get("/api/v1/expert/revenue/periods").with(authentication(customerAuthentication())))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownApiRouteGetsJson404() throws Exception {
        mockMvc.perform(get("/api/v1/expert/revenue/not-a-route").with(authentication(expertAuthentication())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("API_ROUTE_NOT_FOUND"));
    }

    private UsernamePasswordAuthenticationToken expertAuthentication() {
        return createAuthentication("ROLE_EXPERT");
    }

    private UsernamePasswordAuthenticationToken customerAuthentication() {
        return createAuthentication("ROLE_CUSTOMER");
    }

    private UsernamePasswordAuthenticationToken createAuthentication(String authority) {
        UserDetailsImpl principal = new UserDetailsImpl(
                42L, "expert@example.com", "expert@example.com", "", List.of(new SimpleGrantedAuthority(authority)), true);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
