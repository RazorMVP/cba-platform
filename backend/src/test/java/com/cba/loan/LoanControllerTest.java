package com.cba.loan;

import com.cba.common.exception.CbaException;
import com.cba.loan.dto.LoanResponse;
import com.cba.loan.dto.RepaymentScheduleResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Who may read a loan: staff any loan; a customer only their own. */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoanController — loan ownership")
class LoanControllerTest {

    @Mock LoanService loanService;
    @InjectMocks LoanController controller;

    final UUID loanId = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a customer reading another customer's loan gets 404 and no data")
    void customer_otherLoan_notFound() {
        actAsCustomer("kc-intruder");
        doThrow(CbaException.notFound("Loan", loanId)).when(loanService).requireOwnedBy(loanId, "kc-intruder");

        assertThatThrownBy(() -> controller.getLoan(loanId)).isInstanceOf(CbaException.class);
        assertThatThrownBy(() -> controller.getRepaymentSchedule(loanId)).isInstanceOf(CbaException.class);
        verify(loanService, never()).getLoan(any());
        verify(loanService, never()).getRepaymentSchedule(any());
    }

    @Test
    @DisplayName("a customer reading their own loan gets the customer view")
    void customer_ownLoan_redacted() {
        actAsCustomer("kc-owner");
        when(loanService.getLoan(loanId)).thenReturn(loan());

        LoanResponse body = controller.getLoan(loanId).getBody().data();

        verify(loanService).requireOwnedBy(loanId, "kc-owner");
        assertThat(body.lossAllowance()).isNull();
    }

    @Test
    @DisplayName("staff skip the ownership check and see the full loan")
    void staff_anyLoan() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "teller", "n/a", List.of(new SimpleGrantedAuthority("ROLE_TELLER"))));
        when(loanService.getLoan(loanId)).thenReturn(loan());
        when(loanService.getRepaymentSchedule(loanId)).thenReturn(List.<RepaymentScheduleResponse>of());

        LoanResponse body = controller.getLoan(loanId).getBody().data();
        controller.getRepaymentSchedule(loanId);

        verify(loanService, never()).requireOwnedBy(any(), any());
        assertThat(body.lossAllowance()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("a customer without a JWT subject is treated as not the owner")
    void customer_withoutJwt_checkedWithNullSubject() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "someone", "n/a", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        doThrow(CbaException.notFound("Loan", loanId)).when(loanService).requireOwnedBy(loanId, null);

        assertThatThrownBy(() -> controller.getLoan(loanId)).isInstanceOf(CbaException.class);
    }

    private static void actAsCustomer(String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    private LoanResponse loan() {
        return new LoanResponse(loanId, "001-LN-1", UUID.randomUUID(), "Jane Doe", "Personal",
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                new BigDecimal("12.00"), 12, LoanStatus.IN_ARREARS, null, null, null, null,
                40, false, BigDecimal.ZERO, BigDecimal.ZERO,
                2, new BigDecimal("400.00"), new BigDecimal("20.00"),
                null, null, null, null, BigDecimal.ZERO);
    }
}
